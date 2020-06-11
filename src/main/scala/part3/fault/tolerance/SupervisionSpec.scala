package part3.fault.tolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class SupervisionSpec extends TestKit(ActorSystem("SupervisionSpec")) with WordSpecLike with ImplicitSender with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)
  import SupervisionSpec._
  "A supervisor" should {
    "Resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[SupervisorCounter])
      supervisor ! Props[FuzzyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! "This is a very long message that would make the child fail"
      child ! Report
      // In this case, the supervisor would resume the actor, so the internal variables should be kept
      expectMsg(3)
    }

    "restart an actor child in case of an empty sentence" in{
      val supervisor = system.actorOf(Props[SupervisorCounter])
      supervisor ! Props[FuzzyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0)
    }

    "stop the child actor in case of an illegal argument" in {
      val supervisor = system.actorOf(Props[SupervisorCounter])
      supervisor ! Props[FuzzyWordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)

      // The child should be stopped, which sends a Terminated message that is captured, which contains a reference to the
      // dead actor
      child ! "lowercase message"
      val terminated = expectMsgType[Terminated]
      assert(terminated.actor == child)
    }

    "escalate an error when it doesn't know what to do " in {
      val supervisor = system.actorOf(Props[SupervisorCounter], "supervisor")
      supervisor ! Props[FuzzyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)
      child ! 0

      // This is true because when an actor escalates, it stop all its children, and then the supervisor is restarted by
      // the user actor.
      val terminated = expectMsgType[Terminated]
      assert(terminated.actor == child)
    }

    "of type no dead supervisor should not stop its childs on error" in {
      val noDeadSupervisor = system.actorOf(Props[NoDeadSupervisor] , "noDeadSupervisor")
      noDeadSupervisor ! Props[FuzzyWordCounter]
      val child2 = expectMsgType[ActorRef]

      watch(child2)
      child2 ! "Akka is cool"
      child2 ! Report
      expectMsg(3)
      child2 ! 0
      // We have overwritten the preRestart method, so the child objects should not be stopped
      child2 ! Report
      //Because the user guardian restarts everything, the internal state of child is set to 0
      expectMsg(0)

    }

    "Apply all supervisor strategy on all its children" in{
      val supervisor = system.actorOf(Props[AllForOneSupervisor] , "allForOneSupervisor")
      supervisor ! Props[FuzzyWordCounter]
      val child1 = expectMsgType[ActorRef]
      supervisor ! Props[FuzzyWordCounter]
      val child2 = expectMsgType[ActorRef]

      child2 ! "Testing Supervisor"
      child2 ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept{
        child1 ! ""
      }
      Thread.sleep(1000) // We are giving time to the child to be restarted
      child2 ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  case object Report
  class SupervisorCounter extends Actor {
    // The default behaviour of a parent in case a child throws an exception is to restart it, but this behaviour can be
    // overwritten by providing a different supervision strategy, which is an object that has an apply method, and to
    // which you can pass a partial function:
    override val supervisorStrategy:SupervisorStrategy = OneForOneStrategy(){
      case _:NullPointerException => Restart //This is an akka Directive, which is an object that the akka system will handle
      case _:IllegalArgumentException => Stop
      case _:RuntimeException => Resume
      case _:Exception => Escalate
    }

    override def receive: Receive = {
      case props:Props =>
        val childRef = context.actorOf(props)
        sender() ! childRef
    }
  }
  class FuzzyWordCounter extends Actor {
    var words = 0
    override def receive: Receive = {
      case ""=> throw new NullPointerException("Sentence is empty")
      case sentence:String =>
        if(sentence.length > 20) throw new RuntimeException("Sentence is too long")
        else if(!Character.isUpperCase(sentence(0))) throw new IllegalArgumentException("Sentence must start with uppercase")
        else words += sentence.split(" ").length
      case Report => sender() ! words
      case _ => throw new Exception("Not recognized")
    }
  }

  class NoDeadSupervisor extends SupervisorCounter with ActorLogging{
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info("NoDeadSupervisor not stopping childs")
    }
  }

  class AllForOneSupervisor extends SupervisorCounter with ActorLogging{

    //The difference between a OneForOneStrategy strategy and the AllForOneStrategy is that the one for one strategy
    //applies this decision on the exact actor that caused the failure, whereas all for one strategy applies
    //this supervisor strategy for all the actors, regardless of the one that actually caused the failure.
    //So in case one of the children fails, with an exception, all of the children are subject to the same supervision
    //directive.
    override val supervisorStrategy = AllForOneStrategy(){
        case _:NullPointerException => Restart //This is an akka Directive, which is an object that the akka system will handle
        case _:IllegalArgumentException => Stop
        case _:RuntimeException => Resume
        case _:Exception => Escalate
    }
  }
}
package part2.testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration

// TestProbs are special type of Actors with assertion capabilities
class ProbsSpec extends TestKit(ActorSystem("ProbsSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
  import ProbsSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // This would return a TestProbe actor which is used to assert results

      master ! Register(slave.ref) // we pass the actor reference of the Probe
      expectMsg(RegistrationAck)
    }

    "Send the work to the slave actor" in {
      val master = system.actorOf(Props[Master]) // MAster is created inside each test because it is stateful
      val slave = TestProbe("slave")
      master ! Register(slave.ref) // we need to initiate the master
      expectMsg(RegistrationAck)

      val workLoadString = "I love Akka!"
      master ! Work(workLoadString)

      // The slave has the same assertion capabilities that the test
      // The test actor is the original requester since as we have put the ImplicitSender trait, it acts as a proxy of all
      // messages sent
      slave.expectMsg(SlaveWork(workLoadString, testActor))
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3)) // This is the final message received
    }

    "Test Master with multiple pieces of work" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
      val workLoadString = "I love Akka!"
      master ! Work(workLoadString)
      master ! Work(workLoadString)

      // We should expect 2 Report Messages one with the first count (3) and the second with the aggregation to that (6)
      // I don't have a slave actor, it doesn't send any report messages back, we can define the slave probe behaviour with
      // the following method:
      slave.receiveWhile(){
        // The `` around parameters is to indicate that it has to match exactly the values, not a capture argument
        case SlaveWork(`workLoadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }

      expectMsg(Report(3))
      expectMsg(Report(6))
    }
  }
}

object ProbsSpec {
  /**
   * Word counting actor hierarchy with master slave
   * - Send some test to the master
   * - Master send the worker a piece of work
   * - Slave does the work and send the result to the master
   * - Master aggregates the results and send it back to the requester
   */
  case object RegistrationAck
  case class Register(ref:ActorRef)
  case class Work(work:String)
  case class SlaveWork(work:String, originalRequester:ActorRef)
  case class WorkCompleted(count:Int, ref:ActorRef)
  case class Report(count:Int)
  class Master extends Actor{
    override def receive: Receive = {
      case Register(ref) => {
        sender() ! RegistrationAck
        context.become(online(ref, 0))
      }
      case _ => //Ignore
    }
    def online(slaveRef:ActorRef, totalWorkCount:Int):Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWorkCount = totalWorkCount + count
        originalRequester ! Report(newTotalWorkCount)
        context.become(online(slaveRef, newTotalWorkCount))
    }
  }
  // Master actor interacts with multiple actors in the context, we need test entities to act as proxies to be able to test
  // the master behaviour, those entities are called TestProbs
}

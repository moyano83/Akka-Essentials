package part3.fault.tolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import part3.fault.tolerance.L2_ActorLifecycle.Parent.{CheckChild, FailChild}

object L2_ActorLifecycle extends App {
  object LifeCycleActor{
    case object StartChild
  }
  class LifeCycleActor extends Actor with ActorLogging{
    import LifeCycleActor._
    override def receive: Receive = {
      case StartChild => {
        log.info("Creating child!")
        context.actorOf(Props[LifeCycleActor], "child")
      }
    }

    /**
     * This method is called before the actor instance is able to process any messages
     */
    override def preStart(): Unit = {
      log.info("I am starting")
    }

    /**
     * This is called asynchronously after stop() has been called in this actor
     */
    override def postStop(): Unit = log.info("I have stop")

  }

  val system = ActorSystem("LifeCycleDemo")
  val parent = system.actorOf(Props[LifeCycleActor], "lifecycle1")

  import LifeCycleActor._
  parent ! StartChild
  parent ! PoisonPill

  Thread.sleep(1000)
  /**
   * Restarting Demo
   */
  object Child {
    case object Fail
    case object Check
  }
  object Parent{
    case object FailChild
    case object CheckChild
  }
  class Parent extends Actor with ActorLogging {
    import Parent._
    val child = system.actorOf(Props[Child], "child")

    override def receive: Receive = {
      case FailChild => {
        log.warning("Failing child")
        child ! Child.Fail
      }
      case CheckChild => {
        log.info("Checking parent")
        child ! Child.Check
      }
    }
    override def preStart(): Unit = log.info("I am starting Parent")
    override def postStop(): Unit = log.info("I have stop Parent")

    // pre-start is called by the old actor instance before it is swapped during the restart procedure
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.info(s"Parent actor restarting: ${message}")
    // post-restart is called by the new actor instance that has just been inserted at the restart procedure.
    override def postRestart(reason: Throwable): Unit = log.info("Parent actor restarted")
  }
  class Child extends Actor with ActorLogging {
    import Child._
    override def receive: Receive = {
      case Fail => throw new RuntimeException("This is a mess!")
      case Check => log.info("Checking child")
    }
    override def preStart(): Unit = log.info("I am starting Child")
    override def postStop(): Unit = log.info("I have stop Child")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.info(s"Child actor restarting: ${message}")
    override def postRestart(reason: Throwable): Unit = log.info("Child actor restarted")
  }

  val supervisor = system.actorOf(Props[Parent], "parent")
  supervisor ! FailChild
  supervisor ! CheckChild

  /**
   * once the actor child fails, the child actor is restarted as well as the parent bubble up.
   * and after it is able to process more messages. This is a part of the default what it's called supervision strategy and
   * the default supervision:
   *
   * If an actor throw an exception while processing a message this message which caused the exception
   * to be thrown is removed from the queue and not put back in the mailbox again.
   * And the after is restarted which means the mailbox is untouched.
   */

  Thread.sleep(1000)
  system.terminate()
}

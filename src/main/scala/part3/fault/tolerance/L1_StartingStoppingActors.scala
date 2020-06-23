package part3.fault.tolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

object L1_StartingStoppingActors extends App{
   val system = ActorSystem("StoppingActorsDemo")

  object Parent{
    case class StartChild(message: String)
    case class StopChild(message: String)
    case object Stop
  }

  class Parent extends Actor with ActorLogging{
    import Parent._
    override def receive: Receive = {
      withChildren(Map.empty[String, ActorRef])
    }

    /**
     * Stop actor by context.stop
     */
    def withChildren(children:Map[String, ActorRef]):Receive = {
      case StartChild(name) => {
        log.info(s"Starting ${name} child")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      }
      case StopChild(name) => {
        log.warning(s"Stopping child ${name}")
        val childOpt = children.get(name)

        //This is the way to stop a child in the actor context, it is a non synchronous method,  a message is sent to the
        // actor and it can still process some messages
        childOpt.foreach(actor => context.stop(actor))
      }
      // This is the way to stop the own actor, any child actor of this actor is stopped as well, child actors are stopped
      // before the parent actor is
      case Stop => {
        log.warning("Stopping myself")
        context.stop(self)
      }
    }
  }

  class Child extends Actor with ActorLogging{
    override def receive: Receive = {
      case message => log.info(s"received ${message}")
    }
  }

  import Parent._
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("child1")
  val child= system.actorSelection("/user/parent/child1")
  child ! "Hello!"

  parent ! StopChild("child1")
  // We can see that the child is still processing some messages, after that the messages sent to this actor are
  // redirected to the deadletter inbox after the actor is removed
  //for (i <- 1 to 50) child ! s"Are you alive? [${i}]"

  /**
   * Stop actors by using special messages
   * 1 - PoisonPill
   * 2 - Kill
   */
  val looseActor = system.actorOf(Props[Child], "loseActor")
  looseActor ! "Hello!"
  looseActor ! PoisonPill //This is a message handled by Actor, and when it receives this message, the actor is removed
  looseActor ! "Are you still there?"

  val looseActor2 = system.actorOf(Props[Child], "loseActor2")
  looseActor2 ! "Hello!"
  //looseActor2 ! Kill //This is a message kills the actor and forces him to send an ActorKilledException
  looseActor2 ! "Are you still there?"

  /**
   * Death Watch
   */
  class Watcher extends Actor with ActorLogging{
    import Parent._
    override def receive: Receive = {
      case StartChild(name) =>{
        val child = context.actorOf(Props[Child], name)
        log.info(s"Starting to watch ${child}")
        // This register this actor for the death of the child, when the child dies, this actor receives a:
        // Terminated(actorRef) message
        context.watch(child)
      }
      case Terminated(child) => {
        log.info(s"${child} died")
      }
    }
  }
  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("child_watched")

  val watched = system.actorSelection("/user/watcher/child_watched")
  Thread.sleep(1000) // Giving time to process messages
  watched ! PoisonPill
  Thread.sleep(1000) // Giving time to process messages
  system.terminate()
}

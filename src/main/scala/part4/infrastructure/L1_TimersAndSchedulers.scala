package part4.infrastructure
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}
import akka.event.Logging

object L1_TimersAndSchedulers extends App{
  class SimpleActor extends Actor with ActorLogging{
    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }
  val system = ActorSystem("TimersAndSchedulersDemo")
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")

  // This also send messages to the logger actor in the system
  system.log.info("Scheduler reminder for simple actor")

  import system.dispatcher
  //This would schedule the code passed as argument
  system.scheduler.scheduleOnce(1 seconds){
    actor ! "Reminder"
    // as with futures, the scheduler requires an execution context to run the code into
    // we can pass this argument directly, implicitely or by importing the system.dispatcher
  }

  // If you want to schedule a repeated message with a delay of 1 second an triggered every 2 seconds
  // you can do it like this (returns a cancellable )
  val routine = system.scheduler.schedule(1 seconds, 2 seconds){
    actor ! "Scheduled Reminder"
    // as with futures, the scheduler requires an execution context to run the code into
    // we can pass this argument implicitely or by importing the system.dispatcher
  }

  // This would stop the above routine after 10 seconds
  system.scheduler.scheduleOnce(10 seconds){
    routine.cancel()
  }

  /**
   * Exercises: implement a self closeable scheduler:
   *  - if the actor receives a message, you have another second to send another message
   *  - If the time window expires, the actor will stop itself
   *  - if you send another message, the actor's time window is reset and you have another second to solve a message
   */


  class SelfCloseableActor extends Actor with ActorLogging{
    var cancellable:Cancellable = null
    override def receive: Receive = {
      case msg => {
        log.info(msg.toString)
        setCancellable()
      }
    }
    private def setCancellable() ={
      log.info("Initiating self cancellable routine")
      Option(cancellable).map(_.cancel())
      cancellable = context.system.scheduler.scheduleOnce(1 second) {
        log.info("Shutting down")
        context.stop(self)
      }(context.dispatcher)
    }
  }

  val cancellable = system.actorOf(Props[SelfCloseableActor], "cancellableActor")
  cancellable ! "Init"
  Thread.sleep(500)
  cancellable ! "Log this!"
  Thread.sleep(2000)
  cancellable ! "Alive?"


  /**
   * Timers:  timers are a simpler and safer way to schedule messages to self from within an actor
   */
  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedClossingActor extends Actor with ActorLogging with Timers{
    // This is a method that would send a message to self. The first argument is a key that would identify this timer,
    // there can only be a single timer per key, the second argument is the message to send to self, the third is a delay
    // to start sending messages
    timers.startSingleTimer(TimerKey, Start, 1 second)
    override def receive: Receive = {
      case Start => {
        log.info("Bootstrapping")
        // When I do timer.StartPeriodicTimer with the same timer key that was associated to another
        // timer the previous timer is canceled so we don't need to concern ourselves with the lifecycle of the timer
        timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      }
      case Reminder => log.info("Reminder")
      case Stop => {
        log.info("Stopping")
        timers.cancel(TimerKey)
        context.stop(self)
      }
    }
  }

  val timerActor = system.actorOf(Props[TimerBasedClossingActor], "cancellableTimerActor")
  timerActor ! Start

  Thread.sleep(3000)
  timerActor ! Stop
  system.terminate()
}

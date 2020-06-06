package part1.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLoggingDemo extends App{

  // Method 1: Explicit Logging
  class SimpleActorWithExplicitLogger extends Actor{
    //The simplest way to log events in akka is using the provided Logging
    // The second parameter is the log source, we provide the reference to the
    // actor instance
    val logger = Logging(context.system, this)

    override def receive: Receive = {
      case msg => logger.info(s"Simple logging message received: ${msg}")
    }
  }

  val system = ActorSystem("ActorLogging")
  val logger = system.actorOf(Props[SimpleActorWithExplicitLogger], "testLogger")

  logger ! "Log this!"

  //Method 2: Actor Logging
  class ActorWithLogging extends Actor with ActorLogging{
    override def receive: Receive = {
      case (a,b) => log.info("Log interpolation for {} and {}",a,b)
      case msg => log.info(s"Actor logging message received: ${msg}")
    }
  }
  val logger2 = system.actorOf(Props[ActorWithLogging], "otherLogger")

  logger2 ! "Log this other thing"
  logger2 ! (1,2)
  system.terminate()

  // Logging is asynchronous, it is implemented with actors as well
  // The underlying implementation of the logger can be changed through configuration
}

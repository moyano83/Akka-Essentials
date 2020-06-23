package part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing._
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object L3_Dispatchers extends App{
  class Counter extends Actor with ActorLogging{
    var count = 0
    override def receive: Receive = {
      case msg => {
        count += 1
        log.info(s"count [${count}]: ${msg.toString}")
      }
    }
  }

  /**
   * A dispatcher controls how messages are sent and handled, there are several ways to configure this
   *
   *
   * Method 1: Programmatically configure the dispatcher
   */
  val system = ActorSystem("dispatchersDemo") //, ConfigFactory.load().getConfig("dispatchersDemo"))
  val actors = for(i <-1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_${i}")
  val random = new Random
  for(i<- 1 to 1000){
    //The behaviour here is that the thread pool will be created with n number of threads (from config) and then it will
    // dispatch x number of messages (throughput in config) before it removes the actor from the threadpool and gets
    // another one to replace it
    actors(random.nextInt(10)) ! i
  }

  /**
  * Method 2: From config
  */
  // There has to be a config with the same name in application.conf
  val rtjvm = system.actorOf(Props[Counter], "rtjvm")
  // Dispatchers implements the Executor Context trait (the system.dispatcher used in the TimersAndSchedulers lesson),
  // they can run futures
  class DBActor extends Actor with ActorLogging{

    implicit val executionContext: ExecutionContext = context.dispatcher
    override def receive: Receive = {
      // This handles a future, we can use the dispatcher as an execution context
      case msg => Future{
        // simulation of long computation
        Thread.sleep(2000)
        log.info(s"Success: ${msg}")
      } // can be passed as well as implicit
    }
  }
  val dbActor = system.actorOf(Props[DBActor])
  dbActor ! "Meaning of life is 42"

  // The above is shown just for learning purposes, it is not recommended to use the context.dispatcher as an execution
  // context because in case of long computations, the dispatcher might starve and won't be able to process further
  // messages
  val nonBlockingActor = system.actorOf(Props[Counter], "nonBlockingActor")
  for(i<- 1 to 1000){
    //The behaviour here is that the thread pool will be created with n number of threads (from config) and then it will
    // dispatch x number of messages (throughput in config) before it removes the actor from the threadpool and gets
    // another one to replace it, there is a hicup in processing messages, until the dbActor releases the resources
    nonBlockingActor ! i
    dbActor ! i
  }
  // It is possible to solve the above if instead using the context.dispatcher as executor context, you replace that by:
  // implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(<name in config>)
  // or else you can also use a router instead of a dispatcher

  Thread.sleep(3000)
  system.terminate()
}

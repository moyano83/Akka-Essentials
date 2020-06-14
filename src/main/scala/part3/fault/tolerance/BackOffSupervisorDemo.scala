package part3.fault.tolerance

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import scala.io.Source

object BackOffSupervisorDemo extends App{
  /**
   * Problem: The repeated restart of actors (i.e. in case a resource is not available)
   * Backoff supervisor pattern introduces exponential Delays as well as randomness in
   * between the attempts to rerun a supervision strategy.
   */

  object FileBasedPersistenceActor{
    case object ReadFile
  }

  class FileBasedPersistenceActor extends Actor with ActorLogging{
    import FileBasedPersistenceActor._
    var dataSource:Source = null

    override def preStart(): Unit = log.info("Persistence actor starting")

    override def postStop(): Unit = log.warning("Persistence actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.warning("Persistence actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if(dataSource ==null)
          dataSource = Source.fromFile(new File("src/main/resources/testFiles/importantFile_notFound.txt"))
        log.info("I just read an important file:" + dataSource.getLines().toString())
    }

  }
  import FileBasedPersistenceActor._
  val system = ActorSystem("BackoffSupervisorDemo")
  val simpleActor = system.actorOf(Props[FileBasedPersistenceActor], "noBackedOffActor")
  simpleActor ! ReadFile

  //BackOff supervisor pattern deals with this kind of situations, to created a backed off actor we need to create a new
  // props like this:
  val backOffSupervisor = BackoffSupervisor.props(
    //This backoff props
    Backoff.onFailure(
      Props[FileBasedPersistenceActor], "SimpleBackoffActor", 3 seconds, 30 seconds, 0.2
    )
  )
  /**
   * And then pass it to the actor system
   * SimpleSupervisor:
   *  - this simple Supervisor has a child called SimpleBackoffActor
   *  - It can receive any messaage that would be forwarded to its child
   *  - Supervisor strategy is the default one, which is restart everything, but with a twist:
   *    - First attempt to create a child kicks in 3 seconds
   *    - Second attempt is at double the previous, which is 2x the previous attempt (6 seconds) with a bit of noise which
   *    is determined by the random factor.
   *    - third attempt is at 12 seconds, forth at 24 seconds... up to 30 seconds which is the max backoff
   */
  val simpleSupervisorActor = system.actorOf(backOffSupervisor, "simpleSupervisor")
  simpleSupervisorActor ! ReadFile

  /**
   * This backoff object kicks in on Stop and for any exception thrown, the directive would be to stop the actor
   */
  val stopSupervisor = BackoffSupervisor.props(
    //This backoff props
    Backoff.onStop(
      Props[FileBasedPersistenceActor], "StopBackOffActor", 3 seconds, 30 seconds, 0.2
    ).withSupervisorStrategy(OneForOneStrategy(){
      case _ => Stop
    })
  )

  val stopSupervisorActor = system.actorOf(stopSupervisor, "stopSupervisor")
//  stopSupervisorActor ! ReadFile

  /**
   * Lets make a change and create an actor that tries to read the file on initialization, the default strategy is to stop
   * the actor
   */
  class EagerFileBasedPersistenceActor extends FileBasedPersistenceActor{
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testFiles/importantFile_notFound.txt"))
    }
  }

  val eagerActor = system.actorOf(Props[EagerFileBasedPersistenceActor], "eagerActor")
  //It is possible to create a backoff plan that tries to restart the actor with a different backoff strategy
  val eagerSupervisorProps = BackoffSupervisor.props(
    //This backoff props
    Backoff.onStop(
      Props[EagerFileBasedPersistenceActor], "eagerActorChild", 1 seconds, 30 seconds, 0.1
    )
  )

  /**
   * The eager supervisor will:
   * - Create the child eager actor
   *     - this child will die, on start throwing an ActorInitializationException
   *     - This will trigger the eager supervisor strategy which is to STOP the child eager actor
   * - backoff will kick in after one second, then 2, then 4, then 8, then 16, after this the backoff will stop kicking in
   * and the actor child will remain dead
   */
  val repeatedSupervisor = system.actorOf(eagerSupervisorProps, "eagerSupervisor")

  // If the resource sudendly become available, the child actor would stop failing, and then the actor will be able to
  // initialize itself, with the backoff strategy all the actors that were trying to access the resource won't try to
  // access it at the  same time, preventing the resource from being unavailable again (i.e. a database)
  system.terminate()
}

package part2.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

class L3_TimedAssertionsSpec
  extends TestKit(ActorSystem("TimedAssertions", ConfigFactory.load().getConfig("timedAssertionConfig")))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
  import L3_TimedAssertionsSpec._

  // Some times the actors takes a lot of time to reply if the workload is big
  "A worker actor" must {
    val worker = system.actorOf(Props[WorkerActor])

    "reply with the ok code in a timely manner" in {
      // øThis ensures that the response is received in the boundary time
      within(500 millis, 1 second){
        worker ! "work"
        expectMsg(WorkResult(200))
      }
    }

    "reply with valid work with a reasonable cadence" in {
      within(1 second){
        worker ! "workSequence"

        // This means that the max time to receive is 2 senconds, with no idle time of more than 500 millis and
        // should receive 10 messages. The code between {} is a partial function, in this case this would generate a
        // sequence containing the codes returned
        val seqCode = receiveWhile[Int](max = 2 second, idle = 500 millis, messages = 10){
        // messages specified the number of messages this receivewhile should wait for)

          case WorkResult(code) => code
        }
        assert(seqCode.forall(code => code == 202))
      }
    }
    // Within block does not have any influence on TestProbs, we loaded the config timedAssertionConfig from application.conf
    // which has a timeout of 0.3ms, so this will fail even the within specifies 1 second
    "reply to a testProble" in {
      "A timely manner" in {
        within(1 second) {}
          val probe = TestProbe()
          probe.send(worker, "work")
          probe.expectMsg(WorkResult(200))
        }
      }
  }

}

object L3_TimedAssertionsSpec{
  case class WorkResult(code:Int)
  class WorkerActor extends Actor{
    override def receive: Receive = {
      case "work" =>
        // Simulation of a long computation
        Thread.sleep(500)
        sender() ! WorkResult(200)
      case "workSequence" =>
        // simulation of burst reply or rapid fire responses with small results
        val r = new Random()
        for(i <- 1 to 10){
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(202)
        }
    }
  }
}

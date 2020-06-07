package part2.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.util.Random

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  // For scenarios send and reply scenarios with actors, it brings to scope the testActor, which is
  // the sender of all messages send in the test scenario
  with ImplicitSender
  // Allows testing with BDD in a natural way
  with WordSpecLike
  with BeforeAndAfterAll {

  import BasicSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system) // system is a member of testKit
  }

  "A simple actor (The thing being tested)" should { // This is the usual structure of a test
    "send back the original message (do something)" in { // test suite
      val echoActor = system.actorOf(Props[SimpleActor], "simpleActor")
      val message = "Hello Test"
      echoActor ! message
      expectMsg(message) // This asserts that the message received is the one passed as argument
    }

  }
  "A blackholeActor" should {
    "send some messages" in{
      val blackHoleActor = system.actorOf(Props[BlackHoleActor], "blackHoleActor")
      val message = "Hello Test"
      blackHoleActor ! message
      // This would wait for some time before the test fails, the timeout is configurable with the property
      // akka.test.single-expect-default
      // expectMsg(message)

      // For no message received we use the following:
      expectNoMessage() // This method also accept a Duration for setting the timeout
    }
  }

  //Message Assertions
  "A lab test actor" should {
    // In case you have stateful actors that you need to clear up in the test, you'll instantiate the actor inside the test
    val labTestActor = system.actorOf(Props[LabTestActor])
    "Turn a string into uppercase" in {
      labTestActor ! "Akka Rules"
      // If the assertions are more complex, you might want to get hold on the message to do whatever assertion
      val reply = expectMsgType[String]
      assert(reply == "AKKA RULES")
    }
    "reply to a greeting" in {
      labTestActor ! "greeting"
      expectMsgAnyOf("HI!", "HELLO!") // Multiple expectations can be set at the same time
    }

    "receive multiple messages" in {
      labTestActor ! "multiple"
      expectMsgAllOf("AKKA", "RULES")
    }

    "capture multiple messages" in {
      labTestActor ! "multiple"
      val messages = receiveN(2) // Returns a sequence of Any, with the messages
      assert(messages.contains("AKKA"))
      assert(messages.contains("RULES"))
    }

    "reply with multiple messages in a fancy way" in {
      labTestActor ! "multiple"
      expectMsgPF() {
        // This partial function is another way to test the messages received, it can in theory return any value but we only
        // care that the PF is defined for the messages we care about. This can become way more complex
        case "AKKA" =>
        case "RULES" =>
      }
    }
  }

}

object BasicSpec{ // To store all the methods and values to be used in the test
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case msg => sender() ! msg
    }
  }
  class BlackHoleActor extends Actor{
    override def receive: Receive = {
      case msg => Actor.emptyBehavior
    }
  }
  class LabTestActor extends Actor{
    override def receive: Receive = {
      case "multiple" =>
        sender() ! "AKKA"
        sender() ! "RULES"
      case "greeting" =>
        val reply = if(Random.nextInt() % 2 ==0) "HI!" else "HELLO!"
        sender() ! reply
      case msg:String => sender() ! msg.toUpperCase()
    }
  }
}

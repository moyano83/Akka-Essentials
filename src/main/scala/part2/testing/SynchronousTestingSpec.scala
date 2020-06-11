package part2.testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.Duration

// Notice that we are not importing the test kit for synchronous test
class SynchronousTestingSpec extends WordSpecLike with BeforeAndAfterAll{

  // The actor system needs to be implicit so it is passed to the TestActorRef
  implicit val system = ActorSystem("SynchronousTest")

  override def afterAll(): Unit = system.terminate()

  import SynchronousTestingSpec._
  // Testing is all about predictability, akka provides some tools to assure that the actor has received some message so
  // it can be tested based on certain assumptions (synchronously)
  "A counter" should {
     "synchronously increase its counter" in {
       val counter = TestActorRef[CounterActor](Props[CounterActor]) // expects an implicit actor system
       // At this point CounterActor has already receive the message
       counter ! Increment

       assert(counter.underlyingActor.count == 1) // With this we can poke the underlying instance of the actor
     }

    // TestActorRef can invoke the receive handler on the underlying actor directly
    "synchronously increase the counter at the call of the receive function" in {
      val counter = TestActorRef[CounterActor](Props[CounterActor])
      counter.receive(Increment) // Instead sending a message, we can call the handler directly
      assert(counter.underlyingActor.count == 1)
    }

    "work on the calling thread dispatcher" in {
      // With the following configuration we assure that the sending happens on the running thread sequentially
      val counter = system.actorOf(Props[CounterActor].withDispatcher(CallingThreadDispatcher.Id))
      val prob = TestProbe()

      // Because the counter  operates on the calling thread dispatcher, after the below call, the thread has already
      // received the Read reply message
      prob.send(counter, Read)
      prob.expectMsg(Duration.Zero, 0) // we make sure that the Prob has already received the message with the 0
    }
  }
}

object SynchronousTestingSpec{
  case object Increment
  case object Read
  class CounterActor extends Actor{
    var count = 0
    override def receive: Receive = {
      case Increment => count +=1
      case Read => sender() ! count
    }
  }
}

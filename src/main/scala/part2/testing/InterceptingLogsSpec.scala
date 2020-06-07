package part2.testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class InterceptingLogsSpec extends TestKit(ActorSystem("InterceptingLogsSpec",
  ConfigFactory.load().getConfig("eventFilterLoggingTestConfig")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll{

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
  import InterceptingLogsSpec._
  // Intercepting log messages is very useful in integration test when it is not that easy to create TestProbs between actors
  // This is shown in the test example below. It is hard to inject the probe actors since the Checkout actor instantiates
  // its own actors, also, the payment manager never sends out any message, for which we need to do the validation in
  // other way, for this we have the Filter events
  val item = "Akka"
  "The InterceptingLogsSpec" should {
    "should validate the correct flow of Payment" in {
      // We need to create an event filter that checks that the confirmation message of the FullfillmentManagerActor has
      // been logged, for that we need to configure a logger that the event filter is able to intercept, which has been
      // done through the eventFilterLoggingTestConfig configuration defined in application.conf

      // we can also pass the exact message in the info. The intercept body should contain the code of the test
      // The intercept waits for 3 seconds, but this can be changed throught the config property: akka.test.filter-leeway
      EventFilter.info(pattern = s"Order Id [0-9]+ for item ${item} has been dispatched", occurrences = 1) intercept {
        val checkoutActor = system.actorOf(Props[CheckoutActor])
        checkoutActor ! Checkout(item, "1234123412341234")
      }

    }

    // Event Filters can also intercept exceptions like the code shown below
    "Test that the Payment rejected raises an exception " in {
      EventFilter[RuntimeException](occurrences = 1) intercept{
        val checkoutActor = system.actorOf(Props[CheckoutActor])
        checkoutActor ! Checkout(item, "0000000000000000")
      }
    }
  }
}
object InterceptingLogsSpec{
  /**
   * You have a little online shop and you want to implement a check out flow in your online shop
   * When you want to send a message to the check out actor the check out
   * actor will validate the payment card to them payment manager.
   * The payment manager will reply to the check out actor with a payment accepted or denied.
   * And if payment is accepted the check out actor will talk to the fulfillment manager to dispatch your
   * order and then when the checkout actor receives a reply from the fulfillment manager the check out actor
   * will revert back to its normal state where it can accept other checkout commands as well.
   */
  case class Checkout(item:String, creditCard:String)
  case class AuthorizeCard(card:String)
  case object PaymentAccepted
  case object PaymentRejected
  case class DispatchOrder(item:String)
  case object OrderConfirm
  class CheckoutActor extends Actor{
    private val paymentManager = context.actorOf(Props[PaymentManagerActor])
    private val fullfillmentManager = context.actorOf(Props[FullfillmentManagerActor])

    override def receive: Receive = awaitingCheckout

    def awaitingCheckout:Receive = {
      case Checkout(item, card) => paymentManager ! AuthorizeCard(card)
        context.become(pendingPayment(item))
    }

    def pendingPayment(item:String):Receive = {
      case PaymentAccepted =>
        fullfillmentManager ! DispatchOrder(item)
        context.become(pendingFullFilment(item))
      case PaymentRejected => throw new RuntimeException("Can't handle card")
    }

    def pendingFullFilment(item:String):Receive = {
      case OrderConfirm => context.become(awaitingCheckout)
    }
  }
  class PaymentManagerActor extends Actor{
    override def receive: Receive = {
      case AuthorizeCard(card) => if(card.startsWith("0")) sender() ! PaymentRejected else sender() ! PaymentAccepted
    }
  }
  class FullfillmentManagerActor extends Actor with ActorLogging{
    var orderId = 42
    override def receive: Receive = {
      case DispatchOrder(item) =>
        orderId += 1
        log.info(s"Order Id ${orderId} for item ${item} has been dispatched") // We want to verify this message is logged
        sender() ! OrderConfirm
    }
  }
}

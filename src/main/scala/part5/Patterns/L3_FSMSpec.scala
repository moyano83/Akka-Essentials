package part5.Patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, OneInstancePerTest, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Finite state machines is an alternative to context.become when the logic is complex to put it in a method
 */
class L3_FSMSpec extends TestKit(ActorSystem("FSMSystem"))
  with WordSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with OneInstancePerTest{

  import L3_FSMSpec._

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  val products = Map("Coke" -> 10, "Pepsi" -> 5, "Chips" -> 5)
  val prices = Map("Coke" -> 3, "Pepsi" -> 3, "Chips" -> 6)

  def testActor(vendingMachineProps: Props) = {

    "throw an error if not initialized" in {
      val vendingMachine = system.actorOf(vendingMachineProps)
      vendingMachine ! RequestProduct("coca cola")
      expectMsg(VendingError(MACHINE_NOT_INITIALIZED))
    }

    "report a product not available" in {
      val vendingMachine = system.actorOf(vendingMachineProps)
      vendingMachine ! Initialize(products, prices)
      vendingMachine ! RequestProduct("Sandwitch")
      expectMsg(VendingError(PRODUCT_NOT_AVAILABLE))
    }

    "Throw a timeout if the money is not inserted" in {
      val vendingMachine = system.actorOf(vendingMachineProps)
      vendingMachine ! Initialize(products, prices)
      vendingMachine ! RequestProduct("Coke")
      expectMsg(Instruction("Please insert 3$"))
      within(2.5 seconds) {
        expectMsg(VendingError(TIMEOUT_ERROR_MSG))
      }
    }
    "handle the reception of partial money" in {
      val vendingMachine = system.actorOf(vendingMachineProps)
      vendingMachine ! Initialize(products, prices)
      vendingMachine ! RequestProduct("Coke")
      expectMsg(Instruction("Please insert 3$"))
      vendingMachine ! ReceiveMoney(1)
      expectMsg(Instruction("Please insert 2$"))
      within(1.5 seconds) {
        expectMsg(VendingError(TIMEOUT_ERROR_MSG))
      }
    }
    "Deliver the product if receives the money" in {
      val vendingMachine = system.actorOf(vendingMachineProps)
      vendingMachine ! Initialize(products, prices)
      vendingMachine ! RequestProduct("Coke")
      expectMsg(Instruction("Please insert 3$"))
      vendingMachine ! ReceiveMoney(3)
      expectMsg(Deliver("Coke"))
    }

    "Give back change and be able to request for a new product" in {
      val vendingMachine = system.actorOf(vendingMachineProps)
      vendingMachine ! Initialize(products, prices)
      vendingMachine ! RequestProduct("Coke")
      expectMsg(Instruction("Please insert 3$"))
      vendingMachine ! ReceiveMoney(5)
      expectMsg(Deliver("Coke"))
      expectMsg(GiveBackChange(2))

      vendingMachine ! RequestProduct("Coke")
      expectMsg(Instruction("Please insert 3$"))
    }
  }

  "A non finite state machine vending machine" should {
    testActor(Props[VendingMachine])
  }

  "A finite machine state vending machine" should {
    testActor(Props[VendingMachineFSM])
  }
}

object L3_FSMSpec {

  val MACHINE_NOT_INITIALIZED = "Machine not initialized"
  val PRODUCT_NOT_AVAILABLE = "Product not available"
  val TIMEOUT_ERROR_MSG = "Time out"

  case class Initialize(inventory: Map[String, Int], prices: Map[String, Int]) // inventory map is name,quantity
  case class RequestProduct(product: String)

  case class Instruction(instruction: String) // Message the vending machine will show on the screen
  case class ReceiveMoney(amount: Int)

  case class Deliver(product: String)

  case class GiveBackChange(amount: Int)

  case class VendingError(message: String)

  case object ReceiveMoneyTimeout // For example if the machine waits for the money and the user doesn't introduce it

  /**
   * Implement a Vending Machine
   */
  class VendingMachine extends Actor with ActorLogging {
    implicit val executionContext: ExecutionContext = context.dispatcher

    override def receive: Receive = iddle()

    def iddle(): Receive = {
      case Initialize(inventory, prices) => context.become(operational(inventory, prices))
      case _ => sender() ! VendingError(MACHINE_NOT_INITIALIZED)
    }

    def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
      case RequestProduct(product) => inventory.get(product) match {
        case None | Some(0) => sender() ! VendingError(PRODUCT_NOT_AVAILABLE)
        case Some(amount) =>
          val price = prices.get(product).get
          sender() ! Instruction(s"Please insert ${price}$$")
          context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeout(), sender()))
      }
    }

    def waitForMoney(inventory: Map[String, Int],
                     prices: Map[String, Int],
                     product: String,
                     amountReceived: Int,
                     moneyTimeout: Cancellable,
                     requester: ActorRef): Receive = {
      case ReceiveMoneyTimeout =>
        requester ! VendingError(TIMEOUT_ERROR_MSG)
        if (amountReceived > 0) requester ! GiveBackChange(amountReceived)
        context.become(operational(inventory, prices))
      case ReceiveMoney(amount) =>
        moneyTimeout.cancel()
        val productPrice = prices.get(product).get
        if (amountReceived + amount < productPrice) {
          val totalInserted = amountReceived + amount
          requester ! Instruction(s"Please insert ${productPrice - totalInserted}$$")
          context.become(waitForMoney(inventory, prices, product, totalInserted, startReceiveMoneyTimeout(), requester))
        } else {
          requester ! Deliver(product)
          if (amountReceived + amount > productPrice) {
            requester ! GiveBackChange(amountReceived + amount - productPrice)
          }

          val newInventory = inventory + (product -> (inventory.get(product).get - 1))
          context.become(operational(newInventory, prices))
        }

    }

    def startReceiveMoneyTimeout() = context.system.scheduler.scheduleOnce(1 seconds) {
      self ! ReceiveMoneyTimeout
    }
  }

  /**
   * This class replaces the above with Finite state machines, which status is easier to handle. The steps to accomplish
   * this are:
   * 1 - Define the states and data of the actor
   */
  trait VendingState

  case object Idle extends VendingState

  case object Operational extends VendingState

  case object WaitForMoney extends VendingState

  trait VendingData

  case object Uninitialized extends VendingData

  case class Initialized(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData

  case class WaitForMoneyData(inventory: Map[String, Int], prices: Map[String, Int], product: String, amount: Int,
                              requester: ActorRef) extends VendingData

  /**
   * FSM is an actor with some methods, we don't have a receive handler. When an FSM triggers a message it triggers and
   * event, which contains the message and the data. Our job is to handle states and events, not messages.
   * An FSM is an actor that at any time has an event and some data, this data and status can change when a message is
   * received
   */
  class VendingMachineFSM extends FSM[VendingState, VendingData]{
    startWith(Idle, Uninitialized)
    //when(_) reveceives a partial function between events and states
    //
    when(Idle) {
      case Event(Initialize(inventory, prices), Uninitialized) =>
        // This is equivalente to context.become....
        goto(Operational) using Initialized(inventory, prices)
      case _ =>
        sender() ! VendingError(MACHINE_NOT_INITIALIZED) // The sender is still available
        stay()
    }

    when(Operational) {
      case Event(RequestProduct(product), Initialized(inventory, prices)) =>
        inventory.get(product) match {
          case None | Some(0) =>
            sender() ! VendingError(PRODUCT_NOT_AVAILABLE)
            stay()
          case Some(amount) =>
            val price = prices.get(product).get
            sender() ! Instruction(s"Please insert ${price}$$")
            goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, 0, sender())
        }
    }
    when(WaitForMoney, stateTimeout = 1 seconds) { // this is equivalent to setting a scheduler
      // This is the timeout event, which is triggered by akka
      case Event(StateTimeout, WaitForMoneyData(inventory, prices, product, amountReceived, requester)) =>
        requester ! VendingError(TIMEOUT_ERROR_MSG)
        if (amountReceived > 0) requester ! GiveBackChange(amountReceived)
        goto(Operational) using Initialized(inventory, prices)
      case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, amountReceived, requester)) =>
        val productPrice = prices.get(product).get
        if (amountReceived + amount < productPrice) {
          val totalInserted = amountReceived + amount
          requester ! Instruction(s"Please insert ${productPrice - totalInserted}$$")
          goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, amount + amountReceived, sender())
        } else {
          requester ! Deliver(product)
          if (amountReceived + amount > productPrice) {
            requester ! GiveBackChange(amountReceived + amount - productPrice)
          }

          val newInventory = inventory + (product -> (inventory.get(product).get - 1))
          goto(Operational) using Initialized(newInventory, prices)
        }
    }

    //This serves to handle any other message not catched before
    whenUnhandled{
      case Event(_, _) =>
        sender() ! VendingError("Command not found")
        stay()
    }

    onTransition{
      case stateA -> stateB => log.info(s"Transitioning from ${stateA} to ${stateB}")
    }

    initialize() // We need this call to start the actor
  }

}

package part1.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part1.actors.ChildActors.CreditCard.{AttachToAccount, CheckStatus}
import part1.actors.ChildActors.NaiveBankAccount.InitializeAccount

object ChildActors extends App {
  object Parent{
    case class CreateChild(name:String)
    case class TellChild(message:String)
  }

  class Parent extends Actor{
    import Parent._

    override def receive:Receive = {
      case CreateChild(name) => {
        println(s"${self.path} creating child")
        context.become(withChild(context.actorOf(Props[Child],name)))
      }
    }

    def withChild(ref: ActorRef):Receive = {
      case TellChild(message) => ref forward message
    }
  }

  class Child extends Actor{
    override def receive: Receive = {
      case message:String => println(s"${self.path}: I've got ${message}")
      case _ => println(s"${self.path}: I've got called")
    }
  }

  import Parent._
  val system = ActorSystem("ChildReference")
  val parent = system.actorOf(Props[Parent], "parent")

  parent ! CreateChild("child")
  parent ! TellChild("Ey Kiddo")


  //Actor hierarchies
  // parent -> child1 -> grandchild1 -> ...
  //        -> child2 -> ...
  // parent owns childs, but we also have top level (guardian) actors that owns the parent. There is 3 guardian actors per
  // actor system:
  // 1 - System: Manages system actors, like logging
  // 2 - User: Every actor created by system.actorOf is own by this actor
  // 3 - /: root guardian, manages system and user guardians

  // It is possible to select a specific actor by path
  val childSelection = system.actorSelection("/user/parent/child")
  childSelection ! "Message selection"

  /**
   * Never pass mutable actor state or the 'this' reference to child actors, because it can break actor encapsulation
   */
  object NaiveBankAccount{
    case class Deposit(amount:Int)
    case class WithDraw(amount:Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor{
    var amount = 0
    import NaiveBankAccount._
    import CreditCard._
    override def receive: Receive = {
      case InitializeAccount =>
        val ref = context.actorOf(Props[CreditCard], "card")
        ref ! AttachToAccount(this)
      case Deposit(amount) => deposit(amount)
      case WithDraw(amount) => withDraw(amount)
    }
    def deposit(funds:Int) = {
      println(s"${self.path}: Deposit ${funds} to ${amount}")
      amount += funds
    }
    def withDraw(funds:Int) = {
      println(s"${self.path}: WithDrawn ${funds} from ${amount}")
      amount -= funds
    }
  }
  object CreditCard{
    case class AttachToAccount(bankAccount: NaiveBankAccount) //Never do this, instead, pass the actor reference ALWAYS
    case object CheckStatus
  }
  class CreditCard extends Actor{
    var account  = null
    override def receive: Receive = {
      case AttachToAccount(acc) => context.become(attachToAccount(acc))
    }

    def attachToAccount(account: NaiveBankAccount):Receive={
      case CheckStatus => {
        println(s"${self.path}: Your message has been processed")
        // This is an example of breaking actor encapsulation, which like in this case it can be harmful
        // This is really hard to debug
        account.withDraw(1)
      }
    }
  }
  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/account/card")
  ccSelection ! CheckStatus
  // To solve the above, replace:
  // def attachToAccount(account: NaiveBankAccount) for def attachToAccount(account: ActorRef)
  // Every single interactions must happen through messages not calling methods on the Actor, this is called 'Closing
  // over' mutable state.

  system.terminate()
}

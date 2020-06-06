package part1.actors

import akka.actor.{Actor, ActorSystem, Props}
import part1.actors.ActorBasicExercises.BankAccount.{Deposit, Statement, WithDraw}
import part1.actors.ActorBasicExercises.Register.{Decrement, Increment}

object ActorBasicExercises extends App{

  /**
   * 1 - Create a counter actor that when increment or decrement messages are passed it does increment its internal variable
   * and prints the result in the screen
   */
  val actorSystem = ActorSystem("ActorBasicExercises")

  object Register{
    // It is a good practice to put the messages types inside the companion object of the actor, this is called the
    // "domain" of the actor
    case class Increment(amount:Int)
    case class Decrement(amount:Int)
  }

  class Register extends Actor{
    import Register._
    var internalValue:Int = 0

    override def receive: Receive = {
      case Increment(value) => modifyInternals(value)
      case Decrement(value) => modifyInternals(-value)
    }

    def modifyInternals(value:Int) = {
      internalValue += value
      println(s"New value is ${internalValue}")
    }
  }

  val register = actorSystem.actorOf(Props[Register], "register")
  register ! Increment(20)
  register ! Decrement(20)
  /**
   * 2 - Create a bank account with actors that has withdraw and deposit and replies with a success or failure
   */
  object BankAccount {

    case class WithDraw(amount: Int)

    case class Deposit(amount: Int)

    case class Statement()

    case class Success(msg: String)

    case class Failure(msg: String)

  }

  class BankAccount extends Actor{
    import BankAccount._
    var amount = 0

    override def receive: Receive = {
      case WithDraw(value) => if(amount > value) {
        amount -= value
        sender() ! Success(s"WithDrawn for ${value}")
      }else{
        sender() ! Failure(s"Not enought funds")
      }
      case Deposit(value) => if(value >= 0){
        amount += value
        sender() ! Success(s"Deposit ${value}")
      }else{
        sender() ! Failure(s"Invalid amount")
      }
      case Statement => println(s"The bank account value is: ${amount}")
    }
  }

  val account = actorSystem.actorOf(Props[BankAccount], "bankAccount")
  account ! WithDraw(20)
  account ! Deposit(20)
  account ! Statement()

  actorSystem.terminate()
}

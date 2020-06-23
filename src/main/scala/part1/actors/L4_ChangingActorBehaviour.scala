package part1.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part1.actors.L4_ChangingActorBehaviour.Mom.{Ask, Food, MomStart, VEGETABLE}

object L4_ChangingActorBehaviour extends App{

  object FussyKid{
    case object KidAccept
    case object KidReject
    val HAPPY = "HAPPY"
    val SAD = "SAD"
  }
  class FussyKid extends Actor{
    import FussyKid._
    import Mom._
    var state = HAPPY // Having variable state in the actor might be incredible hard to maintain
    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) => state match {
        case HAPPY => sender() ! KidAccept
        case SAD => sender() ! KidReject
      }
    }
  }

  object Mom{
    case class MomStart(kid:ActorRef)
    case class Food(food:String)
    case class Ask(message:String)
    val VEGETABLE = "VEGETABLE"
    val CHOCOLATE = "CHOCOLATE"
  }
  class Mom extends Actor{
    import Mom._
    import FussyKid._
    def initMessage(actorRef: ActorRef) ={
      kid ! Food(VEGETABLE)
      kid ! Ask("Do you want to play")
    }
    override def receive: Receive = {
      case MomStart(kid) => initMessage(kid)
      case KidReject => println("I wish I had aborted you")
      case KidAccept => println("Good!")
    }
  }
  val system = ActorSystem("FussyKidPlay")
  val kid = system.actorOf(Props[FussyKid])
  val mom = system.actorOf(Props[Mom])

  mom ! MomStart(kid)

  //This is an implementation of a Stateles Kid, which doesn't handle internal state
  // The receive message initially returns the happy receive handler which will be used for future messages
  // the context.become forces akka to change the handler by the handler passed as argument
  class StatelessFussyKid extends Actor{
    import Mom._
    import FussyKid._
    override def receive: Receive = happyReceive // Returns this object

    def happyReceive:Receive = {
      case Food(VEGETABLE) => context.become(sadReceive)
      case Food(CHOCOLATE) =>
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive:Receive = {
      case Food(VEGETABLE) =>
      case Food(CHOCOLATE) => context.become(happyReceive)// change handler to HappyReceive
      case Ask(_) => sender() ! KidReject
    }
  }

  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  mom ! MomStart(statelessFussyKid)


  class CumulativeFussyKid extends Actor{
    import Mom._
    import FussyKid._
    override def receive: Receive = happyReceive // Returns this object

    def happyReceive:Receive = {
      case Food(VEGETABLE) => {
        // context.become has two parameters, the second is a boolean that specifies if we want to fully replace the
        // method handler with a new method handler or if we pass false we will stack the new message handler into a stack of
        // message handlers, at all times when an actor needs to handle a message, akka would call the top most handler in
        // the stack, if the stack is empty, it will call the plan receive handler.
        //context.become(sadReceive) // second parameter defaults to true
        context.become(sadReceive, false)
      }//Change handler to SadReceive
      case Food(CHOCOLATE) => context.unbecome() // Returns the stack to the previous handler
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive:Receive = {
      case Food(VEGETABLE) => context.unbecome() // Returns the stack to the previous handler
      case Food(CHOCOLATE) => context.become(happyReceive, false)// change handler to HappyReceive
      case Ask(_) => sender() ! KidReject
    }
  }
  class CumulativeMom extends Mom{
    import Mom._
    override def initMessage(actorRef: ActorRef): Unit = {
      kid ! Food(VEGETABLE)
      kid ! Food(VEGETABLE)
      kid ! Food(CHOCOLATE)
      kid ! Ask("Do you want to play")
    }
  }
  // The stack of handlers would look like:
  // 1. Sat Receive
  // 2. Sad Receive
  // 3. Happy Receive
  // To remove a handler from the top of the sctack, we call the method context.unbecome
  val cumulativeFussyKid = system.actorOf(Props[CumulativeFussyKid])
  val cumulativeMom = system.actorOf(Props[CumulativeMom])
  cumulativeMom ! MomStart(statelessFussyKid)

  system.terminate()
}

package part1.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object L2_ActorCapabilities extends App{

  class SimpleActor extends Actor{
    override def receive: Receive = {
      case message:String => println(s"I am a simple actor which received: ${message}")
      case number:Integer => println(s"I am a simple actor which received the number: ${number}")
    }
  }

  val actorSystem = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = actorSystem.actorOf(Props[SimpleActor], "simpleActor")
  simpleActor ! "Hello"

  // 1 - Messages can be of any type, even your own types, but there are two conditions
  //      a) Messages have to be IMMUTABLE
  //      b) Messages have to be SERIALIZABLE
  simpleActor ! 42

  // 2 - Actors have information about the context and about themselves
  class NotThatSimpleActor extends Actor{
    //Context is passed by the actor system and contains information about the app context
    override def receive: Receive = {
      case "Hi" => sender() ! "Hello There"
      case s:String => println(s"${s} - ${context.self.path}") // context.self is equivalent to 'this' in the akka world
      case i:Int => self ! i.toString // we can use it to send messages to the same reference (self is context.self)
      case SayHiTo(actor) => {
        println(s"I am ${self.path}")
        actor ! "Hi"
      }
    }
  }

  // 3 - Actors can reply to messages by using the context and the sender() method to get a reference of the sender
  // sender() or context.sender() returns a reference to the actor that sent the message to this actor, this reference is
  // passed along with the ! method as implicit => def !(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit
  val alice = actorSystem.actorOf(Props[NotThatSimpleActor], "Alice")
  val bob = actorSystem.actorOf(Props[NotThatSimpleActor], "Bob")

  case class SayHiTo(actor:ActorRef)

  alice ! SayHiTo(bob)

  // This will pass a default sender "Actor.noSender". and when trying to reply to that sender, the message will end up in
  // the path {actorSystemPath}/deadLetters which is a kind of garbage collector for messages
  alice ! "Hi"

  // 4 - Message forwarding: by using 'forward' instead of '!', I keep the original sender of the message
  case class SendTo(message:String, actor:ActorRef)
  class ForwardActor extends Actor{
    override def receive: Receive = {
      case SendTo(msg, actor) => actor forward msg
    }
  }
  val forwardActor = actorSystem.actorOf(Props[ForwardActor], "forward")
  forwardActor ! SendTo("Hi", bob)

  actorSystem.terminate()
}

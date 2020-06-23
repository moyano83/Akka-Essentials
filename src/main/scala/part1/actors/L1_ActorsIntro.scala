package part1.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object L1_ActorsIntro extends App{

  // Part 1: actor system
  //The actor system is a heavy data structure which controls a bunch of threads under the hood which run actors
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  // Part 2-A: Creating actors
  // Actors are uniquely identified
  // Messages are passed to process asynchronously
  // Each actor has a unique way to process the message
  // You can not read or poke an actor
  class WordCountActor extends Actor {
    var totalWords:Int = 0
    // The domain of the Actor class is defined by the partial function receive, which type is Receive, which is an alias
    // for PartialFunction[Any,Unit]
    override def receive: Receive = {
      case message:String => {
        totalWords += message.split(" ").length
        println(s"Received: [${message}], Total count: ${totalWords}")
      }
      case _ => println("Word counter not dealing with the message")
    }
  }
  // Part 2-B: If we want to instantiate an actor which constructor takes parameters, we use the Props with arguments being
  // an instance of the class
  class NamedWordProcess(name:String) extends Actor{
    override def receive: Receive = {
      case "Hello"=> println(s"${name} => Hello, how are you?")
    }
  }

  // Part 3: Instantiating the app
  // The actors are not instantiated but requested to the actor system
  // A props with the actor to instantiate needs to be passed along with the name of the actor
  // An actor reference is returned, but it is not the actual instance of the actor, which is not exposed
  val wordCounter:ActorRef = actorSystem.actorOf(Props[WordCountActor], "wordCounter")

  // This is legal but not recommended
  val namedWordProcess:ActorRef = actorSystem.actorOf(Props(new NamedWordProcess("Test")), "NamedProcessor")

   // The recommended way to do this is through a companion object so the factory method creates the instances
  object NamedWordProcess{
    // This let the actor system
    def props(name:String): Props = Props(new NamedWordProcess("Test"))
  }
  val namedWordProcess2:ActorRef = actorSystem.actorOf(NamedWordProcess.props("Test"), "NamedProcessor")


  // Part 4: communicate with the actor reference
  // The message is passed assynchronously, if we have another actor we can't guarantee which one will process first
  // The ! message is also known by "tell"
  wordCounter ! "I am learning Akka, it is cool!"
  namedWordProcess ! "The named props" // <= This doesn't produce a result because the receive is not defined for the string
  actorSystem.terminate()

}

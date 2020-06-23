package part1.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object L7_ChildActorExercises extends App{
  /**
   * Exercise: Distributed word counting
   */
  object WordCountMaster{
    case class Initialize(n:Int)
    case class WordCountRequest(id:Int,text:String)
    case class WordCountReply(id:Int,count: Int)
  }

  class WordCountMaster extends Actor{
    import WordCountMaster._
    override def receive: Receive = {
      case Initialize(n) => context.become{
          initialized((0 to n).map(i => context.actorOf(Props[WordCountWorker], s"WordCounter${i}")).toArray)
        }
    }

    def initialized(actors:Array[ActorRef]):Receive = {
      case text:String => {
        println(s"${self.path} Received wordCount request")
        val actorId = (System.nanoTime() % actors.length).toInt
        actors(actorId) ! WordCountRequest(actorId, text)
      }
      case WordCountReply(id, count) => println(s"${self.path} => Count is ${count} msgId:${id}")
    }
  }

  class WordCountWorker extends Actor{
    import WordCountMaster._
    override def receive: Receive = {
      case WordCountRequest(id, text) => {
        println(s"${self.path} WordCount for [${text}]")
        sender() ! WordCountReply(id, text.split("\\s").length)
      }
    }
  }

  val system = ActorSystem("WordCount")
  val master = system.actorOf(Props[WordCountMaster], "Master")
  import WordCountMaster._
  master ! Initialize(5)
  Thread.sleep(500) // We give  the master time to initialize the childs
  master ! "We need to count the number of words in this text"

  system.terminate()
}

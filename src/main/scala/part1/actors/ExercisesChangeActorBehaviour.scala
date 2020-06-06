package part1.actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part1.actors.ExercisesChangeActorBehaviour.Counter.Increment

object ExercisesChangeActorBehaviour extends App{
  object Counter{
    case object Increment
    case object Decrement
    case object Print
  }

  /**
   * Exercise 1: Implement Counter with context.become
   */
  class Counter extends Actor{
    import Counter._
    val count = 0
    override def receive: Receive = counterWith(0)

    def counterWith(counter:Int): Receive = {
      case Increment => context.become(counterWith(counter+1),false)
      case Decrement => context.become(counterWith(counter-1),false)
      case Print => println(s"The total count is ${counter}")
    }
  }
  import Counter._
  val system = ActorSystem("ExercisesChangeActorBehaviourExercises")
  val counter = system.actorOf(Props[Counter])

  (1 to 5).foreach(_ => counter ! Increment)
  (1 to 3).foreach(_ => counter ! Decrement)
  counter ! Print

  /**
   * Exercise 2: Implement a simplified voting system
   * Citizen receives the vote message and when it does, it turns itself as havingVote
   * VoteAggregator should receive the aggregate vote message and ask all the citizens for their vote
   */
  case object VoteStatusRequest
  case class Vote(candidate:String)
  case class VoteStatusReply(candidate:Option[String])
  class Citizen extends Actor{
    override def receive: Receive = {
      case Vote(candidate) => context.become(votedFor(candidate))
      case VoteStatusRequest => sender ! VoteStatusReply(None)
    }

    def votedFor(candidate:String):Receive ={
      case Vote(_) => println(s"Already voted for ${candidate}")
      case VoteStatusRequest => sender ! VoteStatusReply(Some(candidate))
    }
  }

  case class AggregateVotes(citizens:Set[ActorRef])
  class VoteAggregator extends Actor{
    override def receive: Receive = {
      case AggregateVotes(citizens) => {
        citizens.foreach(_ ! VoteStatusRequest)
        context.become(aggregation(List(), citizens))
      }
    }

    def aggregation(votes: List[String], pending: Set[ActorRef]):Receive = {
      case VoteStatusReply(candidate) => {
        val newVotes:List[String] = if(candidate.isDefined) candidate.get :: votes else votes
        val stillToVote = pending - sender()
        if(stillToVote.isEmpty){
          newVotes.map(x=> (x,1)).foldLeft(collection.mutable.Map[String,Int]())((map,tuple) =>{
            map.put(tuple._1, map.getOrElse(tuple._1, 0) + tuple._2)
            map
          }).foreach({ case (candidate, votes) => println(s"Votes for [${candidate}] => ${votes}")})
          //print
        }else{
          context.become(aggregation(newVotes, stillToVote))
        }
      }
    }
  }

  val bob = system.actorOf(Props[Citizen])
  val alice = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(bob, alice, charlie, daniel))

  // The above should print the candidates and the number of vote each candidate have received
  system.terminate()
}

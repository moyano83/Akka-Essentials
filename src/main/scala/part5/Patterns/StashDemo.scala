package part5.Patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}
import part5.Patterns.StashDemo.ResourceActor.Write

object StashDemo extends App {
  /**
   * Stashes allows actors to set messages aside for later that they can't ot they  shouldn't process at a particular
   * point in time
   */
  // There is a resource actor (file, db...) that we care about the status only. There is two status:
  //  - Open: It can process requests to read/write to the resource
  //  - Closed: It can not process requests to read/write to the resource and will pospone thos ops until status is open
  // Behaviour:
  //  - Starts in close status, when it receives the open message then it switches to open state
  //  - When resource actor is open:
  //      * Read, Write are handled
  //      * When it receives Closed message then it switches to close state
  //  - When resource actor us closed
  //      * Read, Write are POSTPONED (messages are stashed)
  //      * When it receives Open, it switches to open status, processes the stash and then the rest of the messages in order
  object ResourceActor {

    case object Open

    case object Closed

    case object Read

    case class Write(data: String)

  }

  class ResouceActor extends Actor with ActorLogging with Stash{
    import ResourceActor._
    private var data:String = "" // <-- This is the 'resource' starting empty

    override def receive: Receive =  closed()

    private def closed():Receive = {
      case Open =>
        log.info("Opening circuit")
        unstashAll() // Unstashed messages for processing before doing a context switch
        context.become(open())
      case Closed =>{
          log.info("Circuit already closed")
      }
      case message=>
        log.info("Stashing message:" + message.toString)
        stash() // instruction to stash the message (parameterless)
    }

    private def open():Receive = {
      case Closed =>
        log.warning("Closing circuit")
        context.become(closed())
      case Read =>
        log.info(s"Read received: Data is ${data}")
      case Write(msg) =>
        log.info(s"Writting ${msg}")
        data += msg
      case Open =>{
        log.info("Circuit already open")
      }
    }
  }

  val system = ActorSystem("StashDemo")
  import ResourceActor._
  val resource = system.actorOf(Props[ResouceActor])

  resource ! Write("Test")
  resource ! Read
  resource ! Open
  resource ! Write(" Other test")
  resource ! Read

  Thread.sleep(1000)
  system.terminate()

  /**
   * Things to be careful with stash:
   * - Potential memory bounds (doesn't really happen in practice)
   * - Potential mailboux bounds with unstash: if the number of stash messages is huge, on unstash, the mailbox might
   * overflown
   * - Do not stash the same message twice
   * - The stash message overrides preStart so the trait Stash must be mixed the last
   */
}

package part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App{
  /**
   * Mailboxes are data structures in the actor references that stores messages
   */
  val system = ActorSystem("MailboxDemo", ConfigFactory.load().getConfig("mailBoxesDemo"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case msg => log.info(msg.toString)
    }
  }

  /**
   * Interesting case #1 : custom priority mailbox
   * Priority in order of importance is P0, P1, P2...
   * Below is an example of how to do this, this class needs to have the below arguments for the constructor as in Runtime
   * the instance of the class is going to be created by reflection
   */
  // Step 1: Mailbox definition
  class SupportTicketPriorityMailBox(settings:ActorSystem.Settings, config:Config)
    extends UnboundedPriorityMailbox(PriorityGenerator{
      case message:String if message.startsWith("[P0]") => 0
      case message:String if message.startsWith("[P1]") => 1
      case message:String if message.startsWith("[P2]") => 2
      case message:String if message.startsWith("[P3]") => 3
      case _ => 4
    })

  // Step 2: make it known in the config (look at suppor-ticket-dispatcher in the application.conf)
  // Step 3:  Attach the dispatcher to an actor
  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"), "ticketActor")
  supportTicketLogger ! PoisonPill // This is postponed because the priority of this messages that are in the queue
  supportTicketLogger ! "[P3] This is a message of category 3"
  supportTicketLogger ! "[P3] This is a message of category 3"
  supportTicketLogger ! "[P3] This is a message of category 3"
  supportTicketLogger ! "[P3] This is a message of category 3"
  supportTicketLogger ! "[P3] This is a message of category 3"
  supportTicketLogger ! "[P0] This is a message of category 0 URGENT!!"
  supportTicketLogger ! "[P1] This is a message of category 1 less urgent!!"

  /**
   * Interesting case #2 : Control-Aware Mailbox
   * We'll use unbounded control-aware maibox supplied by akka
   */
  // METHOD 1
  // Step 1: Mark a message as a priority message by marking it as a control message
  case object ManagementTicket extends ControlMessage

  // Step 2: Configure who gets the mailbox
  // - Make the actor attach to the mailbox instead of letting the dispatcher route the message
  val controlAwareActor1 = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"), "ControlAwareActor")
  controlAwareActor1 ! "[P3] This is a message of category 3"
  controlAwareActor1 ! "[P0] This is a message of category 0 URGENT!!"
  controlAwareActor1 ! ManagementTicket // <-- This ticket is prioritized

  // METHOD 2 - Using deployment config in application.conf
  val controlAwareActor2 = system.actorOf(Props[SimpleActor], "AltControlAwareActor")
  controlAwareActor2 ! "[P3] This is a message of category 3"
  controlAwareActor2 ! "[P0] This is a message of category 0 URGENT!!"
  controlAwareActor2 ! ManagementTicket // <-- This ticket is prioritized


  Thread.sleep(1000)
  system.terminate()
}

package part4.infrastructure

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing.{ActorRefRoutee, Broadcast, FromConfig, RoundRobinGroup, RoundRobinPool, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory

object Routers extends App{

  /**
   * Method 1 of creating routers: Manual
   */
  class ManualRouterMaster extends Actor with ActorLogging{
    //This handles the routing logic, the parameters that this app companion object receives are:
    // 1 - Router logic to tell how to route the messages to the children (RoundRobin in this case)
    // 2 - ActorRoutee which is an actor that receives a routing logic

    private val slaves = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"slave_${i}")
      context.watch(slave)
      ActorRefRoutee(slave)
    }
    private val router = Router(RoundRobinRoutingLogic(),  slaves)

    override def receive: Receive = {
      // The message is routed using the route method which accepts two parameters which are:
      // 1 - Message to be routed
      // 2 - Requester of the message (we pass the sender reference but we can pass 'self' in case we want to act as
      // middle man
      case msg => router.route(msg, sender())
      // We need to handle the termination of the routees, so if one dies, we need to remove it from the routing, and then
      // we need to add one to replace the slave that died
      case Terminated(ref) =>
        router.removeRoutee(ref)
        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router.addRoutee(newSlave)
    }
  }

  class Slave extends Actor with ActorLogging{
    override def receive: Receive = {
      case msg => log.info(msg.toString())
    }
  }
  val system = ActorSystem("routersDemo", ConfigFactory.load().getConfig("routersDemo"))
  val master = system.actorOf(Props[ManualRouterMaster])

  for(i<-  1 to 10) master ! s"hello ${i} from the world"

  /**
   * There are several routing strategies:
   * 1 - Round Robin
   * 2 - Random
   * 3 - Smallest Mailbox
   * 4 - Broadcast
   * 5 - Scatter-Gatter-first: waits for the first reply of broadcast, discard subsequential responses
   * 6 - Tail chopping: Forwards the next message sequentially until the first reply is provided and then discards
   * subsequend replies
   * 7 - consistent hashing: All messages with the same hash gets to the same actor
   */
  /**
   * Method 2: A router actor with its own children
   */
  // We call RoundRobinPool needs the props reference of the class that I want to act as router, and name. Shown here:
  Thread.sleep(500)
  val poolMaster = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "SimplePoolMaster")
  for(i<-  1 to 10) poolMaster ! s"hello ${i} from the world"

  //It is possible to pass a specific configuration to this application to set appropriate settings for the pool
  // poolMaster2 is the name of the configuration we have set in application.conf for this pool
  Thread.sleep(500)
  val poolMaster2 = system.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
  for(i<-  1 to 10) poolMaster2 ! s"hello ${i} from the world Pool master 2"

  /**
   * Method number 3: Routers with actors created elsewhere
   * In another part of my application I created a val with the slave list and having the paths of the actors, I want to
   * create a router for them
   */
  Thread.sleep(500)
  val slavesToRoute = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_${i}")).toList
  val slavePaths = slavesToRoute.map(ref => ref.path.toString)

  Thread.sleep(500)
  //Creation of the slavesToRoute group router
  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props(), "groupMaster")
  for(i<-  1 to 10) groupMaster ! s"hello ${i} from the world group master"

  Thread.sleep(500)
  // It is also possible to create this group from configuration
  val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster2")
  for(i<-  1 to 10) groupMaster2 ! s"hello ${i} from the world group master 2"

  /**
   * There is also some special messages on this routing logic
   */
  // This sends a message to all the actors under this master regardless of the routing strategy
  // PoisonPill and Kill are exceptions to this, those messages are not broadcasted, as well as management messages such
  // as addRoute, removeRoutee, getRoutee ... which are handled only by the routing actor
  groupMaster2 ! Broadcast("Hello Everyone")
  system.terminate()
}

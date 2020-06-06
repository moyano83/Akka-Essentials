package part1.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object IntroAkkaConfig extends App {
  // There is multiple ways to pass akka configuration
  /**
   * Method 1: Inline configuration
   */
  val configString =
    """
      |akka {
      | loglevel = "DEBUG"
      |}
      |""".stripMargin

  val config = ConfigFactory.parseString(configString)
  //Individual keys on the config can be loaded like below:
  println(config.getString("akka.loglevel"))
  class ActorWithLogging extends Actor with ActorLogging{
    override def receive: Receive = {
      case msg => log.info(s"Actor logging message received: ${msg}")
    }
  }

  val system = ActorSystem("ConfigDemo", ConfigFactory.load(config)) // The config is loaded with the extra properties passed
  val actor = system.actorOf(Props[ActorWithLogging])
  actor ! "Log this"
  system.terminate()

  /**
   * Method 2: application.conf in resources
   */
  // If no explicit config is passed, Akka automatically picks up whatever is in application.conf
  val system2 = ActorSystem("ConfigDemoFromResources")
  val actor2 = system2.actorOf(Props[ActorWithLogging])
  actor2 ! "Log this other thing"
  system2.terminate()

  // If you need to pass different configurations to different actor system, you can have multiple namespaces in the same
  // configuration file
  /**
   * Method 3: separate config in the same file
   */
  // The getConfig("path") serves to pass the namespace of the config
  val system3 = ActorSystem("ConfigDemoFromResources", ConfigFactory.load().getConfig("mySpecialConfig"))
  val actor3 = system3.actorOf(Props[ActorWithLogging])
  actor3 ! "Log this with mySpecialConfig"
  system3.terminate()
  /**
   * Method 4: separate config in another file
   */
  val system4 = ActorSystem("ConfigDemoFromResources", ConfigFactory.load("secret/SecretConfiguration.conf"))
  val actor4 = system4.actorOf(Props[ActorWithLogging])
  actor4 ! "Log this with separate file config"
  system4.terminate()

  /**
   * Method 5: Different config formats are supported like JSON, properties
   */
  val system5 = ActorSystem("ConfigDemoFromResources", ConfigFactory.load("jsonConfig.json"))
  val actor5 = system5.actorOf(Props[ActorWithLogging])
  actor5 ! "Log this with separate file config in JSON format"
  system5.terminate()
}

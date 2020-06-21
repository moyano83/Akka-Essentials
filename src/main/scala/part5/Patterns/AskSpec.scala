package part5.Patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class AskSpec extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll{
  import AskSpec._
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "An authenticator Manager" should{
    authenticatorTestSuit(Props[AuthManager])
  }

  "An piped authenticator Manager" should{
    authenticatorTestSuit(Props[PipedAuthManager])
  }

  def authenticatorTestSuit(props:Props) = {
      "fail to authenticate a non authorized user" in {
        val authManager = system.actorOf(props)
        authManager ! Authenticate("u", "p")
        expectMsg(AuthFailure("User not found"))
      }

      "fail to authenticate if the password is invalid" in {
        val authManager = system.actorOf(props)
        authManager ! RegisterUser("u", "p")
        authManager ! Authenticate("u", "_p_")

        expectMsg(AuthFailure("Password doesn't match"))
      }

      "successfully register a user" in {
        val authManager = system.actorOf(props)
        authManager ! RegisterUser("u", "p")
        authManager ! Authenticate("u", "p")

        expectMsg(AuthSuccess)
      }
    }
}

object AskSpec{
  //Assume this code is somewhere else in your App
  case class Read(key:String)
  case class Write(key:String, value:String)
  /**
   * This actor holds a simple map between string string, used for authenticate a user in the app
   */
  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv:Map[String,String]):Receive = {
      case Read(k) =>
        log.info(s"Trying to read ${k}")
        sender() ! kv.get(k)
      case Write(k,v) => {
        log.info(s"Writting the value ${v} for the key ${k}")
        context.become(online(kv + (k -> v)))
      }
    }
  }

  case class RegisterUser(user:String, pass:String)
  case class Authenticate(user:String, pass:String)
  case class AuthFailure(msg:String)
  case object AuthSuccess
  // Step 1: Import the akka.pattern.ask
  import akka.pattern.ask
  class AuthManager extends Actor with ActorLogging {
    import scala.concurrent.duration._
    protected val authDB = context.actorOf(Props[KVActor])
    // Step 2: Create the logistics for execution
    implicit val timeout:Timeout = 1 second // This is needed as the ask method requires it
    implicit val executionContext: ExecutionContext = context.dispatcher // Neded to run the future computation

    override def receive: Receive = {
      case RegisterUser(username,password) => authDB ! Write(username,password)
      // Instead of creating a method to do a context.become and then pass a function that wait for a message from authDB
      // and then validate from a given response, which user is that authentication request for and other complications,
      // there is a solution which is the Ask pattern.
      // The Ask method is available through '?' after importing akka.pattern.ask. The ask method returns a future with
      // possibly a value
      case Authenticate(username,password) => handleAuthentication(username, password)
    }

    def handleAuthentication(username:String, password:String) = {
      // Step 3: Use the ? to get the future
      val future = authDB ? Read(username)
      // Step 4: Handle the future
      // Step 5: Never call methods on an actor instance or access mutable state on onComplete
      val originalSender = sender()
      future.onComplete{
        // If we didn't store the sender in a separate val, as the future is executed in a separate thread, it will have
        // a different sender in this case (authDB).
        // NEVER CALL METHODS OR ACCESS MUTABLE STATE IN CALLBACKS!!
        case Success(None) =>
          log.warning("User not found")
          originalSender ! AuthFailure("User not found") // don't use sender(). This is known as 'closing over'
        case Success(Some(passwordDB)) =>
          if(password != passwordDB) originalSender ! AuthFailure("Password doesn't match")
          else originalSender ! AuthSuccess
        case Failure(ex) =>
          log.error("Unable to complete the operation", ex)
          originalSender! AuthFailure("Internal Error ")
      }
    }
  }
  import akka.pattern.pipe
  class PipedAuthManager extends AuthManager {
    override def handleAuthentication(username: String, password: String): Unit = {
      // Step 3: Ask the actor
      val response = authDB ? Read(username) // returns Future[Any]
      // Step 4: Process the future
      val passwordFuture = response.mapTo[Option[String]] // This is a future[Option[String]]
      val responseFuture = passwordFuture.map{
        case None => AuthFailure("User not found")
        case Some(passwordDB) =>
          if(password != passwordDB) AuthFailure("Password doesn't match")
          else AuthSuccess
      } // Future[Any] will be completed with the response I will send back

      // Step 5: Pipe the resulting future to the actor you want to respond to
      // pipeTo does the following: when the future completes send the response to the actorRef, this avoid having
      // problems in dealing with actor encapsulation
      responseFuture pipeTo sender()
    }
  }
}

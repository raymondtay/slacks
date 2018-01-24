package slacks.core.program.supervisor

import akka.actor._
import akka.http.scaladsl.model.StatusCodes.ClientError
import akka.http.scaladsl.model.{ExceptionWithErrorInfo, ErrorInfo, EntityStreamException, EntityStreamSizeException, IllegalRequestException, IllegalResponseException, IllegalUriException}


import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ WordSpecLike, Matchers, BeforeAndAfterAll }
import akka.testkit.{ TestActors, TestKit, ImplicitSender, EventFilter }

/**
  * This specification tests the restart strategy for [[slacks]] and we have
  * basically two (i.e. 2) supervisors that basically do not restart and one
  * that restarts the children actors when the exceptions are detected.
  *
  * By default, we escalate any errors to the top-level actor if its not caught
  * by either [[SlacksBase]] or [[SlacksRestartN]].
  *
  * @author Raymond Tay
  */

class Child extends Actor {
  var state = 0
  def receive = {
    case ex : ExceptionWithErrorInfo ⇒ throw ex
    case ex : Exception ⇒ throw ex
    case x: Int        ⇒ state = x
    case "get"         ⇒ sender() ! state
  }
}

case class UserDefinedException() extends Exception

/**
  * Specification to make sure the restart strategies work as expected.
  * @author Raymond Tay
  */
class RestartStrategySpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  private[this] var supervisorNoRestart : ActorRef = _
  private[this] var supervisorRestartN  : ActorRef = _

  def this() = this(ActorSystem(
    "RestartStrategySpec",
    ConfigFactory.parseString("""
      akka {
        loggers = ["akka.testkit.TestEventListener"]
        loglevel = "WARNING"
      }
      """)))

  override def beforeAll {
    supervisorNoRestart = system.actorOf(Props[SupervisorNoRestart], "supervisorNoRestart")
    supervisorRestartN = system.actorOf(Props[SupervisorRestartN], "supervisorRestartN")
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A restart supervisor" must {
    "apply the chosen strategy for its child" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]

      child ! 42
      child ! "get"
      expectMsg(42)
    }

    "apply the Escalate strategy for its child when it isn't caught" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]

      child ! 42
      child ! "get"
      expectMsg(42)
      child ! UserDefinedException()
    }

    "apply the Restart strategy for its child when it sees EntityStreamSizeException" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorRestartN ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child2 ! 24  // set the state

      child ! EntityStreamSizeException(limit = 100, actualSize = Some(99))

      child ! "get"
      expectMsg(0) // restarted hence , 0 is returned

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Restart strategy for its child when it sees EntityStreamException" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorRestartN ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child2 ! 24  // set the state

      child ! EntityStreamException(ErrorInfo("Awww shucks: entity stream error."))

      child ! "get"
      expectMsg(0) // restarted hence , 0 is returned

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Restart strategy for its child when it sees IllegalRequestException" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorRestartN ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child2 ! 24  // set the state

      child ! IllegalRequestException(ErrorInfo("Awww shucks: illegal request"), ClientError(500)(reason = "", defaultMessage = ""))

      child ! "get"
      expectMsg(0) // restarted hence , 0 is returned

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Restart strategy for its child when it sees IllegalResponseException" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorRestartN ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child2 ! 24  // set the state

      child ! IllegalResponseException(ErrorInfo("Awww shucks: illegal request"))

      child ! "get"
      expectMsg(0) // restarted hence , 0 is returned

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Stop strategy for its child when it sees NPE" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]
      watch(child) // the child is being watched

      child ! new NullPointerException("Awww shucks.")
      expectMsgPF() { case Terminated(`child`) ⇒ () }
    }
 
    "apply the Stop strategy for its child when it sees IllegalUriException" in {
      supervisorRestartN ! Props[Child]
      val child = expectMsgType[ActorRef]
      watch(child) // the child is being watched

      child ! IllegalUriException(ErrorInfo("illegal shit."))
      expectMsgPF() { case Terminated(`child`) ⇒ () }
    }
  }

  "A no-restart supervisor" must {
    "apply the chosen strategy for its child" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]

      child ! 42
      child ! "get"
      expectMsg(42)
    }

    "apply the Escalate strategy for its child when it isn't caught" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]

      child ! 42
      child ! "get"
      expectMsg(42)
      child ! UserDefinedException()
    }

    "apply the Restart strategy for its child when it sees EntityStreamSizeException" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorNoRestart ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child ! "get"
      expectMsg(22) // get the state

      child2 ! 24  // set the state

      child ! EntityStreamSizeException(limit = 100, actualSize = Some(99))
      expectMsgPF() { case t @ Terminated(`child`) ⇒  () } // terminated

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Restart strategy for its child when it sees EntityStreamException" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorNoRestart ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child ! "get"
      expectMsg(22) // get the state

      child2 ! 24  // set the state

      child ! EntityStreamException(ErrorInfo("Awww shucks: entity stream error."))
      expectMsgPF() { case t @ Terminated(`child`) ⇒  () } // terminated

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Restart strategy for its child when it sees IllegalRequestException" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorNoRestart ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child ! "get"
      expectMsg(22) // get the state
      child2 ! 24  // set the state

      child ! IllegalRequestException(ErrorInfo("Awww shucks: illegal request"), ClientError(500)(reason = "", defaultMessage = ""))
      expectMsgPF() { case t @ Terminated(`child`) ⇒  () } // terminated

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Restart strategy for its child when it sees IllegalResponseException" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]
      supervisorNoRestart ! Props[Child]
      val child2 = expectMsgType[ActorRef]
      watch(child) // the child is being watched
      watch(child2) // the child is being watched

      child ! 22  // set the state
      child ! "get"
      expectMsg(22) // get the state
      child2 ! 24  // set the state

      child ! IllegalResponseException(ErrorInfo("Awww shucks: illegal request"))
      expectMsgPF() { case t @ Terminated(`child`) ⇒  () } // terminated

      child2 ! "get"
      expectMsg(24) // its a 1-1 strategy, this should not be affected.
    }
 
    "apply the Stop strategy for its child when it sees NPE" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]
      watch(child) // the child is being watched

      child ! new NullPointerException("Awww shucks.")
      expectMsgPF() { case Terminated(`child`) ⇒ () }
    }
 
    "apply the Stop strategy for its child when it sees IllegalUriException" in {
      supervisorNoRestart ! Props[Child]
      val child = expectMsgType[ActorRef]
      watch(child) // the child is being watched

      child ! IllegalUriException(ErrorInfo("illegal shit."))
      expectMsgPF() { case Terminated(`child`) ⇒ () }
    }
  }

}

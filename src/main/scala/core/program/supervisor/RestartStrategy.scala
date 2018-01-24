package slacks.core.program.supervisor

import akka.actor.Actor
import akka.actor.Props
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import akka.http.scaladsl.model.{EntityStreamException, EntityStreamSizeException, IllegalRequestException, IllegalResponseException, IllegalUriException}

import scala.language.postfixOps
import scala.concurrent.duration._

/**
  * If we see a NPE, then its natural to stop the actor that caused it (It
  * does not make sense to apply the decider to all actors of this type).
  * The caveat is that the message is lost and no longer present.
  * If there was some other error, then we defer to Akka as highlighted over
  * here
  * [https://doc.akka.io/docs/akka/current/fault-tolerance.html#fault-handling-in-practice]
  * but even if that doesn't work, then we would apply the Escalation
  * strategy.
  *
  * @author Raymond Tay
  */
trait SlacksBase extends Actor {

 /* In cases where its an illegal uri or NPE, makes sense to log errors and
  * stop
  */
 val default : Decider = {
    case _ : EntityStreamException ⇒ Restart
    case _ : EntityStreamSizeException ⇒ Restart
    case _ : IllegalRequestException ⇒ Restart
    case _ : IllegalResponseException ⇒ Restart
    case _ : IllegalUriException ⇒ Stop
    case _ : NullPointerException ⇒ Stop
    case t ⇒ super.supervisorStrategy.decider.applyOrElse(t, (_: Any) ⇒ Escalate)
  }

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 0)(default)

}

trait SlacksRestartN extends Actor {

  /* In cases where its an illegal uri or NPE, makes sense to log errors and
   * stop
   */
  val default : Decider = {
    case _ : EntityStreamException ⇒ Restart
    case _ : EntityStreamSizeException ⇒ Restart
    case _ : IllegalRequestException ⇒ Restart
    case _ : IllegalResponseException ⇒ Restart
    case _ : IllegalUriException ⇒ Stop
    case _ : NullPointerException ⇒ Stop
    case t ⇒ super.supervisorStrategy.decider.applyOrElse(t, (_: Any) ⇒ Escalate)
  }

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 3 minutes)(default)

}

class SupervisorNoRestart extends SlacksBase {
  def receive = {
    case p : Props ⇒ sender() ! context.actorOf(p)
  }
}

class SupervisorRestartN extends SlacksRestartN {
  def receive = {
    case p : Props ⇒ sender() ! context.actorOf(p)
  }
}

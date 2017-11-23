package slacks.core.tracer

import io.opentracing.ActiveSpan
import io.opentracing.Tracer
import io.opentracing.propagation.Format.Builtin
import io.opentracing.propagation.TextMapInjectAdapter
import io.opentracing.tag.Tags

/**
  * Tracing of API calls using Inject-Extract propagation
  * see http://opentracing.io/documentation/pages/api/cross-process-tracing.html
  *
  * @author Raymond Tay
  * @version 1.0
  */

object TracerFunctions extends Defaults {
  import scala.util.Try
  import cats._, data._, implicits._
  import org.atnos.eff._
  import org.atnos.eff.all._
  import org.atnos.eff.future._
  import org.atnos.eff.syntax.all._
  import org.atnos.eff.syntax.future._

  import scala.collection.JavaConversions._
  import scala.collection.JavaConverters._

  type ReadTracer[A] = Reader[Tracer, A]
  type LogTracer[A] = Writer[String, A]
  type TracerStack = Fx.fx4[ReadTracer, LogTracer, Throwable Either ?, Eval]

  /**
    * Effect to trace the API calls
    * @param f a closure or effect which would be invoked
    * @param message a map of (k,v) where you want the consumer of the effects
    * to act on
    * @return the result of running `f`
    */
  def traceEffect[A](f: ⇒ A)(message: Message) : Eff[TracerStack, A] =
    for {
      tracer <- ask[TracerStack, Tracer]
      -      <- tell[TracerStack, String](s"Got the tracer")
      _      <- fromEither[TracerStack, Throwable,Tracer](activateSpan(tracer)(message))
      _      <- tell[TracerStack, String](s"Activated the tracer")
      result <- fromEither[TracerStack, Throwable, A](Either.catchNonFatal(f))
      _      <- tell[TracerStack, String](s"Closure function was invoked, with trace")
    } yield result

  private def activateSpan(tracer: Tracer)(message: Message) : Either[Throwable, Tracer] =
    Either.catchNonFatal{
      val activeSpan =
        tracer.buildSpan("collect-slack-channels")
          .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
          .withTag(Tags.COMPONENT.getKey(), "slacks-client")
          .startActive()
      tracer.inject(activeSpan.context(), Builtin.TEXT_MAP, new TextMapInjectAdapter(message))
      tracer
    }

}


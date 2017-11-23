package slacks.core

import com.uber.jaeger.Configuration
import io.opentracing.{ActiveSpan, Tracer}
import io.opentracing.util.GlobalTracer

package object tracer {

  // [Reference docs](http://opentracing.io/documentation/pages/api/cross-process-tracing.html)
  //
  trait Defaults {
    val samplingPeriod = 1
    val constantSamplerCfg = new Configuration.SamplerConfiguration("const", samplingPeriod)

    val logSpans = true
    val agentHost = "localhost"
    val agentPort = 0
    val flushIntervalMillis = 1000
    val maxQueueSize = 10000
    val reporterCfg = new Configuration.ReporterConfiguration(logSpans, agentHost, agentPort, flushIntervalMillis, maxQueueSize)
    lazy val globalTracer =
      GlobalTracer.register(
      new Configuration(
          "SlacksTracer",
          constantSamplerCfg,
          reporterCfg).getTracer())
  }

  // Allows the developer to carry context-sensitive information during
  // cross-processing tracing.
  type Message = collection.immutable.HashMap[String,String]

}

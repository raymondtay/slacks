import sbt._

object Dependencies {

  // Versions
  val catsVersion       = "0.9.0"
  val akkaHttpVersion   = "10.0.10"
  val akkaVersion       = "2.4.19"
  val scalaCheckVersion = "1.13.4"
  val specs2Version     = "4.0.1"
  val circeVersion      = "0.8.0"
  val scalaLoggerVersion = "3.7.2"
  val logbackClassic     = "1.2.3"
  val fastparseVersion   = "1.0.0"
  val typesafecfgVersion = "1.3.1"
  val effVersion         = "4.5.0"
  val jaegerCoreVersion  = "0.22.0-RC2"
  val openTracingVersion = "0.30.0"

  // Libraries
  val cats           = "org.typelevel" %% "cats-core" % catsVersion
  val akkaHttp       = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpTest   = "com.typesafe.akka" %% "akka-http-testkit" %  akkaHttpVersion
  val scalaCheckTest = "org.scalacheck" %% "scalacheck" % scalaCheckVersion
  val specs2Test     = "org.specs2" %% "specs2-core" % specs2Version
  val specs2ScalaCheckTest = "org.specs2" %% "specs2-scalacheck" % specs2Version
  val akkaStream           = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaActors           = "com.typesafe.akka" %% "akka-actor"  % akkaVersion
  val circeJson            =  Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-optics"
    ).map(_ % circeVersion)
  val logger = Seq(
    "ch.qos.logback" % "logback-classic" % logbackClassic,
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggerVersion
    )
  val openTracing = Seq(
    "io.opentracing" % "opentracing-api" % openTracingVersion ,
    "io.opentracing" % "opentracing-util" % openTracingVersion,
    "com.uber.jaeger" % "jaeger-core" % jaegerCoreVersion)
  val fastparse = "com.lihaoyi" %% "fastparse" % fastparseVersion
  val typesafecfg =  "com.typesafe" % "config" % typesafecfgVersion
  val eff = "org.atnos" %% "eff" % effVersion
  
  // Grouping the libraries to logical units
  val generalLibs = Seq(cats, akkaHttp, akkaStream, akkaActors, fastparse, eff) ++ logger ++ circeJson ++ openTracing

  val testLibs = Seq(akkaHttpTest, specs2ScalaCheckTest, specs2Test).map( _ % Test )

}

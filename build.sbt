import Dependencies._

val commonSettings = Seq(
  name := "slacks",
  organization := "org.slacks",
  description := "Simple library for Slack APIs",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.11",
  scalacOptions ++= Seq("-deprecation", "-feature", "-Yrangepos", "-Ypartial-unification")
)

val codeCoverageSettings = Seq(
 coverageExcludedPackages := "",
 coverageMinimum := 80,
 coverageFailOnMinimum := true
)

coverageExcludedPackages := "slacks\\.core\\.program\\.RealHttpService"

lazy val slacks = (project in file("."))
  .settings(
    commonSettings ++ codeCoverageSettings,
    libraryDependencies ++= (generalLibs ++ testLibs)
  )

enablePlugins(JavaServerAppPackaging)

resolvers += Resolver.sonatypeRepo("releases")

// Supporting code in markdowns
enablePlugins(TutPlugin)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")

// if your project uses multiple Scala versions, use this for cross building
addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary)

// if your project uses both 2.10 and polymorphic lambdas
libraryDependencies ++= (scalaBinaryVersion.value match {
  case "2.10" =>
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full) :: Nil
  case _ =>
    Nil
})

// All tests are run sequentially
parallelExecution in Test := false

scapegoatVersion in ThisBuild := "1.1.0"

githubOwner := "raymondtay"
githubRepository := "slacks"


// http://www.scalastyle.org/sbt.html 
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
// for autoplugins
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.1")
// https://github.com/sksamuel/sbt-scapegoat
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.9")

addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"    % "1.0.1")

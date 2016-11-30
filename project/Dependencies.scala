import DependenciesVersions._
import sbt._

object Dependencies {
  val akkaDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
    "org.json4s" %% "json4s-native" % json4sVersion
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion % "test"
  )

  val additionalResolvers = Seq(
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
  )
}

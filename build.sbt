name := "macwire-autowire"

version := "0.1"

scalaVersion := "2.13.7"
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings

import sbt._
import Keys._

import scala.util.Try
import scala.sys.process.Process
import complete.DefaultParsers._

val doobieVersion = "1.0.0-RC1"
val http4sVersion = "0.23.7"
val circeVersion = "0.14.1"
val tsecVersion = "0.4.0"
val sttpVersion = "3.3.18"
val prometheusVersion = "0.14.1"
val tapirVersion = "0.20.0-M3"
val macwireVersion = "2.5.3"

val dbDependencies = Seq(
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-hikari" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion
)

val httpDependencies = Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion,
  "com.softwaremill.sttp.client3" %% "http4s-backend" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "slf4j-backend" % sttpVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion
)

val monitoringDependencies = Seq(
  "io.prometheus" % "simpleclient" % prometheusVersion,
  "io.prometheus" % "simpleclient_hotspot" % prometheusVersion,
  "com.softwaremill.sttp.client3" %% "prometheus-backend" % sttpVersion
)

val jsonDependencies = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.client3" %% "circe" % sttpVersion
)

val loggingDependencies = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "ch.qos.logback" % "logback-classic" % "1.2.9",
  "org.codehaus.janino" % "janino" % "3.1.6",
  "de.siegmar" % "logback-gelf" % "4.0.2"
)

val configDependencies = Seq(
  "com.github.pureconfig" %% "pureconfig" % "0.17.1",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.1"
)

val baseDependencies = Seq(
  "org.typelevel" %% "cats-effect" % "3.3.0",
  "com.softwaremill.common" %% "tagging" % "2.3.2",
  "com.softwaremill.quicklens" %% "quicklens" % "1.8.2"
)

val apiDocsDependencies = Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion
)

val macwireDependencies = Seq(
//  "com.softwaremill.macwire" %% "macros" % macwireVersion,
  "com.softwaremill.macwire" %% "macrosautocats" % "2.5.5"
).map(_ % Provided)

val scalatest = "org.scalatest" %% "scalatest" % "3.2.10" % Test

val commonDependencies = baseDependencies ++ loggingDependencies ++ configDependencies

lazy val commonSettings = commonSmlBuildSettings ++ Seq(
  organization := "com.softwaremill.autowire",
  scalaVersion := "2.13.7",
  libraryDependencies ++= commonDependencies,
)

def haltOnCmdResultError(result: Int) {
  if (result != 0) {
    throw new Exception("Build failed.")
  }
}

def now(): String = {
  import java.text.SimpleDateFormat
  import java.util.Date
  new SimpleDateFormat("yyyy-MM-dd-hhmmss").format(new Date())
}

lazy val rootProject = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "autowire"
  ).settings(
  libraryDependencies ++= dbDependencies ++ httpDependencies ++ jsonDependencies ++ apiDocsDependencies ++ monitoringDependencies ++ macwireDependencies,
  Compile / mainClass := Some("com.softwaremill.macwire.autowire.Main")
)

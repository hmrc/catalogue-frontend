import play.routes.compiler.StaticRoutesGenerator
import sbt.Keys._
import sbt._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.SbtAutoBuildPlugin._
import play.sbt.routes.RoutesKeys.routesGenerator


trait MicroService {
  val appName: String

  lazy val appDependencies : Seq[ModuleID] = ???
  lazy val plugins : Seq[Plugins] = Seq()

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin) ++ plugins : _*)
    .settings(publishingSettings: _*)
    .settings(
      libraryDependencies ++= appDependencies,
      retrieveManaged := true,
      forceSourceHeader := true,
      routesGenerator := StaticRoutesGenerator)
    .settings(resolvers ++= Seq(Resolver.bintrayRepo("hmrc", "releases"), Resolver.jcenterRepo))
}
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

object MicroServiceBuild extends Build with MicroService {

  override val appName = "catalogue-frontend"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.PlayImport._
  import play.core.PlayVersion

  private val hmrcTestVersion = "1.4.0"

  val compile = Seq(

    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "6.1.0",
    "uk.gov.hmrc" %% "play-config" % "2.0.1",
    "uk.gov.hmrc" %% "play-json-logger" % "2.1.1",
    "uk.gov.hmrc" %% "play-health" % "1.1.0",
    "uk.gov.hmrc" %% "govuk-template" % "4.0.0",
    "uk.gov.hmrc" %% "play-ui" % "4.2.0",
    "uk.gov.hmrc" %% "url-builder" % "1.0.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.2" % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,        
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
      )
    }.test
  }


  def apply() = compile ++ Test()
}


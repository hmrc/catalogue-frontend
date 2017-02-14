import sbt._

object MicroServiceBuild extends Build with MicroService {
  override val appName = "catalogue-frontend"
  override lazy val plugins: Seq[Plugins] = Seq()
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.core.PlayVersion
  import play.sbt.PlayImport._


  private val frontendBootstrapVersion = "7.11.0"
  private val playConfigVersion = "3.0.0"
  private val logbackJsonLoggerVersion = "3.0.0"
  private val playJsonLoggerVersion = "3.0.0"
  private val playHealthVersion = "2.0.0"
  private val govTemplateVersion = "5.0.0"
  private val playUIVersion = "5.0.0"
  private val urlBuilderVersion = "1.1.0"

  private val hmrcTestVersion = "2.0.0"


  val compile = Seq(
//    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % frontendBootstrapVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "play-json-logger" % playJsonLoggerVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "govuk-template" % govTemplateVersion,
    "uk.gov.hmrc" %% "play-ui" % playUIVersion,
    "uk.gov.hmrc" %% "url-builder" % urlBuilderVersion,
    "org.apache.httpcomponents" % "httpcore" % "4.3.2",
    "org.apache.httpcomponents" % "httpclient" % "4.3.5"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0" % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,        
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "com.github.tomakehurst" % "wiremock" % "1.52" % scope,
        "org.jsoup" % "jsoup" % "1.9.2" % scope,
        "org.mockito" % "mockito-all" % "1.10.19" % scope


      )
    }.test
  }

  def apply() = compile ++ Test()
}


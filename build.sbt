import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.versioning.SbtGitVersioning

lazy val microservice = Project("catalogue-frontend", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion := 5,
    scalaVersion := "3.3.3",
    scalacOptions += "-Ywarn-macros:after",
    playDefaultPort := 9017,
    libraryDependencies ++= compile ++ test,
    // ***************
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.model._",
      "uk.gov.hmrc.cataloguefrontend.platforminitiatives.DisplayType",
      "uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType",
      "uk.gov.hmrc.cataloguefrontend.binders.Binders._",
      "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl",
      "uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.PrototypeStatus"
    ),
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.model._",
      "uk.gov.hmrc.cataloguefrontend.util.ViewHelper.csrfFormField",
      "views.html.helper.CSPNonce",
      "uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches"
    ),
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress unused-imports in twirl and routes files
    scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s",
    scalacOptions += "-Wconf:src=routes/.*:s",
    //scalacOptions += "-explain",
    javaOptions += "-Xmx2G",
    pipelineStages := Seq(digest)
  )

val bootstrapPlayVersion = "9.0.0"
val hmrcMongoVersion     = "2.0.0"

val compile = Seq(
  ehcache,
  "uk.gov.hmrc"               %% "bootstrap-frontend-play-30"   % bootstrapPlayVersion,
  "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-30"           % hmrcMongoVersion,
  "uk.gov.hmrc"               %% "internal-auth-client-play-30" % "3.0.0",
  "org.typelevel"             %% "cats-core"                    % "2.10.0",
  "org.yaml"                  %  "snakeyaml"                    % "2.2",
  "org.apache.httpcomponents" %  "httpclient"                   % "4.5.14",
  "org.planet42"              %% "laika-core"                   % "0.19.5",
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapPlayVersion % Test,
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30"  % hmrcMongoVersion     % Test,
  "org.scalatestplus"      %% "scalacheck-1-17"          % "3.2.17.0"           % Test,
  "org.scalatestplus"      %% "mockito-3-4"              % "3.2.10.0"           % Test,
  ws
)

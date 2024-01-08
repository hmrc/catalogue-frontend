import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.versioning.SbtGitVersioning

lazy val microservice = Project("catalogue-frontend", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion := 4,
    scalaVersion := "2.13.12",
    scalacOptions += "-Ywarn-macros:after",
    playDefaultPort := 9017,
    libraryDependencies ++= compile ++ test,
    // ***************
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.connector.model.TeamName",
      "uk.gov.hmrc.cataloguefrontend.model.Environment",
      "uk.gov.hmrc.cataloguefrontend.platforminitiatives.DisplayType",
      "uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType",
      "uk.gov.hmrc.cataloguefrontend.binders.Binders._",
      "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl",
      "uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.PrototypeStatus"
    ),
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.util.ViewHelper.csrfFormField",
      "views.html.helper.CSPNonce",
      "uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches"
    ),
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress unused-imports in twirl and routes files
    scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s",
    scalacOptions += "-Wconf:src=routes/.*:s",
    pipelineStages := Seq(digest)
  )

val bootstrapPlayVersion = "8.4.0"
val hmrcMongoVersion     = "1.7.0"

val compile = Seq(
  ehcache,
  "uk.gov.hmrc"               %% "bootstrap-frontend-play-30"   % bootstrapPlayVersion,
  "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-30"           % hmrcMongoVersion,
  "uk.gov.hmrc"               %% "internal-auth-client-play-30" % "1.8.0",
  "org.typelevel"             %% "cats-core"                    % "2.10.0",
  "org.yaml"                  %  "snakeyaml"                    % "2.2",
  "org.apache.httpcomponents" %  "httpclient"                   % "4.5.14",
  "org.planet42"              %% "laika-core"                   % "0.19.5",
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapPlayVersion % Test,
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30"  % hmrcMongoVersion     % Test,
  "org.scalatestplus"      %% "scalacheck-1-17"          % "3.2.17.0"           % Test,
  "org.mockito"            %% "mockito-scala-scalatest"  % "1.17.14"            % Test,
  ws
)

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)

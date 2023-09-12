import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.versioning.SbtGitVersioning

lazy val microservice = Project("catalogue-frontend", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion := 4,
    scalaVersion := "2.13.10",
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
      "views.html.helper.CSPNonce"
    ),
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress unused-imports in twirl and routes files
    scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s",
    scalacOptions += "-Wconf:src=routes/.*:s",
    pipelineStages := Seq(digest)
  )

val bootstrapPlayVersion = "7.22.0"
val hmrcMongoVersion     = "1.3.0"

val compile = Seq(
  ehcache,
  "uk.gov.hmrc"               %% "bootstrap-frontend-play-28"   % bootstrapPlayVersion,
  "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-28"           % hmrcMongoVersion,
  "uk.gov.hmrc"               %% "internal-auth-client-play-28" % "1.6.0",
  "org.typelevel"             %% "cats-core"                    % "2.6.1",
  "org.yaml"                  %  "snakeyaml"                    % "1.27",
  "org.apache.httpcomponents" %  "httpclient"                   % "4.5.13",
  "com.github.tototoshi"      %% "scala-csv"                    % "1.3.6",
  "org.planet42"              %% "laika-core"                   % "0.15.0",
  "software.amazon.awssdk"    %  "auth"                         % "2.18.21"
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapPlayVersion % Test,
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"  % hmrcMongoVersion     % Test,
  "org.scalatestplus"      %% "scalatestplus-scalacheck" % "3.1.0.0-RC2"        % Test,
  "org.jsoup"              %  "jsoup"                    % "1.13.1"             % Test,
  "org.mockito"            %% "mockito-scala-scalatest"  % "1.16.46"            % Test,
  ws
)

// tests using software.amazon.awssdk.core (e.g. PrototypePageSpec)
Test / javaOptions := Seq("-Daws.accessKeyId=1111", "-Daws.secretAccessKey=2222")

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)

import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning

val silencerVersion = "1.7.5"

lazy val microservice = Project("catalogue-frontend", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(publishingSettings: _*)
  .settings(
    majorVersion := 4,
    scalaVersion := "2.12.14",
    scalacOptions += "-Ywarn-macros:after",
    playDefaultPort := 9017,
    libraryDependencies ++= compile ++ test,
    // ***************
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.connector.model.TeamName",
      "uk.gov.hmrc.cataloguefrontend.model.Environment",
      "uk.gov.hmrc.cataloguefrontend.platforminitiatives.DisplayType",
      "uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType",
      "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl"
    ),
    // ***************
    // Use the silencer plugin to suppress warnings from unused imports in compiled twirl templates
    scalacOptions += "-P:silencer:pathFilters=views;routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    // ***************
    pipelineStages := Seq(digest)
  )

val bootstrapPlayVersion = "5.14.0"
val hmrcMongoVersion     = "0.54.0"

val compile = Seq(
  "uk.gov.hmrc"               %% "bootstrap-frontend-play-28"   % bootstrapPlayVersion,
  "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-28"           % hmrcMongoVersion,
  "uk.gov.hmrc"               %% "internal-auth-client-play-28" % "0.14.0",
  "org.typelevel"             %% "cats-core"                    % "2.6.1",
  "org.apache.httpcomponents" %  "httpcore"                     % "4.3.3",
  "org.yaml"                  %  "snakeyaml"                    % "1.27",
  "org.apache.httpcomponents" %  "httpclient"                   % "4.3.6",
  "com.github.tototoshi"      %% "scala-csv"                    % "1.3.6",
  "com.github.melrief"        %% "purecsv"                      % "0.1.1",
  "com.opencsv"               %  "opencsv"                      % "4.0",
  "org.planet42"              %% "laika-core"                   % "0.15.0"
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapPlayVersion % Test,
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"  % hmrcMongoVersion     % Test,
  "org.scalatestplus"      %% "scalatestplus-scalacheck" % "3.1.0.0-RC2"        % Test,
  "org.jsoup"              %  "jsoup"                    % "1.13.1"             % Test,
  "org.mockito"            %% "mockito-scala-scalatest"  % "1.16.46"            % Test,
  ws
)

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

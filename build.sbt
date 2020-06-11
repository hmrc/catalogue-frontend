import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName: String = "catalogue-frontend"
val silencerVersion = "1.4.4"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(publishingSettings: _*)
  .settings(
    majorVersion := 4,
    scalaVersion := "2.12.10",
    scalacOptions += "-Ywarn-macros:after",
    playDefaultPort := 9017,
    libraryDependencies ++= compile ++ test,
    // ***************
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    resolvers += Resolver.jcenterRepo,
    RoutesKeys.routesImport ++= Seq(
        "uk.gov.hmrc.cataloguefrontend.connector.model.TeamName",
        "uk.gov.hmrc.cataloguefrontend.model.Environment",
        "uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType"
    ),
    // ***************
    // Use the silencer plugin to suppress warnings from unused imports in compiled twirl templates
    scalacOptions += "-P:silencer:pathFilters=views;routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
    // ***************
  )

val bootstrapPlayVersion = "2.15.0"
val hmrcMongoVersion     = "0.29.0"

val compile = Seq(
  "uk.gov.hmrc"               %% "bootstrap-frontend-play-27" % bootstrapPlayVersion,
  "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-27"         % hmrcMongoVersion,
  "org.typelevel"             %% "cats-core"                  % "2.1.1",
  "org.apache.httpcomponents" %  "httpcore"                   % "4.3.3",
  "org.yaml"                  %  "snakeyaml"                  % "1.25",
  "org.apache.httpcomponents" %  "httpclient"                 % "4.3.6",
  "com.github.tototoshi"      %% "scala-csv"                  % "1.3.6",
  "com.github.melrief"        %% "purecsv"                    % "0.1.1",
  "com.opencsv"               %  "opencsv"                    % "4.0"
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-test-play-27"   % bootstrapPlayVersion % Test,
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-27"  % hmrcMongoVersion     % Test,
  "org.scalatest"          %% "scalatest"                % "3.1.2"              % Test,
  "org.scalatestplus.play" %% "scalatestplus-play"       % "3.1.3"              % Test,
  "org.scalacheck"         %% "scalacheck"               % "1.14.3"             % Test,
  "org.scalatestplus"      %% "scalatestplus-scalacheck" % "3.1.0.0-RC2"        % Test,
  "com.vladsch.flexmark"   %  "flexmark-all"             % "0.35.10"            % Test,
  "com.typesafe.play"      %% "play-test"                % PlayVersion.current  % Test,
  "com.github.tomakehurst" %  "wiremock"                 % "1.58"               % Test,
  "org.jsoup"              %  "jsoup"                    % "1.13.1"              % Test,
  "org.mockito"            %% "mockito-scala"            % "1.10.6"             % Test,
  ws
)

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

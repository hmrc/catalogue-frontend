import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName: String = "catalogue-frontend"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .settings(publishingSettings: _*)
  .settings(
    majorVersion := 4,
    playDefaultPort := 9017,
    libraryDependencies ++= compile ++ test,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    resolvers += Resolver.jcenterRepo
  )

val bootstrapPlayVersion = "0.36.0"

val compile = Seq(
  "uk.gov.hmrc"               %% "simple-reactivemongo" % "7.20.0-play-26",
  "uk.gov.hmrc"               %% "bootstrap-play-26"    % bootstrapPlayVersion,
  "uk.gov.hmrc"               %% "url-builder"          % "1.1.0",
  "org.typelevel"             %% "cats-core"            % "1.6.1",
  "org.apache.httpcomponents" %  "httpcore"             % "4.3.2",
  "org.yaml"                  %  "snakeyaml"            % "1.17",
  "org.apache.httpcomponents" %  "httpclient"           % "4.3.5",
  "com.github.tototoshi"      %% "scala-csv"            % "1.3.4",
  "com.github.melrief"        %% "purecsv"              % "0.1.0",
  "com.opencsv"               %  "opencsv"              % "4.0"
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-play-26"  % bootstrapPlayVersion % Test classifier "tests",
  "uk.gov.hmrc"            %% "hmrctest"           % "3.2.0"              % Test,
  "uk.gov.hmrc"            %% "reactivemongo-test" % "4.6.0-play-26"      % Test,
  "org.scalatest"          %% "scalatest"          % "3.0.5"              % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"              % Test,
  "org.scalacheck"         %% "scalacheck"         % "1.14.0"             % Test,
  "org.pegdown"            %  "pegdown"            % "1.6.0"              % Test,
  "com.typesafe.play"      %% "play-test"          % PlayVersion.current  % Test,
  "com.github.tomakehurst" %  "wiremock"           % "1.55"               % Test,
  "org.jsoup"              %  "jsoup"              % "1.9.2"              % Test,
  "org.mockito"            %  "mockito-all"        % "1.10.19"            % Test,
  // force dependencies due to security flaws found in xercesImpl 2.11.0
  "xerces"                 %  "xercesImpl"         % "2.12.0"             % Test,
  ws
)

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
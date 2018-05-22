import play.core.PlayVersion
import play.sbt.PlayImport.PlayKeys.playDefaultPort
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName: String = "catalogue-frontend"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin): _*)
  .settings(publishingSettings: _*)
  .settings(
    playDefaultPort                               := 9017,
    libraryDependencies                           ++= compile ++ test,
    retrieveManaged                               := true,
    evictionWarningOptions in update              := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    resolvers                                     += Resolver.jcenterRepo
  )


val compile = Seq(
  "uk.gov.hmrc"               %% "bootstrap-play-25"  % "1.5.0",
  "uk.gov.hmrc"               %% "govuk-template"     % "5.11.0",
  "uk.gov.hmrc"               %% "play-ui"            % "7.14.0",
  "uk.gov.hmrc"               %% "url-builder"        % "1.1.0",
  "uk.gov.hmrc"               %% "play-reactivemongo" % "6.2.0",
  "org.typelevel"             %% "cats-core"          % "1.1.0",
  "org.apache.httpcomponents" % "httpcore"            % "4.3.2",
  "org.apache.httpcomponents" % "httpclient"          % "4.3.5",
  "com.github.tototoshi"      %% "scala-csv"          % "1.3.4",
  "com.github.melrief"        %% "purecsv"            % "0.1.0",
  "com.opencsv"               % "opencsv"             % "4.0"
)

val test  = Seq(
  "uk.gov.hmrc"            %% "hmrctest"           % "2.3.0"             % "test",
  "uk.gov.hmrc"            %% "reactivemongo-test" % "2.0.0"             % "test",
  "org.scalatest"          %% "scalatest"          % "2.2.6"             % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0"             % "test",
  "org.scalacheck"         %% "scalacheck"         % "1.12.6"            % "test",
  "org.pegdown"            % "pegdown"             % "1.4.2"             % "test",
  "com.typesafe.play"      %% "play-test"          % PlayVersion.current % "test",
  "com.github.tomakehurst" % "wiremock"            % "1.52"              % "test",
  "org.jsoup"              % "jsoup"               % "1.9.2"             % "test",
  "org.mockito"            % "mockito-all"         % "1.10.19"           % "test"
)

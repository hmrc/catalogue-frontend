import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesKeys

lazy val microservice = Project("catalogue-frontend", file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion := 5,
    scalaVersion := "3.3.6",
    PlayKeys.playDefaultPort := 9017,
    libraryDependencies ++= compile ++ test,
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.model._",
      "uk.gov.hmrc.cataloguefrontend.platforminitiatives.DisplayType",
      "uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType",
      "uk.gov.hmrc.cataloguefrontend.binders.Binders._",
      "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl",
      "uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.PrototypeStatus",
      "uk.gov.hmrc.cataloguefrontend.createrepository.RepoType",
      "uk.gov.hmrc.cataloguefrontend.servicemetrics.LogMetricId",
      "uk.gov.hmrc.cataloguefrontend.vulnerabilities.CurationStatus",
      "uk.gov.hmrc.cataloguefrontend.healthmetrics.HealthMetric"
    ),
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.cataloguefrontend.model._",
      "uk.gov.hmrc.cataloguefrontend.view.ViewHelper.csrfFormField",
      "uk.gov.hmrc.cataloguefrontend.view.helper.html._",
      "uk.gov.hmrc.cataloguefrontend.view.partials.{html => partials}",
      "uk.gov.hmrc.cataloguefrontend.view.html.standard_layout",
      "uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches",
      "views.html.helper.CSPNonce"
    ),
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress unused-imports in twirl and routes files
    scalacOptions += "-Wconf:msg=unused import&src=html/.*:s",
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:msg=Flag.*repeatedly:s",
    //scalacOptions += "-explain",
    Compile / javaOptions += "-Xmx2G", // without `Compile` it breaks sbt start/runProd (Universal scope)
    pipelineStages := Seq(digest)
  )

val bootstrapPlayVersion = "9.18.2-RC2"
val hmrcMongoVersion     = "2.6.0"

val compile = Seq(
  caffeine,
  "uk.gov.hmrc"               %% "bootstrap-frontend-play-30"   % bootstrapPlayVersion,
  "uk.gov.hmrc.mongo"         %% "hmrc-mongo-play-30"           % hmrcMongoVersion,
  "uk.gov.hmrc"               %% "internal-auth-client-play-30" % "4.0.0",
  "org.typelevel"             %% "cats-core"                    % "2.13.0",
  "org.yaml"                  %  "snakeyaml"                    % "2.3",
  "org.planet42"              %% "laika-core"                   % "0.19.5",
  "org.jsoup"                 %  "jsoup"                        % "1.17.2"
)

val test = Seq(
  "uk.gov.hmrc"            %% "bootstrap-test-play-30"   % bootstrapPlayVersion % Test,
  "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30"  % hmrcMongoVersion     % Test,
  "org.scalatestplus"      %% "scalacheck-1-17"          % "3.2.17.0"           % Test,
  ws
)

/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend.deployservice

import cats.data.EitherT

import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ReleasesConnector, WhatsRunningWhereVersion}
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesConnector
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Retrieval, Resource}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.deployservice.{DeployServicePage, DeployServiceStep4Page}
import views.html.error_404_template

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeployServiceController @Inject()(
  override val auth            : FrontendAuthComponents
, override val mcc             : MessagesControllerComponents
, configuration                : Configuration
, buildJobsConnector           : BuildJobsConnector
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, serviceDependenciesConnector : ServiceDependenciesConnector
, serviceCommissioningConnector: ServiceCommissioningStatusConnector
, releasesConnector            : ReleasesConnector
, vulnerabilitiesConnector     : VulnerabilitiesConnector
, serviceConfigsService        : ServiceConfigsService
, deployServicePage            : DeployServicePage
, deployServiceStep4Page       : DeployServiceStep4Page
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders
  with I18nSupport {

  private def predicate(serviceName: String): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"services/$serviceName"), IAAction("DEPLOY_SERVICE"))

  private val failedPredicateMsg = "You do not have permission to deploy this service"

  // Display form options
  def step1(serviceName: Option[String]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(serviceName)
    ).async { implicit request =>
      (for {
        allServices  <- EitherT.right[Result](teamsAndRepositoriesConnector.allServices())
        hasPerm      <- serviceName match {
                          case Some(sn) => EitherT.right[Result](auth.authorised(retrieval = Retrieval.hasPredicate(predicate(sn)), predicate = None))
                          case None     => EitherT.pure[Future, Result](true)
                        }
        form         =  serviceName.fold(DeployServiceForm.form)(x => DeployServiceForm.form.bind(Map("serviceName" -> x)).discardingErrors)
        _            <- EitherT
                          .fromOption[Future](
                            Option.when(hasPerm)(())
                          , Forbidden(deployServicePage(form.withGlobalError(failedPredicateMsg), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                          )
        latest       <- serviceName.fold(EitherT.rightT[Future, Result](Option.empty[Version]))(sn =>
                          EitherT
                            .fromOptionF(
                              serviceDependenciesConnector.getSlugInfo(sn)
                            , BadRequest(deployServicePage(form.withGlobalError("Service not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                            ).map(x => Some(x.version))
                        )
        releases     <- serviceName.fold(EitherT.rightT[Future, Result](Seq.empty[WhatsRunningWhereVersion]))(sn =>
                          EitherT
                            .right[Result](releasesConnector.releasesForService(sn))
                            .map(_.versions.toSeq)
                        )
        _            <- serviceName match {
                          case Some(_) => EitherT.fromOption[Future](releases.headOption, BadRequest(deployServicePage(form.withGlobalError("No versions found"), hasPerm, allServices, None, Nil, Nil, evaluations = None)))
                          case None    => EitherT.right[Result](Future.unit)
                        }
        environments <- serviceName.fold(EitherT.rightT[Future, Result](Seq.empty[Environment]))(sn =>
                          EitherT
                            .fromOptionF(
                              serviceCommissioningConnector.commissioningStatus(sn)
                            , BadRequest(deployServicePage(form.withGlobalError("Service Commissioning Status not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                            ).map(_.collect { case x: Check.EnvCheck if x.title == "App Config Environment" => x.checkResults.filter(_._2.isRight).keys }.flatten )
                             .map(_.sorted)
                        )
        _            <- serviceName match {
                          case Some(_) => EitherT.fromOption[Future](environments.headOption, BadRequest(deployServicePage(form.withGlobalError("App Config Environment not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None)))
                          case None    => EitherT.right[Result](Future.unit)
                        }
      } yield
        Ok(deployServicePage(form, hasPerm, allServices, latest, releases, environments, evaluations = None))
      ).merge
    }

  // Display service info - config warnings, vulnerabilities, etc
  def step2(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None)
    ).async { implicit request =>
      (for {
        allServices  <- EitherT
                          .right[Result](teamsAndRepositoriesConnector.allServices())
        form         =  DeployServiceForm.form.bindFromRequest()
        formObject   <- EitherT
                          .fromEither[Future](form.fold(
                            formWithErrors => Left(BadRequest(deployServicePage(formWithErrors, hasPerm = false, allServices, None, Nil, Nil, evaluations = None)))
                          , validForm      => Right(validForm)
                          ))
        hasPerm      <- EitherT
                          .right[Result](auth.authorised(retrieval = Retrieval.hasPredicate(predicate(formObject.serviceName)), predicate = None))
        _            <- EitherT
                          .fromOption[Future](
                            Option.when(hasPerm)(())
                          , Forbidden(deployServicePage(form.withGlobalError(failedPredicateMsg), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                          )
        _            <- EitherT
                          .right[Result](auth.verify(retrieval = Retrieval.hasPredicate(predicate(formObject.serviceName))))
        latest       <- EitherT
                          .fromOptionF(
                            serviceDependenciesConnector.getSlugInfo(formObject.serviceName)
                          , BadRequest(deployServicePage(form.withGlobalError("Service not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                          ).map(x => Some(x.version))
        releases     <- EitherT
                          .right[Result](releasesConnector.releasesForService(formObject.serviceName))
                          .map(_.versions.toSeq)
        _            <- EitherT.fromOption[Future](releases.headOption, BadRequest(deployServicePage(form.withGlobalError("No releases found"), hasPerm, allServices, None, Nil, Nil, evaluations = None)))
        environments <- EitherT
                          .fromOptionF(
                            serviceCommissioningConnector.commissioningStatus(formObject.serviceName)
                            , BadRequest(deployServicePage(form.withGlobalError("Service Commissioning Status not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                          ).map(_.collect { case x: Check.EnvCheck if x.title == "App Config Environment" => x.checkResults.filter(_._2.isRight).keys }.flatten )
                           .map(_.sorted)
        _            <- EitherT.fromOption[Future](environments.headOption, BadRequest(deployServicePage(form.withGlobalError("App Config Environment not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None)))
        slugInfo     <- EitherT
                          .fromOptionF(
                            serviceDependenciesConnector.getSlugInfo(formObject.serviceName, Some(formObject.version))
                            , BadRequest(deployServicePage(form.withGlobalError("Version not found"), hasPerm, allServices, latest, releases, environments, evaluations = None))
                          )
        confUpdates  <- EitherT
                          .right[Result](serviceConfigsService.configByKeyWithNextDeployment(formObject.serviceName, Seq(formObject.environment), Some(formObject.version)))
                          .map(_.flatMap { case (k, envData) =>
                            envData
                              .getOrElse(ServiceConfigsService.ConfigEnvironment.ForEnvironment(formObject.environment), Nil)
                              .collect { case (x, true) if !x.isReferenceConf => x }
                              .map(v => (k -> v))
                          })
        confWarnings <- EitherT
                          .right[Result](serviceConfigsService.configWarnings(ServiceConfigsService.ServiceName(formObject.serviceName), Seq(formObject.environment), Some(formObject.version), latest = true))
        vulnerabils  <- EitherT
                          .right[Result](vulnerabilitiesConnector.vulnerabilitySummaries(service = Some(formObject.serviceName), version = Some(formObject.version)))
      } yield
        Ok(deployServicePage(form, hasPerm, allServices, latest, releases, environments, Some((confUpdates, confWarnings, vulnerabils))))
      ).merge
    }

  // Deploy service and redirects (to avoid redeploying on refresh)
  def step3(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None)
    ).async { implicit request =>
      (for {
        allServices  <- EitherT.right[Result](teamsAndRepositoriesConnector.allServices())
        form         =  DeployServiceForm.form.bindFromRequest()
        formObject   <- EitherT
                          .fromEither[Future](form.fold(
                            formWithErrors => Left(BadRequest(deployServicePage(formWithErrors, hasPerm = false, allServices, None, Nil, Nil, evaluations = None)))
                          , validForm      => Right(validForm)
                          ))
        hasPerm      <- EitherT
                          .right[Result](auth.authorised(retrieval = Retrieval.hasPredicate(predicate(formObject.serviceName)), predicate = None))
        _            <- EitherT
                          .fromOption[Future](
                            Option.when(hasPerm)(())
                          , Forbidden(deployServicePage(form.withGlobalError(failedPredicateMsg), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                          )
        slugInfo     <- EitherT
                          .fromOptionF(
                            serviceDependenciesConnector.getSlugInfo(formObject.serviceName, Some(formObject.version))
                          , BadRequest(deployServicePage(form.withGlobalError("Service not found"), hasPerm, allServices, None, Nil, Nil, evaluations = None))
                          )
        queueUrl      <- EitherT
                          .right[Result](buildJobsConnector.deployMicroservice(
                            serviceName = formObject.serviceName
                          , version     = formObject.version
                          , environment = formObject.environment
                          ))
      } yield
        Redirect(routes.DeployServiceController.step4(
          serviceName = formObject.serviceName
        , version     = formObject.version.original
        , environment = formObject.environment.asString
        , queueUrl    = queueUrl
        ))
      ).merge
    }

  private val telemetryLogsLinkTemplate    = configuration.get[String]("telemetry.templates.logs")
  private val telemetryMetricsLinkTemplate = configuration.get[String]("telemetry.templates.metrics")

  // Display progress and useful links
  def step4(serviceName: String, version: String, environment: String, queueUrl: String, buildUrl: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      DeployServiceForm
        .form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(NotFound(error_404_template()))
        , formObject     => serviceDependenciesConnector
                              .getSlugInfo(formObject.serviceName, Some(formObject.version))
                              .map(_.fold(NotFound(error_404_template())){_ =>
                                val grafanaLink = TelemetryLinks.create("Grafana Dashboard", telemetryMetricsLinkTemplate, formObject.environment, formObject.serviceName)
                                val kibanaLink  = TelemetryLinks.create("Kibana Dashboard", telemetryLogsLinkTemplate, formObject.environment, formObject.serviceName)
                                Ok(deployServiceStep4Page(formObject, queueUrl, buildUrl, grafanaLink, kibanaLink))
                              })
        )
    }

  import scala.concurrent.duration._
  import akka.stream.scaladsl.Source
  import play.api.http.ContentTypes
  import play.api.libs.EventSource
  import play.api.libs.json.Json
  def step4sse(queueUrl: String, buildUrl: Option[String] ): Action[AnyContent] =
    BasicAuthAction.apply { implicit request =>
      implicit val w1 = BuildJobsConnector.QueueStatus.format
      implicit val w2 = BuildJobsConnector.BuildStatus.format
      val flow =
        Source
          .tick(1.millis, if (buildUrl.isEmpty) 1.second else 10.second, "TICK")
          .mapAsync(parallelism = 1) { _ =>
            (queueUrl, buildUrl) match {
              case (u, None)    => buildJobsConnector.queueStatus(u).map(x => Json.obj("queueStatus" -> Json.toJson(x)).toString)
              case (_, Some(u)) => buildJobsConnector.buildStatus(u).map(x => Json.obj("buildStatus" -> Json.toJson(x)).toString)
            }
          }
          .via(EventSource.flow)

      Ok.chunked(flow)
        .as(ContentTypes.EVENT_STREAM)
    }
}

case class DeployServiceForm(
  serviceName: String
, version    : Version
, environment: Environment
)

import play.api.data.{Form, Forms}
object DeployServiceForm {
  val form: Form[DeployServiceForm] = Form(
    Forms.mapping(
      "serviceName" -> Forms.text
    , "version"     -> Forms.of[Version](Version.formFormat)
    , "environment" -> Forms.of[Environment](Environment.formFormat)
    )(DeployServiceForm.apply)(DeployServiceForm.unapply)
  )
}

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

package uk.gov.hmrc.cataloguefrontend.deployments

import cats.data.EitherT
import play.api.i18n.I18nSupport
import play.api.libs.json.Writes
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result}
import play.api.{Logging, Configuration}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubProxyConnector, RepoType, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.deployments.view.html.{DeployServicePage, DeployServiceStep4Page}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.{CurationStatus, VulnerabilitiesConnector}
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ReleasesConnector, WhatsRunningWhereVersion}
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.binders.{AbsoluteWithHostnameFromAllowlist, RedirectUrl, RedirectUrlPolicy, SafeRedirectUrl}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class DeployServiceController @Inject()(
  override val auth            : FrontendAuthComponents
, override val mcc             : MessagesControllerComponents
, configuration                : Configuration
, buildJobsConnector           : BuildJobsConnector
, serviceDependenciesConnector : ServiceDependenciesConnector
, serviceCommissioningConnector: ServiceCommissioningStatusConnector
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, releasesConnector            : ReleasesConnector
, vulnerabilitiesConnector     : VulnerabilitiesConnector
, gitHubProxyConnector         : GitHubProxyConnector
, serviceConfigsService        : ServiceConfigsService
, telemetryLinks               : TelemetryLinks
, deployServicePage            : DeployServicePage
, deployServiceStep4Page       : DeployServiceStep4Page
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders
  with I18nSupport
  with Logging:

  private def predicate(serviceName: ServiceName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"services/${serviceName.asString}"), IAAction("DEPLOY_SERVICE"))

  private val failedPredicateMsg = "You do not have permission to deploy this service"

  private val servicesRetrieval = Retrieval.locations(
    resourceType = Some(ResourceType("catalogue-frontend")),
    action       = Some(IAAction("DEPLOY_SERVICE"))
  )

  private def cleanseServices(resources: Set[Resource]): Seq[String] =
    resources.map(_.resourceLocation.value.stripPrefix("services/"))
      .toSeq
      .sorted

  // Display form options
  def step1(serviceName: Option[ServiceName]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(serviceName),
      retrieval   = servicesRetrieval
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      (for
        allServices          <- EitherT.right[Result](teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service), archived = Some(false)))
        accessibleRepos      <- EitherT.pure[Future, Result](cleanseServices(request.retrieval))
        accessibleServices   =  allServices.map(_.name).intersect(accessibleRepos).map(ServiceName.apply)
        hasPerm              <- serviceName match
                                  case Some(sn) => EitherT.right[Result](auth.authorised(retrieval = Retrieval.hasPredicate(predicate(sn)), predicate = None))
                                  case None     => EitherT.pure[Future, Result](true)
        form                 =  serviceName.fold(DeployServiceForm.form): s =>
                                  DeployServiceForm.form.bind(Map("serviceName" -> s.asString)).discardingErrors
        _                    <- EitherT
                                  .fromOption[Future](
                                    Option.when(hasPerm)(())
                                  , Forbidden(deployServicePage(form.withGlobalError(failedPredicateMsg), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                  )
        latest               <- serviceName.fold(EitherT.rightT[Future, Result](Option.empty[Version])): sn =>
                                  EitherT
                                    .fromOptionF(
                                      serviceDependenciesConnector.getSlugInfo(sn)
                                    , BadRequest(deployServicePage(form.withGlobalError("Service not found"), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                    ).map(x => Some(x.version))
        releases             <- serviceName.fold(EitherT.rightT[Future, Result](Seq.empty[WhatsRunningWhereVersion])): sn =>
                                  EitherT
                                    .right[Result](releasesConnector.releasesForService(sn))
                                    .map(_.versions.toSeq)
        environments         <- serviceName.fold(EitherT.rightT[Future, Result](Seq.empty[Environment])): sn =>
                                  EitherT
                                    .right[Result](serviceCommissioningConnector.commissioningStatus(sn))
                                    .map:
                                      _
                                        .collect { case x: Check.EnvCheck if x.title == "App Config Environment" => x.checkResults.filter(_._2.isRight).keys }
                                        .flatten
                                    .map(_.sorted)
        _                    <- serviceName match
                                  case Some(_) => EitherT.fromOption[Future](
                                                    environments.headOption,
                                                    BadRequest(deployServicePage(form.withGlobalError("App Config Environment not found"), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                                  )
                                  case None    => EitherT.right[Result](Future.unit)
       yield
         Ok(deployServicePage(form, hasPerm, accessibleServices, latest, releases, environments, evaluations = None))
      ).merge

  // Display service info - config warnings, vulnerabilities, etc
  def step2(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None)
    , retrieval   = servicesRetrieval
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      (for
         allServices          <- EitherT.right[Result](teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service), archived = Some(false)))
         accessibleRepos      <- EitherT.pure[Future, Result](cleanseServices(request.retrieval))
         accessibleServices   =  allServices.map(_.name).intersect(accessibleRepos).map(ServiceName.apply)
         form                 =  DeployServiceForm.form.bindFromRequest()
         formObject           <- EitherT
                                   .fromEither[Future](form.fold(
                                     formWithErrors => Left(BadRequest(deployServicePage(formWithErrors, hasPerm = false, accessibleServices, None, Nil, Nil, evaluations = None)))
                                   , validForm      => Right(validForm)
                                   ))
         hasPerm              <- EitherT
                                   .right[Result](auth.authorised(retrieval = Retrieval.hasPredicate(predicate(formObject.serviceName)), predicate = None))
         _                    <- EitherT
                                   .fromOption[Future](
                                     Option.when(hasPerm)(())
                                   , Forbidden(deployServicePage(form.withGlobalError(failedPredicateMsg), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                   )
         _                    <- EitherT
                                   .right[Result](auth.verify(retrieval = Retrieval.hasPredicate(predicate(formObject.serviceName))))
         latest               <- EitherT
                                   .fromOptionF(
                                     serviceDependenciesConnector.getSlugInfo(formObject.serviceName)
                                   , BadRequest(deployServicePage(form.withGlobalError("Service not found"), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                   ).map(x => Some(x.version))
         releases             <- EitherT
                                   .right[Result](releasesConnector.releasesForService(formObject.serviceName))
                                   .map(_.versions.toSeq)
         environments         <- EitherT
                                   .right[Result](serviceCommissioningConnector.commissioningStatus(formObject.serviceName))
                                   .map(_.collect { case x: Check.EnvCheck if x.title == "App Config Environment" => x.checkResults.filter(_._2.isRight).keys }.flatten )
                                   .map(_.sorted)
         _                    <- EitherT.fromOption[Future](environments.headOption, BadRequest(deployServicePage(form.withGlobalError("App Config Environment not found"), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None)))
         currentSlug          <- EitherT.right[Result](serviceDependenciesConnector.getSlugInfo(formObject.serviceName, releases.find(_.environment == formObject.environment).map(_.version)))
         slugToDeploy         <- EitherT
                                   .fromOptionF(
                                     serviceDependenciesConnector.getSlugInfo(formObject.serviceName, Some(formObject.version))
                                   , BadRequest(deployServicePage(form.withGlobalError("Version not found. If this is a recent build, it may take a few minutes to be picked up"), hasPerm, accessibleServices, latest, releases, environments, evaluations = None))
                                   )
         gitHubCompare        <- EitherT
                                   .right[Result]:
                                     gitHubProxyConnector
                                       .compare(formObject.serviceName.asString, v1 = currentSlug.fold(Version("0.1.0"))(_.version), v2 = formObject.version)
                                       .recover:
                                         case NonFatal(ex) => logger.error(s"Could not call git compare ${ex.getMessage}", ex); None
         jvmChanges           =  (currentSlug.map(_.java), slugToDeploy.java)
         configChanges        <- EitherT
                                   .right[Result](serviceConfigsService.configChangesNextDeployment(formObject.serviceName, formObject.environment, formObject.version))
         configWarnings       <- EitherT
                                   .right[Result](serviceConfigsService.configWarnings(formObject.serviceName, Seq(formObject.environment), Some(formObject.version), latest = true))
         vulnerabilities      <- EitherT
                                   .right[Result](vulnerabilitiesConnector.vulnerabilitySummaries(serviceQuery = Some(formObject.serviceName.asString), version = Some(formObject.version), curationStatus = Some(CurationStatus.ActionRequired)))
         violations           <- EitherT
                                   .right[Result](serviceDependenciesConnector.getRepositoryModules(formObject.serviceName.asString, formObject.version))
       yield
         Ok(deployServicePage(
           form,
           hasPerm,
           accessibleServices,
           latest,
           releases,
           environments,
           Some((gitHubCompare, jvmChanges, configChanges, configWarnings, vulnerabilities, violations))
         ))
      ).merge

  // Deploy service and redirects (to avoid redeploying on refresh)
  def step3(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.DeployServiceController.step1(None)
    , retrieval   = Retrieval.username ~ servicesRetrieval
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Retrieval.Username ~ Set[Resource]] = request
      val username ~ locations = request.retrieval
      (for
         allServices          <- EitherT.right[Result](teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service), archived = Some(false)))
         accessibleRepos      <- EitherT.pure[Future, Result](cleanseServices(locations))
         accessibleServices   =  allServices.map(_.name).intersect(accessibleRepos).map(ServiceName.apply)
         form                 =  DeployServiceForm.form.bindFromRequest()
         formObject           <- EitherT.fromEither[Future]:
                                   form.fold(
                                     formWithErrors => Left(BadRequest(deployServicePage(formWithErrors, hasPerm = false, accessibleServices, None, Nil, Nil, evaluations = None)))
                                   , validForm      => Right(validForm)
                                   )
         hasPerm              <- EitherT
                                   .right[Result](auth.authorised(retrieval = Retrieval.hasPredicate(predicate(formObject.serviceName)), predicate = None))
         _                    <- EitherT
                                   .fromOption[Future](
                                     Option.when(hasPerm)(())
                                   , Forbidden(deployServicePage(form.withGlobalError(failedPredicateMsg), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                   )
         _                    <- EitherT
                                   .fromOptionF(
                                     serviceDependenciesConnector.getSlugInfo(formObject.serviceName, Some(formObject.version))
                                   , BadRequest(deployServicePage(form.withGlobalError("Service not found"), hasPerm, accessibleServices, None, Nil, Nil, evaluations = None))
                                   )
         queueUrl             <- EitherT.right[Result]:
                                   buildJobsConnector.deployMicroservice(
                                     serviceName = formObject.serviceName
                                   , version     = formObject.version
                                   , environment = formObject.environment
                                   , user        = username
                                   )
       yield
        Redirect(routes.DeployServiceController.step4(
          serviceName = formObject.serviceName
        , version     = formObject.version.original
        , environment = formObject.environment.asString
        , queueUrl    = RedirectUrl(queueUrl)
        ))
      ).merge

  private val redirectUrlPolicy =
    AbsoluteWithHostnameFromAllowlist:
      java.net.URL(configuration.get[String]("jenkins.buildjobs.url")).getHost

  import RedirectUrl._
  // Display progress and useful links
  def step4(
    serviceName: ServiceName,
    version    : String,
    environment: String,
    queueUrl   : RedirectUrl,
    buildUrl   : Option[RedirectUrl]
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given Request[AnyContent] = request
      (for
         qUrl <- EitherT.fromEither[Future](queueUrl.getEither[RedirectUrlPolicy.Id](redirectUrlPolicy))
                        .leftMap(_ => BadRequest("Invalid queueUrl"))
         bUrl <- EitherT.fromEither[Future](buildUrl.fold(Right(Option.empty[SafeRedirectUrl]): Either[String, Option[SafeRedirectUrl]])(_.getEither(redirectUrlPolicy).map(Option.apply)))
                        .leftMap(_ => BadRequest("Invalid buildUrl"))
         res  <- EitherT.right[Result](
                   DeployServiceForm
                     .form
                     .bindFromRequest()
                     .fold(
                       formWithErrors => Future.successful(NotFound(error_404_template()))
                     , formObject     => serviceDependenciesConnector
                                           .getSlugInfo(formObject.serviceName, Some(formObject.version))
                                           .map(_.fold(NotFound(error_404_template())){_ =>
                                             val deploymentLogsLink = telemetryLinks.kibanaDeploymentLogs(formObject.environment, formObject.serviceName)
                                             val grafanaLink        = telemetryLinks.grafanaDashboard(formObject.environment, formObject.serviceName)
                                             val kibanaLink         = telemetryLinks.kibanaDashboard(formObject.environment, formObject.serviceName)
                                             Ok(deployServiceStep4Page(formObject, qUrl, bUrl, deploymentLogsLink, grafanaLink, kibanaLink))
                                           })
                   )
                 )
       yield res
      ).merge

  def step4sse(queueUrl: RedirectUrl, buildUrl: Option[RedirectUrl]): Action[AnyContent] =
    import scala.concurrent.duration._
    import org.apache.pekko.stream.scaladsl.Source
    import play.api.http.ContentTypes
    import play.api.libs.EventSource
    import play.api.libs.json.Json
    BasicAuthAction.async { implicit request =>
     (for
        qUrl <- EitherT.fromEither[Future](queueUrl.getEither[RedirectUrlPolicy.Id](redirectUrlPolicy))
                       .leftMap(_ => BadRequest("Invalid queueUrl"))
        bUrl <- EitherT.fromEither[Future](buildUrl.fold(Right(Option.empty[SafeRedirectUrl]): Either[String, Option[SafeRedirectUrl]])(_.getEither(redirectUrlPolicy).map(Option.apply)))
                       .leftMap(_ => BadRequest("Invalid buildUrl"))
      yield
        given Writes[BuildJobsConnector.QueueStatus] = BuildJobsConnector.QueueStatus.format
        given Writes[BuildJobsConnector.BuildStatus] = BuildJobsConnector.BuildStatus.format
        val flow =
          Source
            .tick(
              1.millis
            , if buildUrl.isEmpty then 1.second else 10.second
            , "TICK"
            )
            .mapAsync(parallelism = 1): _ =>
              (qUrl, bUrl) match
                case (u, None)    => buildJobsConnector.queueStatus(u).map(x => Json.obj("queueStatus" -> Json.toJson(x)).toString)
                case (_, Some(u)) => buildJobsConnector.buildStatus(u).map(x => Json.obj("buildStatus" -> Json.toJson(x)).toString)
            .via(EventSource.flow)

        Ok.chunked(flow)
          .as(ContentTypes.EVENT_STREAM)
     ).merge
    }

end DeployServiceController

case class DeployServiceForm(
  serviceName: ServiceName
, version    : Version
, environment: Environment
)

import play.api.data.{Form, Forms}
object DeployServiceForm {
  val form: Form[DeployServiceForm] =
    Form(
      Forms.mapping(
        "serviceName" -> Forms.of[ServiceName]
      , "version"     -> Forms.of[Version](Version.formFormat)
      , "environment" -> Forms.of[Environment]
      )(DeployServiceForm.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
}

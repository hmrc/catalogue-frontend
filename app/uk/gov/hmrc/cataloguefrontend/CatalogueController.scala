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

package uk.gov.hmrc.cataloguefrontend

import cats.data.{EitherT, OptionT}
import cats.implicits.*
import play.api.data.{Form, Forms}
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.{Configuration, Logger}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.{AuthController, CatalogueAuthBuilders}
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.PrototypeStatus
import uk.gov.hmrc.cataloguefrontend.connector.*
import uk.gov.hmrc.cataloguefrontend.connector.RouteConfigurationConnector.{Route, RouteType}
import uk.gov.hmrc.cataloguefrontend.connector.model.RepositoryModules
import uk.gov.hmrc.cataloguefrontend.cost.{CostEstimateConfig, CostEstimationService, Zone}
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionService
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, SlugInfoFlag, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.prcommenter.PrCommenterConnector
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{ServiceConfigsConnector, ServiceConfigsService}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterState, ShutterType}
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{LifecycleStatus, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ReleasesConnector, WhatsRunningWhereService}
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, Retrieval}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.cataloguefrontend.view.html.{IndexPage, LibraryInfoPage, PrototypeInfoPage, RepositoryInfoPage, ServiceInfoPage, TestRepoInfoPage, error_404_template}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class EnvData(
  version                    : Version,
  repoModules                : Option[RepositoryModules],
  shutterStates              : Seq[ShutterState],
  telemetryLinks             : Seq[Link],
  actionReqVulnerabilityCount: Int
)

@Singleton
class CatalogueController @Inject() (
  config                             : Configuration,
  teamsAndRepositoriesConnector      : TeamsAndRepositoriesConnector,
  serviceConfigsService              : ServiceConfigsService,
  costEstimationService              : CostEstimationService,
  routeRulesService                  : RouteRulesService,
  costEstimateConfig                 : CostEstimateConfig,
  serviceDependenciesConnector       : ServiceDependenciesConnector,
  serviceCommissioningStatusConnector: ServiceCommissioningStatusConnector,
  leakDetectionService               : LeakDetectionService,
  shutterService                     : ShutterService,
  override val mcc                   : MessagesControllerComponents,
  whatsRunningWhereService           : WhatsRunningWhereService,
  prCommenterConnector               : PrCommenterConnector,
  vulnerabilitiesConnector           : VulnerabilitiesConnector,
  confluenceConnector                : ConfluenceConnector,
  buildDeployApiConnector            : BuildDeployApiConnector,
  telemetryLinks                     : TelemetryLinks,
  indexPage                          : IndexPage,
  serviceInfoPage                    : ServiceInfoPage,
  libraryInfoPage                    : LibraryInfoPage,
  prototypeInfoPage                  : PrototypeInfoPage,
  testRepoInfoPage                   : TestRepoInfoPage,
  repositoryInfoPage                 : RepositoryInfoPage,
  serviceMetricsConnector            : ServiceMetricsConnector,
  serviceConfigsConnector            : ServiceConfigsConnector,
  releasesConnector                  : ReleasesConnector,
  routesRulesConnector               : RouteConfigurationConnector,
  override val auth                  : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with I18nSupport:

  private val logger = Logger(getClass)

  private def notFound(using request: Request[?]) =
    NotFound(error_404_template())

  val index: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      confluenceConnector
        .getBlogs()
        .map(blogs => Ok(indexPage(blogs, config.get[String]("confluence.allBlogsUrl"))))
    }

  /** Renders the service page by either the repository name, or the artefact name (if configured).
    * This is where it differs from accessing through the generic `/repositories/name` endpoint, which only
    * considers the name of the repository.
    */
  def service(serviceName: ServiceName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      def buildServicePageFromItsArtefactName(serviceName: ServiceName, hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation): Future[Result] =
        serviceConfigsConnector.repoNameForService(serviceName).flatMap:
          case Some(repoName) => buildServicePageFromRepoName(repoName, hasBranchProtectionAuth).getOrElse(notFound)
          case _              => logger.info(s"Not found repo name for the service: $serviceName")
                                 Future.successful(notFound)

      def buildServicePageFromRepoName(repoName: String, hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation): OptionT[Future, Result] =
        OptionT(teamsAndRepositoriesConnector.repositoryDetails(repoName))
          .semiflatMap:
            case repositoryDetails if repositoryDetails.repoType == RepoType.Service => renderServicePage(serviceName, repositoryDetails, hasBranchProtectionAuth)
            case _                                                                   => Future.successful(notFound)

      for
        hasBranchProtectionAuth <- hasEnableBranchProtectionAuthorisation(serviceName.asString)
        result                  <- buildServicePageFromRepoName(serviceName.asString, hasBranchProtectionAuth)
                                    .getOrElseF(buildServicePageFromItsArtefactName(serviceName, hasBranchProtectionAuth))
      yield result
    }

  private def retrieveZone(serviceName: ServiceName)(using request: Request[?]): Future[Option[Zone]] =
    serviceConfigsService.deploymentConfig(serviceName = Some(serviceName))
      .map: deploymentConfigs =>
        val zones = deploymentConfigs.map(_.zone).distinct
        if zones.size > 1
        then logger.warn(s"Service $serviceName is hosted on different zones: \n${deploymentConfigs.map(dc => s"${dc.environment}: ${dc.zone}").mkString("\n")}")
        zones.headOption

  private def renderServicePage(
    serviceName            : ServiceName,
    repositoryDetails      : GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation,
  )(using
    request : Request[?]
  ): Future[Result] =
    for
      deployments               <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
      repositoryName            =  repositoryDetails.name
      jenkinsJobs               <- teamsAndRepositoriesConnector.lookupLatestJenkinsJobs(repositoryName)
      testRepos                 <- teamsAndRepositoriesConnector.findRelatedTestRepos(repositoryName)
      testJobMap                <- testRepos.foldLeftM[Future, Map[String, Seq[JenkinsJob]]](Map.empty): (xs, r) =>
                                     teamsAndRepositoriesConnector
                                       .lookupLatestJenkinsJobs(r)
                                       .map(x => xs ++ Map(r -> x.filter(_.jobType == BuildJobType.Test)))
      logMetrics                <- serviceMetricsConnector.logMetrics(serviceName)
      envDatas                  <- Environment.values.toSeq
                                     .traverse: env =>
                                       val deployedVersions = deployments.filter(_.environment == env).map(_.version)
                                       // a single environment may have multiple versions during a deployment
                                       // return the lowest
                                       deployedVersions.sorted.headOption match
                                         case Some(version) =>
                                           for
                                             repoModules           <- serviceDependenciesConnector.getRepositoryModules(repositoryName, version)
                                             shutterStates         <- ShutterType.values.toSeq.foldLeftM[Future, Seq[ShutterState]](Seq.empty): (acc, shutterType) =>
                                                                        shutterService
                                                                          .getShutterStates(shutterType, env, Some(serviceName))
                                                                          .map(acc ++ _)
                                             vulnerabilitiesCount <- vulnerabilitiesConnector
                                                                        .vulnerabilityCounts(flag = SlugInfoFlag.ForEnvironment(env), serviceName = Some(serviceName))
                                                                        .map(_.headOption) // should only return one result for defined serviceName
                                             data                  =  EnvData(
                                                                        version                     = version
                                                                      , repoModules                 = repoModules.headOption
                                                                      , shutterStates               = shutterStates
                                                                      , telemetryLinks              = Seq(
                                                                                                        telemetryLinks.grafanaDashboard(env, serviceName)
                                                                                                      , telemetryLinks.kibanaDashboard(env, serviceName)
                                                                                                      ) ++ logMetrics.flatMap(telemetryLinks.kibanaLink(env, _))
                                                                      , actionReqVulnerabilityCount = vulnerabilitiesCount.fold(0)(_.actionRequired)
                                                                      )
                                           yield Some((SlugInfoFlag.ForEnvironment(env): SlugInfoFlag) -> data)
                                         case None =>
                                           Future.successful(None)
                                     .map(_.collect { case Some(v) => v }.toMap)
      latestRepoModules         <- serviceDependenciesConnector.getRepositoryModulesLatestVersion(repositoryName)
      urlIfLeaksFound           <- leakDetectionService.urlIfLeaksFound(repositoryName)
      routes                    <- routesRulesConnector.routes(service = Some(serviceName))
      prodApiRoutes             <- releasesConnector.apiServices(Environment.Production).map: apiService =>
                                     apiService.collect:
                                       case api if api.serviceName == serviceName =>
                                         Route(
                                           serviceName          = api.serviceName,
                                           path                 = api.context,
                                           ruleConfigurationUrl = None,
                                           routeType            = RouteType.ApiContext,
                                           environment          = api.environment
                                         )
      allProdRoutes             =  routes.filter(_.environment == Environment.Production) ++ prodApiRoutes
      inconsistentRoutes        =  routeRulesService.inconsistentRoutes(routes)
      optLatestServiceInfo      <- serviceDependenciesConnector.getSlugInfo(serviceName)
      serviceCostEstimate       <- costEstimationService.estimateServiceCost(serviceName)
      commenterReport           <- prCommenterConnector.report(repositoryName)
      latestVulnerabilitiesCount<- vulnerabilitiesConnector
                                    .vulnerabilityCounts(flag = SlugInfoFlag.Latest, serviceName = Some(serviceName))
                                    .map(_.headOption)
      serviceRelationships      <- serviceConfigsService.serviceRelationships(serviceName)
      zone                      <- retrieveZone(serviceName)
      optLatestData             =  optLatestServiceInfo.map: latestServiceInfo =>
                                     SlugInfoFlag.Latest ->
                                       EnvData(
                                         version                     = latestServiceInfo.version,
                                         repoModules                 = latestRepoModules,
                                         shutterStates               = Seq.empty,
                                         telemetryLinks              = Seq.empty,
                                         actionReqVulnerabilityCount = latestVulnerabilitiesCount.fold(0)(_.actionRequired)
                                       )
      canMarkForDecommissioning <- hasMarkForDecommissioningAuthorisation(repositoryName)
      lifecycle                 <- serviceCommissioningStatusConnector.getLifecycle(serviceName)
      isGuest                   = request.session.get(AuthController.SESSION_USERNAME).exists(_.startsWith("guest-"))
    yield
      Ok(serviceInfoPage(
        serviceName                  = serviceName,
        repositoryDetails            = repositoryDetails.copy(jenkinsJobs = jenkinsJobs, zone = zone),
        serviceCostEstimate          = serviceCostEstimate,
        costEstimateConfig           = costEstimateConfig,
        repositoryCreationDate       = repositoryDetails.createdDate,
        envDatas                     = optLatestData.fold(envDatas)(envDatas + _),
        linkToLeakDetection          = urlIfLeaksFound,
        prodRoutes                   = allProdRoutes,
        inconsistentRoutes           = inconsistentRoutes,
        hasBranchProtectionAuth      = hasBranchProtectionAuth,
        commenterReport              = commenterReport,
        serviceRelationships         = serviceRelationships,
        canMarkForDecommissioning    = canMarkForDecommissioning,
        lifecycle                    = lifecycle,
        testJobMap                   = testJobMap,
        isGuest                      = isGuest
      ))

  def library(name: String): Action[AnyContent] =
    Action(Redirect(routes.CatalogueController.repository(name)))

  def prototype(name: String): Action[AnyContent] =
    Action(Redirect(routes.CatalogueController.repository(name)))

  def repository(name: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        hasBranchProtectionAuth <- hasEnableBranchProtectionAuthorisation(name)
        result                  <- OptionT(teamsAndRepositoriesConnector.repositoryDetails(name))
                                     .foldF(Future.successful(notFound)): repoDetails =>
                                       repoDetails.repoType match
                                         case RepoType.Service   => renderServicePage(ServiceName(repoDetails.name), repoDetails, hasBranchProtectionAuth)
                                         case RepoType.Library   => renderLibrary(repoDetails, hasBranchProtectionAuth)
                                         case RepoType.Prototype => renderPrototype(repoDetails, hasBranchProtectionAuth).map(Ok(_))
                                         case RepoType.Test      => renderTest(repoDetails, hasBranchProtectionAuth)
                                         case RepoType.Other     => renderOther(repoDetails, hasBranchProtectionAuth)
      yield result
    }

  def enableBranchProtection(repoName: String) =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(repoName),
        predicate = EnableBranchProtection.permission(repoName))
      .async { implicit request =>
        teamsAndRepositoriesConnector
          .enableBranchProtection(repoName)
          .map(_ => Redirect(routes.CatalogueController.repository(repoName)))
      }

  private def hasEnableBranchProtectionAuthorisation(repoName: String)(
    using HeaderCarrier
  ): Future[EnableBranchProtection.HasAuthorisation] =
    auth
      .verify(Retrieval.hasPredicate(EnableBranchProtection.permission(repoName)))
      .map(r => EnableBranchProtection.HasAuthorisation(r.getOrElse(false)))

  def markForDecommissioning(serviceName: ServiceName) =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(serviceName.asString),
        predicate   = MarkForDecommissioning.permission(serviceName.asString),
        retrieval   = Retrieval.username
    ).async { implicit request =>
      serviceCommissioningStatusConnector
        .setLifecycleStatus(serviceName, LifecycleStatus.DecommissionInProgress, username = request.retrieval.value)
        .map(_ => Redirect(routes.CatalogueController.repository(serviceName.asString)))
    }

  private def hasMarkForDecommissioningAuthorisation(repoName: String)(
    using HeaderCarrier
  ): Future[MarkForDecommissioning.HasAuthorisation] =
    auth
      .verify(Retrieval.hasPredicate(MarkForDecommissioning.permission(repoName)))
      .map(r => MarkForDecommissioning.HasAuthorisation(r.getOrElse(false)))

  def changePrototypePassword(repoName: String) =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(repoName),
        predicate   = ChangePrototypePassword.permission(repoName),
      ).async { implicit request =>
        (for
          hasBranchProtectionAuth <- EitherT.liftF[Future, Result, EnableBranchProtection.HasAuthorisation](hasEnableBranchProtectionAuthorisation(repoName))
          repoDetails             <- EitherT.fromOptionF[Future, Result, GitRepository](teamsAndRepositoriesConnector.repositoryDetails(repoName), notFound)
          newPassword             <- ChangePrototypePassword
                                        .form()
                                        .bindFromRequest()
                                        .fold[EitherT[Future, Result, ChangePrototypePassword.PrototypePassword]](
                                          formWithErrors => EitherT.left(renderPrototype(repoDetails, hasBranchProtectionAuth, formWithErrors).map(BadRequest(_))),
                                          password => EitherT.rightT(password)
                                        )
          user                    =  request.session.get(AuthController.SESSION_USERNAME).getOrElse("Unknown")
          _                       =  logger.info(s"User $user has triggered a password reset for prototype: $repoName")
          response                <- EitherT(buildDeployApiConnector.changePrototypePassword(repoName, newPassword))
                                        .leftSemiflatMap(errorMsg => renderPrototype(repoDetails, hasBranchProtectionAuth, ChangePrototypePassword.form().withGlobalError(errorMsg) , None).map(BadRequest(_)))
          result                  <- EitherT.liftF[Future, Result, Html](renderPrototype(repoDetails, hasBranchProtectionAuth, ChangePrototypePassword.form() , Some(response)))
        yield Ok(result)
        ).merge
      }

  def setPrototypeStatus(repoName: String, status: PrototypeStatus): Action[AnyContent] =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(repoName),
        predicate   = ChangePrototypePassword.permission(repoName)
      ).async { implicit request =>
      (for
         repoDetails   <- EitherT.fromOptionF[Future, Result, GitRepository](teamsAndRepositoriesConnector.repositoryDetails(repoName), notFound)
         user          =  request.session.get(AuthController.SESSION_USERNAME).getOrElse("Unknown")
         _             =  logger.info(s"Setting prototype $repoName to status ${status.displayString}, triggered by User: $user")
         prototypeName = repoDetails.prototypeName.getOrElse(repoName)
         _             <- EitherT.liftF[Future, Result, Unit]:
                            buildDeployApiConnector.setPrototypeStatus(prototypeName, status)
                              .map:
                                 _
                                   .leftMap(err => logger.warn(s"Failed to set $prototypeName to ${status.displayString}: $err"))
                                   .merge
       yield Redirect(routes.CatalogueController.repository(repoName))
      ).merge
    }

  private def hasChangePrototypePasswordAuthorisation(repoName: String)(
    using HeaderCarrier
  ): Future[ChangePrototypePassword.HasAuthorisation] =
    auth
      .verify(Retrieval.hasPredicate(ChangePrototypePassword.permission(repoName)))
      .map(r => ChangePrototypePassword.HasAuthorisation(r.getOrElse(false)))

  def renderLibrary(
    repoDetails: GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation
  )(using request: Request[?]): Future[Result] =
    ( teamsAndRepositoriesConnector.lookupLatestJenkinsJobs(repoDetails.name),
      serviceDependenciesConnector.getRepositoryModulesAllVersions(repoDetails.name),
      leakDetectionService.urlIfLeaksFound(repoDetails.name),
      prCommenterConnector.report(repoDetails.name)
    ).mapN { ( jenkinsJobs,
               repoModulesAllVersions,
               urlIfLeaksFound,
               commenterReport
             ) =>
      Ok(libraryInfoPage(
        repoDetails.copy(jenkinsJobs = jenkinsJobs),
        repoModulesAllVersions.sorted(Ordering.by((_: RepositoryModules).version).reverse),
        urlIfLeaksFound,
        hasBranchProtectionAuth,
        commenterReport
      ))
    }

  private def renderPrototype(
    repoDetails            : GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation,
    form                   : Form[?]        = ChangePrototypePassword.form(),
    successMessage         : Option[String] = None
  )(using request: Request[?]): Future[Html] =
    for
      urlIfLeaksFound       <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
      commenterReport       <- prCommenterConnector.report(repoDetails.name)
      hasPasswordChangeAuth <- hasChangePrototypePasswordAuthorisation(repoDetails.name)
      prototypeName         =  repoDetails.prototypeName.getOrElse(repoDetails.name)
      prototypeDetails      <- buildDeployApiConnector.getPrototypeDetails(prototypeName)
    yield
      prototypeInfoPage(
        repoDetails,
        urlIfLeaksFound,
        hasBranchProtectionAuth,
        hasPasswordChangeAuth,
        form,
        successMessage,
        commenterReport,
        prototypeDetails
      )

  private def renderTest(
    repoDetails: GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation
  )(using request: Request[?]): Future[Result] =
    for
      jenkinsJobs       <- teamsAndRepositoriesConnector.lookupLatestJenkinsJobs(repoDetails.name)
      repoModules       <- serviceDependenciesConnector.getRepositoryModulesLatestVersion(repoDetails.name)
      urlIfLeaksFound   <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
      commenterReport   <- prCommenterConnector.report(repoDetails.name)
      servicesUnderTest <- teamsAndRepositoriesConnector.findServicesUnderTest(repoDetails.name)
    yield Ok(testRepoInfoPage(
      repoDetails.copy(jenkinsJobs = jenkinsJobs),
      repoModules,
      urlIfLeaksFound,
      hasBranchProtectionAuth,
      commenterReport,
      servicesUnderTest
    ))

  private def renderOther(
    repoDetails            : GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation
  )(using request: Request[?]): Future[Result] =
    ( teamsAndRepositoriesConnector.lookupLatestJenkinsJobs(repoDetails.name),
      serviceDependenciesConnector.getRepositoryModulesLatestVersion(repoDetails.name),
      leakDetectionService.urlIfLeaksFound(repoDetails.name),
      prCommenterConnector.report(repoDetails.name)
    ).mapN { ( jenkinsJobs,
               repoModules,
               urlIfLeaksFound,
               commenterReport
             ) =>
      Ok(
        repositoryInfoPage(
          repoDetails.copy(
            teamNames  = { val (owners, writers) =  repoDetails.teamNames.partition(repoDetails.owningTeams.contains) // TODO should this apply to renderLibrary too?
                           owners.sorted ++ writers.sorted
                         },
            jenkinsJobs = jenkinsJobs
          ),
          repoModules,
          urlIfLeaksFound,
          hasBranchProtectionAuth,
          commenterReport
        )
      )
    }

  def dependencyRepository(group: String, artefact: String, version: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      serviceDependenciesConnector.getRepositoryName(group, artefact, Version(version))
        .map: repoName =>
          Redirect(routes.CatalogueController.repository(repoName.getOrElse(artefact)).copy(fragment = artefact))
    }

end CatalogueController

case class TeamFilter(
  name: Option[String] = None
):
  def isEmpty: Boolean =
    name.isEmpty

object TeamFilter:
  lazy val form = Form(
    Forms.mapping(
      "name" -> Forms.optional(Forms.text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(TeamFilter.apply)(f => Some.apply(f.name))
  )

object EnableBranchProtection:

  case class HasAuthorisation(value: Boolean) extends AnyVal

  def permission(repoName: String): Permission =
    Predicate.Permission(
      Resource.from("catalogue-repository", repoName),
      IAAction("WRITE_BRANCH_PROTECTION")
    )

object MarkForDecommissioning:

  case class HasAuthorisation(value: Boolean) extends AnyVal

  def permission(repoName: String): Permission =
    Predicate.Permission(
      Resource.from("catalogue-frontend", s"services/$repoName"),
      IAAction("DECOMMISSION")
    )

object ChangePrototypePassword:

  case class HasAuthorisation(value: Boolean) extends AnyVal

  case class PrototypePassword(value: String) extends AnyVal:
    override def toString: String = "PrototypePassword(...)"

  def permission(repoName: String): Permission =
    Predicate.Permission(
      Resource.from("catalogue-repository", repoName),
      IAAction("CHANGE_PROTOTYPE_PASSWORD")
    )

  private val passwordConstraint =
    Constraint[String]("constraints.password"): input =>
      if input.matches("^[a-zA-Z0-9_]+$")
      then Valid
      else Invalid("Should only contain uppercase letters, lowercase letters, numbers, underscores")

  def form(): Form[PrototypePassword] =
    Form(
      Forms.mapping(
        "password" -> Forms.nonEmptyText.verifying(passwordConstraint)
      )(PrototypePassword.apply)(f => Some(f.value))
    )

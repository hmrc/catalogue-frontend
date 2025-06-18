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

package uk.gov.hmrc.cataloguefrontend.createrepository

import cats.data.EitherT
import cats.implicits.*
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, RequestHeader, Result}
import play.api.libs.json.{Format, Json, Reads, Writes}
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, GitHubTeam, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.createrepository.view.html._
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateRepositoryController @Inject()(
  override val auth               : FrontendAuthComponents,
  override val mcc                : MessagesControllerComponents,
  mongoComponent                  : MongoComponent,
  servicesConfig                  : ServicesConfig,
  timestampSupport                : TimestampSupport,
  selectRepoTypePage              : SelectRepoTypePage,
  createServicePage               : CreateServicePage,
  createPrototypePage             : CreatePrototypePage,
  createTestPage                  : CreateTestPage,
  createExternalPage              : CreateExternalPage,
  createRepositoryConfirmationPage: CreateRepositoryConfirmationPage,
  buildDeployApiConnector         : BuildDeployApiConnector,
  teamsAndReposConnector          : TeamsAndRepositoriesConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport
    with Logging:

  import CreateRepositoryController._

  val sessionIdKey = "CreateRepositoryController"

  val cacheRepo: SessionCacheRepository = SessionCacheRepository(
    mongoComponent   = mongoComponent,
    collectionName   = "sessions.createRepo",
    ttl              = servicesConfig.getDuration("mongodb.session.expireAfter"),
    timestampSupport = timestampSupport,
    sessionIdKey     = sessionIdKey
  )

  given Format[RepoTypeOut] = repoTypeOutFormats

  private def createRepositoryPermission(teamName: TeamName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("CREATE_REPOSITORY"))

  private val createRepoRetrieval: Retrieval.SimpleRetrieval[Set[Resource]] =
    Retrieval.locations(
      resourceType = Some(ResourceType("catalogue-frontend")),
      action       = Some(IAAction("CREATE_REPOSITORY"))
    )

  // Step One GET
  val createRepoLandingGet: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoLandingGet()
    )(): request =>
      given RequestHeader = request
      Ok(selectRepoTypePage(SelectRepoType.form))

  // Step One POST
  val createRepoLandingPost: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoLandingGet(),
    ).async: request =>
      given Request[AnyContent] = request
      (for
        submittedForm <- EitherT.pure[Future, Result](SelectRepoType.form.bindFromRequest())
        repoType      <- submittedForm.fold[EitherT[Future, Result, RepoType]](
                            formWithErrors => EitherT.leftT(BadRequest(selectRepoTypePage(formWithErrors))),
                            repoType       => EitherT.pure(repoType)
                          )
        pageState     <- EitherT.liftF(putSession(repoTypeKey, RepoTypeOut(repoType)))
       yield Redirect(routes.CreateRepositoryController.createRepoGet()).withSession(request.session + pageState)
      ).merge

  // Step Two GET
  val createRepoGet: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoGet(),
      retrieval   = createRepoRetrieval
    ).async: request =>
      given Request[AnyContent] = request
      (for
        githubTeams       <- EitherT.liftF(teamsAndReposConnector.allTeams())
        userGithubTeams   <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval, githubTeams))
        repoTypeOut       <- getRepoTypeOut()
        result            =  repoTypeOut.repoType match
                               case RepoType.Prototype => Ok(createPrototypePage(CreatePrototype.form, userGithubTeams.filterNot(_ == TeamName("Designers"))))
                               case RepoType.Test      => Ok(createTestPage(CreateTest.form, userGithubTeams))
                               case RepoType.Service   => Ok(createServicePage(CreateService.form, userGithubTeams))
                               case RepoType.External  => Ok(createExternalPage(CreateExternal.form, userGithubTeams))
       yield result
      ).merge

  private def createRepo[T <: CreateRepo](
    bindForm        : Form[T],
    createPage      : (Form[T], Seq[TeamName]) => Html,
    bndApiCreateRepo: T => Future[Either[String, BuildDeployApiConnector.AsyncRequestId]],
  )(using request: AuthenticatedRequest[_, Set[Resource]]): EitherT[Future, Result, Result] =
    for
      repoTypeOut       <- getRepoTypeOut()
      githubTeams       <- EitherT.liftF(teamsAndReposConnector.allTeams())
      userGithubTeams   <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval, githubTeams))
      submittedForm     =  bindForm.bindFromRequest()
      validForm         <- submittedForm.fold[EitherT[Future, Result, T]](
                             formWithErrors => EitherT.leftT(BadRequest(createPage(formWithErrors, userGithubTeams))),
                             validForm      => EitherT.pure(validForm)
                           )
      _                 <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
      id                <- EitherT(bndApiCreateRepo(validForm))
                             .leftMap: error =>
                               logger.info(s"CreateRepository request for ${validForm.repositoryName} failed with message: $error")
                               BadRequest(createPage(submittedForm.withGlobalError(s"Repository creation failed! Error: $error"), userGithubTeams))
      _                 =  logger.info(s"CreateRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
    yield Redirect(routes.CreateRepositoryController.createRepoConfirmation(repoTypeOut.repoType, validForm.repositoryName))

  // Step Two POST
  val createRepoPost: Action[AnyContent] =
    auth.authenticatedAction(
    continueUrl = routes.CreateRepositoryController.createRepoGet(),
    retrieval   = createRepoRetrieval
    ).async: request =>
      given AuthenticatedRequest[_, Set[Resource]] = request
      (for
        repoTypeOut <- getRepoTypeOut()
        result      <- repoTypeOut.repoType match
                         case RepoType.Prototype =>
                           createRepo(
                             bindForm         = CreatePrototype.form,
                             createPage       = (form: Form[CreatePrototype], teams: Seq[TeamName]) => createPrototypePage(form, teams),
                             bndApiCreateRepo = (form: CreatePrototype) => buildDeployApiConnector.createPrototypeRepository(form),
                           )
                         case RepoType.Test =>
                           createRepo(
                             bindForm         = CreateTest.form,
                             createPage       = (form: Form[CreateTest], teams: Seq[TeamName]) => createTestPage(form, teams),
                             bndApiCreateRepo = (form: CreateTest) => buildDeployApiConnector.createTestRepository(form),
                           )
                         case RepoType.Service =>
                           createRepo(
                             bindForm         = CreateService.form,
                             createPage       = (form: Form[CreateService], teams: Seq[TeamName]) => createServicePage(form, teams),
                             bndApiCreateRepo = (form: CreateService) => buildDeployApiConnector.createServiceRepository(form),
                           )
                         case RepoType.External =>
                           createRepo(
                             bindForm         = CreateExternal.form,
                             createPage       = (form: Form[CreateExternal], teams: Seq[TeamName]) => createExternalPage(form, teams),
                             bndApiCreateRepo = (form: CreateExternal) => buildDeployApiConnector.createExternalRepository(form),
                           )
        _           <- EitherT.liftF(deleteFromSession(repoTypeKey)) // stops user accessing page 2 on completion
       yield result
      ).merge

  // Step Three GET
  def createRepoConfirmation(repoType: RepoType, repoName: String): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoConfirmation(repoType, repoName)
    )(): request =>
      given Request[AnyContent] = request
      Ok(createRepositoryConfirmationPage(repoType, repoName))

  private def getFromSession[A: Reads](dataKey: DataKey[A])(using Request[?]): Future[Option[A]] =
    cacheRepo.getFromSession(dataKey)

  private def putSession[A: Writes](dataKey: DataKey[A], data: A)(using Request[?]): Future[(String, String)] =
    cacheRepo.putSession(dataKey, data)

  private def deleteFromSession[T](dataKey: DataKey[T])(implicit request: Request[?]): Future[Unit] =
    cacheRepo.deleteFromSession(dataKey)

  private def getRepoTypeOut()(using Request[?]) =
    EitherT.fromOptionF[Future, Result, RepoTypeOut](
      getFromSession(repoTypeKey),
      Redirect(routes.CreateRepositoryController.createRepoLandingGet())
    )

  private def cleanseUserTeams(resources: Set[Resource], githubTeams: Seq[GitHubTeam]): Seq[TeamName] =
    resources
      .map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .map(TeamName.apply)
      .toSeq
      .filter(ut => githubTeams.exists(_.name == ut))
      .sorted
end CreateRepositoryController

object CreateRepositoryController:
  case class RepoTypeOut(repoType: RepoType)
  val repoTypeKey        = DataKey[RepoTypeOut]("repoType")
  val repoTypeOutFormats = Json.format[RepoTypeOut]
end CreateRepositoryController

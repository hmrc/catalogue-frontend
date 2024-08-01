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
import play.api.Logger
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result}
import play.api.libs.json.{Format, Json, Reads, Writes}
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.AsyncRequestId
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.createrepository.view.html.{CreatePrototypePage, CreateServicePage, CreateTestPage, SelectRepoTypePage, CreateRepositoryConfirmationPage}
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier
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
  createRepositoryConfirmationPage: CreateRepositoryConfirmationPage,
  buildDeployApiConnector         : BuildDeployApiConnector,
  teamsAndReposConnector          : TeamsAndRepositoriesConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport:

  import CreateRepositoryController._

  val sessionIdKey = "CreateRepoWizardController"

  val cacheRepo: SessionCacheRepository = SessionCacheRepository(
    mongoComponent   = mongoComponent,
    collectionName   = "sessions.createRepo",
    ttl              = servicesConfig.getDuration("mongodb.session.expireAfter"),
    timestampSupport = timestampSupport,
    sessionIdKey     = sessionIdKey
  )

  given Format[RepoTypeOut] = repoTypeOutFormats
  given Format[RepoNameOut] = repoNameOutFormats

  private def createRepositoryPermission(teamName: TeamName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("CREATE_REPOSITORY"))

  private val logger = Logger(getClass)

  private val createRepoRetrieval: Retrieval.SimpleRetrieval[Set[Resource]] =
    Retrieval.locations(
      resourceType = Some(ResourceType("catalogue-frontend")),
      action       = Some(IAAction("CREATE_REPOSITORY"))
    )

  // Step One GET
  val createRepoLandingGet: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoLandingGet()
    ) { implicit request =>
      Ok(selectRepoTypePage(SelectRepoType.form))
    }

  // Step One POST
  val createRepoLandingPost: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoLandingGet(),
    ).async { implicit request =>
      (for
        submittedForm <- EitherT.pure[Future, Result](SelectRepoType.form.bindFromRequest())
        repoType      <- submittedForm.fold[EitherT[Future, Result, RepoType]](
                            formWithErrors => EitherT.leftT(BadRequest(selectRepoTypePage(formWithErrors))),
                            repoType       => EitherT.pure(repoType)
                          )
        pageState     <- EitherT.liftF(putSession(repoTypeKey, RepoTypeOut(repoType)))
       yield Redirect(routes.CreateRepositoryController.createRepoGet()).withSession(request.session + pageState)
      ).merge
    }

  // Step Two GET
  val createRepoGet: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createRepoGet(),
      retrieval   = createRepoRetrieval
    ).async { implicit request =>
      (for
        userTeams   <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval))
        repoTypeOut <- getRepoTypeOut()
        result      =  repoTypeOut.repoType match
                         case RepoType.Prototype => Ok(createPrototypePage(CreatePrototype.form, userTeams.filterNot(_ == TeamName("Designers"))))
                         case RepoType.Test      => Ok(createTestPage(CreateTest.form, userTeams))
                         case RepoType.Service   => Ok(createServicePage(CreateService.form, userTeams))
       yield result
      ).merge
    }

  private def createRepoAction[T <: CreateRepo](
    bindForm  : Form[T],
    createPage: (Form[T], Seq[TeamName]) => Html,
    createRepo: T => Future[Either[String, AsyncRequestId]],
  )(implicit request: AuthenticatedRequest[_, Set[Resource]]): Future[Result] =
    (for
      userTeams     <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval))
      submittedForm =  bindForm.bindFromRequest()
      validForm     <- submittedForm.fold[EitherT[Future, Result, T]](
                         formWithErrors => EitherT.leftT(BadRequest(createPage(formWithErrors, userTeams))),
                         validForm      => EitherT.pure(validForm)
                       )
      _             <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
      id            <- EitherT(createRepo(validForm))
                         .leftMap: error =>
                           logger.info(s"CreateRepository request for ${validForm.repositoryName} failed with message: $error")
                           BadRequest(createPage(submittedForm.withGlobalError(s"Repository creation failed! Error: $error"), userTeams))
      _             =  logger.info(s"CreateRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
      pageState     <- EitherT.liftF(putSession(repoNameKey, RepoNameOut(validForm.repositoryName)))
     yield Redirect(routes.CreateRepositoryController.createRepoConfirmation()).withSession(request.session + pageState)
    ).merge

  // Step Two POST
  val createRepoPost: Action[AnyContent] =
    auth.authenticatedAction(
    continueUrl = routes.CreateRepositoryController.createRepoGet(),
    retrieval   = createRepoRetrieval
    ).async { implicit request =>
      (for
        repoTypeOut <- getRepoTypeOut()
        result      <- EitherT(repoTypeOut.repoType match
                         case RepoType.Prototype =>
                           createRepoAction(
                             bindForm      = CreatePrototype.form,
                             createPage    = (form: Form[CreatePrototype], teams: Seq[TeamName]) => createPrototypePage(form, teams),
                             createRepo    = (form: CreatePrototype) => buildDeployApiConnector.createPrototypeRepository(form),
                           ).map(Right(_))
                         case RepoType.Test =>
                           createRepoAction(
                             bindForm   = CreateTest.form,
                             createPage = (form: Form[CreateTest], teams: Seq[TeamName]) => createTestPage(form, teams),
                             createRepo = (form: CreateTest) => buildDeployApiConnector.createTestRepository(form),
                           ).map(Right(_))
                         case RepoType.Service =>
                           createRepoAction(
                             bindForm      = CreateService.form,
                             createPage    = (form: Form[CreateService], teams: Seq[TeamName]) => createServicePage(form, teams),
                             createRepo    = (form: CreateService) => buildDeployApiConnector.createServiceRepository(form),
                           ).map(Right(_))
                       )
       yield result
      ).merge
  }

  def createRepoConfirmation(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      (for
        repoTypeOut <- getRepoTypeOut()
        repoNameOut <- getRepoNameOut()
       yield Ok(createRepositoryConfirmationPage(repoNameOut.repoName, repoTypeOut.repoType))
      ).valueOr(identity)
    }

  private def getFromSession[A: Reads](dataKey: DataKey[A])(using Request[?]): Future[Option[A]] =
    cacheRepo.getFromSession(dataKey)

  private def putSession[A: Writes](dataKey: DataKey[A], data: A)(using Request[?]): Future[(String, String)] =
    cacheRepo.putSession(dataKey, data)

  private def getRepoTypeOut()(using Request[?]) =
    EitherT.fromOptionF[Future, Result, RepoTypeOut](
      getFromSession(repoTypeKey),
      Redirect(routes.CreateRepositoryController.createRepoLandingGet())
    )

  private def getRepoNameOut()(using Request[?]) =
    EitherT.fromOptionF[Future, Result, RepoNameOut](
      getFromSession(repoNameKey),
      Redirect(routes.CreateRepositoryController.createRepoLandingGet())
    )

  private def cleanseUserTeams(resources: Set[Resource]): Seq[TeamName] =
    resources
      .map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .map(TeamName.apply)
      .toSeq
      .sorted
end CreateRepositoryController

object CreateRepositoryController:

  case class RepoTypeOut(repoType: RepoType)
  val repoTypeKey        = DataKey[RepoTypeOut]("repoType")
  val repoTypeOutFormats = Json.format[RepoTypeOut]

  case class RepoNameOut(repoName: String)
  val repoNameKey        = DataKey[RepoNameOut]("repoName")
  val repoNameOutFormats = Json.format[RepoNameOut]
end CreateRepositoryController

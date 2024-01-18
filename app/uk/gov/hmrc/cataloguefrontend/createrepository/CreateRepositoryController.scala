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
import cats.implicits._
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.createrepository.CreateRepositoryController._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.createrepository.{CreatePrototypeRepositoryPage, CreateServiceRepositoryPage, CreateTestRepositoryPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateRepositoryController @Inject()(
   override val auth        : FrontendAuthComponents,
   override val mcc         : MessagesControllerComponents,
   createRepositoryPage     : CreateServiceRepositoryPage,
   createPrototypePage      : CreatePrototypeRepositoryPage,
   createTestRepositoryPage : CreateTestRepositoryPage,
   buildDeployApiConnector  : BuildDeployApiConnector,
   teamsAndReposConnector   : TeamsAndRepositoriesConnector
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport {

  private val logger = Logger(getClass)

  private def createRepositoryPermission(teamName: TeamName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("CREATE_REPOSITORY"))

  def createServiceRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createServiceRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createRepositoryPage(CreateServiceRepoForm.form, userTeams, CreateServiceRepositoryType.values))
    }
  }

  def createServiceRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createServiceRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      val submittedForm = CreateServiceRepoForm.form.bindFromRequest()

      submittedForm.fold(
        formWithErrors => {
          Future.successful(BadRequest(createRepositoryPage(formWithErrors, userTeams, CreateServiceRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          (for {
            _   <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
            _   <- EitherT(verifyGithubTeamExists(validForm.teamName))
            res <- EitherT(buildDeployApiConnector.createServiceRepository(validForm)).leftMap[CreateRepoError](ApiError)
          } yield res).value map {
            case Right(id) =>
              logger.info(s"CreateServiceRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
              Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
            case Left(ApiError(msg)) =>
              logger.info(s"CreateServiceRepository request for ${validForm.repositoryName} failed with message: $msg")
              BadRequest(createRepositoryPage(submittedForm.withGlobalError(s"Repository creation failed! Error: $msg"), userTeams, CreateServiceRepositoryType.values))
            case Left(ValidationError(msg)) =>
              BadRequest(createRepositoryPage(submittedForm.withError("teamName", msg), userTeams, CreateServiceRepositoryType.values))
          }
        }
      )
    }

  def createPrototypeRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createPrototypeRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createPrototypePage(CreatePrototypeRepoForm.form, userTeams))
    }
  }
  def createPrototypeRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createPrototypeRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      val submittedForm = CreatePrototypeRepoForm.form.bindFromRequest()

      submittedForm.fold(
        formWithErrors => {
          Future.successful(BadRequest(createPrototypePage(formWithErrors, userTeams)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          (for {
            _   <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
            _   <- EitherT(verifyGithubTeamExists(validForm.teamName))
            res <- EitherT(buildDeployApiConnector.createPrototypeRepository(validForm)).leftMap[CreateRepoError](ApiError)
          } yield res).value map {
            case Right(id)    =>
              logger.info(s"CreatePrototypeRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
              Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
            case Left(ApiError(msg)) =>
              logger.info(s"CreatePrototypeRepository request for ${validForm.repositoryName} failed with message: $msg")
              BadRequest(createPrototypePage(submittedForm.withGlobalError(s"Repository creation failed! Error: $msg"), userTeams))
            case Left(ValidationError(msg)) =>
              BadRequest(createPrototypePage(submittedForm.withError("teamName", msg), userTeams))
          }
        }
      )
    }

  def createTestRepositoryLanding(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createTestRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createTestRepositoryPage(CreateTestRepoForm.form, userTeams, CreateTestRepositoryType.values))
    }

  def createTestRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createTestRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      val submittedForm = CreateTestRepoForm.form.bindFromRequest()

      submittedForm.fold(
        formWithErrors => {
          Future.successful(BadRequest(createTestRepositoryPage(formWithErrors, userTeams, CreateTestRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          (for {
            _   <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
            _   <- EitherT(verifyGithubTeamExists(validForm.teamName))
            res <- EitherT(buildDeployApiConnector.createTestRepository(validForm)).leftMap[CreateRepoError](ApiError)
          } yield res).value map {
            case Right(id)    =>
              logger.info(s"CreateTestRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
              Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
            case Left(ApiError(msg)) =>
              logger.info(s"CreateTestRepository request for ${validForm.repositoryName} failed with message: $msg")
              BadRequest(createTestRepositoryPage(submittedForm.withGlobalError(s"Repository creation failed! Error: $msg"), userTeams, CreateTestRepositoryType.values))
            case Left(ValidationError(msg)) =>
              BadRequest(createTestRepositoryPage(submittedForm.withError("teamName", msg), userTeams, CreateTestRepositoryType.values))
          }
        }
      )
    }

  private def verifyGithubTeamExists(
    selectedTeam: TeamName
  )(implicit hc: HeaderCarrier): Future[Either[CreateRepoError, Unit]] =
    teamsAndReposConnector.allTeams().map { gitTeams =>
      if(gitTeams.map(_.name).contains(selectedTeam))
        Right(())
      else
        Left(ValidationError(s"'${selectedTeam.asString}' does not exist as a team on Github."))
    }

  private def cleanseUserTeams(resources: Set[Resource]): Seq[TeamName] =
    resources
      .map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .map(TeamName.apply)
      .toSeq
      .sorted
}

object CreateRepositoryController {
  sealed trait CreateRepoError {
    val message: String
  }
  final case class ValidationError(message: String) extends CreateRepoError
  final case class ApiError(message: String) extends CreateRepoError
}

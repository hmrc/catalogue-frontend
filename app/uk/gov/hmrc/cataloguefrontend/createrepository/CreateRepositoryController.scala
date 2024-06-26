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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.views.html.createrepository.{CreatePrototypeRepositoryConfirmationPage, CreatePrototypeRepositoryPage, CreateServiceRepositoryConfirmationPage, CreateServiceRepositoryPage, CreateTestRepositoryConfirmationPage, CreateTestRepositoryPage}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateRepositoryController @Inject()(
   override val auth                        : FrontendAuthComponents,
   override val mcc                         : MessagesControllerComponents,
   createServiceRepositoryPage              : CreateServiceRepositoryPage,
   createServiceRepositoryConfirmationPage  : CreateServiceRepositoryConfirmationPage,
   createPrototypePage                      : CreatePrototypeRepositoryPage,
   createPrototypeRepositoryConfirmationPage: CreatePrototypeRepositoryConfirmationPage,
   createTestRepositoryPage                 : CreateTestRepositoryPage,
   createTestRepositoryConfirmationPage     : CreateTestRepositoryConfirmationPage,
   buildDeployApiConnector                  : BuildDeployApiConnector,
   teamsAndReposConnector                   : TeamsAndRepositoriesConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport:

  private val logger = Logger(getClass)

  private def createRepositoryPermission(teamName: TeamName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("CREATE_REPOSITORY"))

  val createServiceRepositoryLanding: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createServiceRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createServiceRepositoryPage(CreateServiceRepoForm.form, userTeams, CreateServiceRepositoryType.valuesAsSeq))
    }

  val createServiceRepository: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createServiceRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      (for
         userTeams     <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval))
         submittedForm =  CreateServiceRepoForm.form.bindFromRequest()
         validForm     <- submittedForm.fold[EitherT[Future, Result, CreateServiceRepoForm]](
                            formWithErrors => EitherT.leftT(BadRequest(createServiceRepositoryPage(formWithErrors, userTeams, CreateServiceRepositoryType.valuesAsSeq))),
                            validForm      => EitherT.pure(validForm)
                          )
         _             <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
         _             <- EitherT(verifyGithubTeamExists(validForm.teamName))
                            .leftMap(error => BadRequest(createServiceRepositoryPage(submittedForm.withError("teamName", error), userTeams, CreateServiceRepositoryType.valuesAsSeq)))
         id            <- EitherT(buildDeployApiConnector.createServiceRepository(validForm))
                            .leftMap: error =>
                              logger.info(s"CreateServiceRepository request for ${validForm.repositoryName} failed with message: $error")
                              BadRequest(createServiceRepositoryPage(submittedForm.withGlobalError(s"Repository creation failed! Error: $error"), userTeams, CreateServiceRepositoryType.valuesAsSeq))
         _             =  logger.info(s"CreateServiceRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
       yield Redirect(routes.CreateRepositoryController.createServiceRepositoryConfirmation(ServiceName(validForm.repositoryName)))
      ).merge
    }

  val createPrototypeRepositoryLanding: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createPrototypeRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createPrototypePage(CreatePrototypeRepoForm.form, userTeams))
    }

  val createPrototypeRepository: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createPrototypeRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      (for
         userTeams     <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval))
         submittedForm =  CreatePrototypeRepoForm.form.bindFromRequest()
         validForm     <- submittedForm.fold[EitherT[Future, Result, CreatePrototypeRepoForm]](
                            formWithErrors => EitherT.leftT(BadRequest(createPrototypePage(formWithErrors, userTeams))),
                            validForm      => EitherT.pure(validForm)
                          )
         _             <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
         _             <- EitherT(verifyGithubTeamExists(validForm.teamName))
                            .leftMap(error => BadRequest(createPrototypePage(submittedForm.withError("teamName", error), userTeams)))
         id            <- EitherT(buildDeployApiConnector.createPrototypeRepository(validForm))
                            .leftMap{ error =>
                              logger.info(s"CreatePrototypeRepository request for ${validForm.repositoryName} failed with message: $error")
                              BadRequest(createPrototypePage(submittedForm.withGlobalError(s"Repository creation failed! Error: $error"), userTeams))
                            }
         _             =  logger.info(s"CreatePrototypeRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
       yield Redirect(routes.CreateRepositoryController.createPrototypeRepositoryConfirmation(validForm.repositoryName))
      ).merge
    }

  val createTestRepositoryLanding: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createTestRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createTestRepositoryPage(CreateTestRepoForm.form, userTeams, CreateTestRepositoryType.valuesAsSeq))
    }

  val createTestRepository: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createTestRepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      (for
         userTeams     <- EitherT.pure[Future, Result](cleanseUserTeams(request.retrieval))
         submittedForm =  CreateTestRepoForm.form.bindFromRequest()
         validForm     <- submittedForm.fold[EitherT[Future, Result, CreateServiceRepoForm]](
                            formWithErrors => EitherT.leftT(BadRequest(createTestRepositoryPage(formWithErrors, userTeams, CreateTestRepositoryType.valuesAsSeq))),
                            validForm      => EitherT.pure(validForm)
                          )
         _             <- EitherT.liftF(auth.authorised(Some(createRepositoryPermission(validForm.teamName))))
         _             <- EitherT(verifyGithubTeamExists(validForm.teamName))
                            .leftMap: error =>
                              BadRequest(createTestRepositoryPage(submittedForm.withError("teamName", error), userTeams, CreateTestRepositoryType.valuesAsSeq))
         id            <- EitherT(buildDeployApiConnector.createTestRepository(validForm))
                            .leftMap: error =>
                              logger.info(s"CreateTestRepository request for ${validForm.repositoryName} failed with message: $error")
                              BadRequest(createTestRepositoryPage(submittedForm.withGlobalError(s"Repository creation failed! Error: $error"), userTeams, CreateTestRepositoryType.valuesAsSeq))
         _             =  logger.info(s"CreateTestRepository request for ${validForm.repositoryName} successfully sent. Bnd api request id: $id:")
       yield Redirect(routes.CreateRepositoryController.createTestRepositoryConfirmation(validForm.repositoryName))
      ).merge
    }

  def createTestRepositoryConfirmation(repoName: String): Action[AnyContent] =
    BasicAuthAction { implicit request =>
      Ok(createTestRepositoryConfirmationPage(repoName))
    }

  def createPrototypeRepositoryConfirmation(repoName: String): Action[AnyContent] =
    BasicAuthAction { implicit request =>
      Ok(createPrototypeRepositoryConfirmationPage(repoName))
    }

  def createServiceRepositoryConfirmation(repoName: ServiceName): Action[AnyContent] =
    BasicAuthAction { implicit request =>
      Ok(createServiceRepositoryConfirmationPage(repoName))
    }

  private def verifyGithubTeamExists(
    selectedTeam: TeamName
  )(using  HeaderCarrier): Future[Either[String, Unit]] =
    teamsAndReposConnector.allTeams().map: gitTeams =>
      if gitTeams.map(_.name).contains(selectedTeam)
      then Right(())
      else Left(s"'${selectedTeam.asString}' does not exist as a team on Github.")

  private def cleanseUserTeams(resources: Set[Resource]): Seq[TeamName] =
    resources
      .map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .map(TeamName.apply)
      .toSeq
      .sorted

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

import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.createrepository.{CreatePrototypeRepositoryPage, CreateServiceRepositoryPage, CreateTestRepositoryPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateRepositoryController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createRepositoryPage         : CreateServiceRepositoryPage,
   createPrototypePage          : CreatePrototypeRepositoryPage,
   createTestRepositoryPage     : CreateTestRepositoryPage,
   buildDeployApiConnector      : BuildDeployApiConnector
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport {

  private val logger = Logger(getClass)

  private def createRepositoryPermission(teamName: String): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/$teamName"), IAAction("CREATE_REPOSITORY"))

  def createServiceRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createServiceRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createRepositoryPage(CreateServiceRepoForm.form, userTeams, CreateServiceRepositoryType.values))
    }
  }

  def createServiceRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createServiceRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      CreateServiceRepoForm.form.bindFromRequest().fold(
        formWithErrors => {
          val userTeams = cleanseUserTeams(request.retrieval)
          Future.successful(BadRequest(createRepositoryPage(formWithErrors, userTeams, CreateServiceRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createServiceRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createServiceRepository failed with: $errMsg")
              case Right(id) => logger.info(s"Bnd api request id: $id:")
            }
            Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
          }
        }
      )
    }

  def createPrototypeRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createPrototypeRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createPrototypePage(CreateServiceRepoForm.form, userTeams))
    }
  }
  def createPrototypeRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createPrototypeRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      CreatePrototypeRepoForm.form.bindFromRequest().fold(
        formWithErrors => {
          val userTeams = cleanseUserTeams(request.retrieval)
          Future.successful(BadRequest(createPrototypePage(formWithErrors, userTeams)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createPrototypeRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createPrototypeRepository failed with: $errMsg")
              case Right(id) => logger.info(s"Bnd api request id: $id:")
            }
            Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
          }
        }
      )
    }

  def createTestRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createTestRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createTestRepositoryPage(CreateTestRepoForm.form, userTeams, CreateTestRepositoryType.values))
    }
  }

  def createTestRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateRepositoryController.createTestRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      CreateTestRepoForm.form.bindFromRequest().fold(
        formWithErrors => {
          val userTeams = cleanseUserTeams(request.retrieval)
          Future.successful(BadRequest(createTestRepositoryPage(formWithErrors, userTeams, CreateTestRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createTestRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createTestRepository failed with: $errMsg")
              case Right(id) => logger.info(s"Bnd api request id: $id:")
            }
            Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
          }
        }
      )
    }

  private def cleanseUserTeams(resources: Set[Resource]): Seq[String] =
    resources.map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .toSeq
      .sorted
}
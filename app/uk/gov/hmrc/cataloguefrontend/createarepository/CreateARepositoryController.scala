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

package uk.gov.hmrc.cataloguefrontend.createarepository

import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{CreateAPrototypeRepositoryPage, CreateAServiceRepositoryPage, CreateATestRepositoryPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateARepositoryController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createARepositoryPage        : CreateAServiceRepositoryPage,
   createAPrototypePage         : CreateAPrototypeRepositoryPage,
   createATestRepositoryPage    : CreateATestRepositoryPage,
   buildDeployApiConnector      : BuildDeployApiConnector
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport {

  private val logger = Logger(getClass)

  private def createRepositoryPermission(teamName: String): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/$teamName"), IAAction("CREATE_REPOSITORY"))

  def createAServiceRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createAServiceRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createARepositoryPage(CreateServiceRepoForm.form, userTeams, CreateServiceRepositoryType.values))
    }
  }

  def createAServiceRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createAServiceRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      CreateServiceRepoForm.form.bindFromRequest().fold(
        formWithErrors => {
          val userTeams = cleanseUserTeams(request.retrieval)
          Future.successful(BadRequest(createARepositoryPage(formWithErrors, userTeams, CreateServiceRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createAServiceRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createAServiceRepository failed with: $errMsg")
              case Right(id) => logger.info(s"Bnd api request id: $id:")
            }
            Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
          }
        }
      )
    }

  def createAPrototypeRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createAPrototypeRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createAPrototypePage(CreateServiceRepoForm.form, userTeams))
    }
  }
  def createAPrototypeRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createAPrototypeRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      CreatePrototypeRepoForm.form.bindFromRequest().fold(
        formWithErrors => {
          val userTeams = cleanseUserTeams(request.retrieval)
          Future.successful(BadRequest(createAPrototypePage(formWithErrors, userTeams)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createAPrototypeRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createAPrototypeRepository failed with: $errMsg")
              case Right(id) => logger.info(s"Bnd api request id: $id:")
            }
            Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
          }
        }
      )
    }

  def createATestRepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createATestRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
      val userTeams = cleanseUserTeams(request.retrieval)
      Ok(createATestRepositoryPage(CreateTestRepoForm.form, userTeams, CreateTestRepositoryType.values))
    }
  }

  def createATestRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createATestRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      CreateTestRepoForm.form.bindFromRequest().fold(
        formWithErrors => {
          val userTeams = cleanseUserTeams(request.retrieval)
          Future.successful(BadRequest(createATestRepositoryPage(formWithErrors, userTeams, CreateTestRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createATestRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createATestRepository failed with: $errMsg")
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

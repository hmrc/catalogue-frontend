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
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.I18nSupport
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Writes, __}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector
import uk.gov.hmrc.cataloguefrontend.createarepository.CreateRepoConstraints.mkConstraint
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, ResourceType, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{CreateAPrototypePage, CreateARepositoryPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateARepositoryController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createARepositoryPage        : CreateARepositoryPage,
   createAPrototypePage         : CreateAPrototypePage,
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
      Ok(createARepositoryPage(CreateServiceRepoForm.form, userTeams, CreateRepositoryType.values))
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
          Future.successful(BadRequest(createARepositoryPage(formWithErrors, userTeams, CreateRepositoryType.values)))
        },
        validForm => {
          import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
          for {
            _ <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
            res <- buildDeployApiConnector.createAServiceRepository(validForm)
          } yield {
            res match {
              case Left(errMsg) => logger.info(s"createARepository failed with: $errMsg")
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
      val userTeams = Seq("Platops") // TODO restore cleanseUserTeams(request.retrieval)
      Ok(createAPrototypePage(CreateServiceRepoForm.form, userTeams))
    }
  }
  def createAPrototypeRepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createAPrototypeRepositoryLanding(),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ).async { implicit request =>
      val withSuffixEnsured = CreateRepoConstraints.ensureSuffix(request.body.asFormUrlEncoded.get, "-prototype")
      CreatePrototypeRepoForm.form.bindFromRequest(withSuffixEnsured).fold(
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

  private def cleanseUserTeams(resources: Set[Resource]): Seq[String] =
    resources.map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .toSeq
      .sorted
}

case class CreateServiceRepoForm(
   repositoryName     : String,
   makePrivate        : Boolean,
   teamName           : String,
   repoType           : String
)

object CreateServiceRepoForm {

  implicit val writes: Writes[CreateServiceRepoForm] =
    ( (__ \ "repositoryName").write[String]
    ~ (__ \ "makePrivate"   ).write[Boolean]
    ~ (__ \ "teamName"      ).write[String]
    ~ (__ \ "repoType"      ).write[String]
    )(unlift(CreateServiceRepoForm.unapply))

  val repoTypeValidation           : String => Boolean = str => CreateRepositoryType.parse(str).nonEmpty

  val conflictingFieldsValidation1 : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("backend")  && crf.repositoryName.toLowerCase.contains("frontend"))
  val conflictingFieldsValidation2 : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("frontend")  && crf.repositoryName.toLowerCase.contains("backend"))
  val frontendValidation1          : CreateServiceRepoForm => Boolean = crf => !(crf.repoType.toLowerCase.contains("frontend")  && !crf.repositoryName.toLowerCase.contains("frontend"))
  val frontendValidation2          : CreateServiceRepoForm => Boolean = crf => !(crf.repositoryName.toLowerCase.contains("frontend") && !crf.repoType.toLowerCase.contains("frontend"))


  private val repoTypeConstraint: Constraint[String] = mkConstraint("constraints.repoTypeCheck")(constraint = repoTypeValidation, error = CreateRepositoryType.parsingError)

  private val repoTypeAndNameConstraints = Seq(
    mkConstraint("constraints.conflictingFields1")(constraint = conflictingFieldsValidation1, error = "You have chosen a backend repo type, but have included 'frontend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.conflictingFields2")(constraint = conflictingFieldsValidation2, error = "You have chosen a frontend repo type, but have included 'backend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation1, error = "Repositories with a frontend repo type require 'frontend' to be present in their repo name."),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation2, error = "Repositories with 'frontend' in their repo name require a frontend repo type")
  )

  val form: Form[CreateServiceRepoForm] = Form(
    mapping(
      "repositoryName"      -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(47) :_*),
      "makePrivate"         -> boolean,
      "teamName"            -> nonEmptyText,
      "repoType"            -> nonEmptyText.verifying(repoTypeConstraint),
    )(CreateServiceRepoForm.apply)(CreateServiceRepoForm.unapply)
      .verifying(repoTypeAndNameConstraints :_*)
  )
}

case class CreatePrototypeRepoForm(
  repositoryName     : String,
  password           : String,
  teamName           : String,
  slackChannels      : String)

object CreatePrototypeRepoForm {

  implicit val writes: Writes[CreatePrototypeRepoForm] =
    ( (__ \ "repositoryName"  ).write[String]
      ~ (__ \ "password"      ).write[String]
      ~ (__ \ "teamName"      ).write[String]
      ~ (__ \ "slackChannels" ).write[String]
      )(unlift(CreatePrototypeRepoForm.unapply))

  private val passwordCharacterValidation: String => Boolean = str => str.matches("^[a-zA-Z0-9_]+$")
  private val passwordConstraint = mkConstraint("constraints.passwordCharacterCheck")(constraint = passwordCharacterValidation, error = "Should only contain the following characters uppercase letters, lowercase letters, numbers, underscores")

  val form: Form[CreatePrototypeRepoForm] = Form(
    mapping(
      "repositoryName"      -> nonEmptyText.verifying(CreateRepoConstraints.createRepoNameConstraints(30) :_*),
      "password"            -> nonEmptyText.verifying(passwordConstraint),
      "teamName"            -> nonEmptyText,
      "slackChannels"       -> text,
    )(CreatePrototypeRepoForm.apply)(CreatePrototypeRepoForm.unapply)
  )
}

object CreateRepoConstraints {

  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] = {
    Constraint(constraintName)({ toBeValidated => if (constraint(toBeValidated)) Valid else Invalid(error) })
  }

  def createRepoNameConstraints(length: Int): Seq[Constraint[String]] = {
    val repoNameWhiteSpaceValidation: String => Boolean = str => !str.matches(".*\\s.*")
    val repoNameUnderscoreValidation: String => Boolean = str => !str.contains("_")
    val repoNameLengthValidation: String => Boolean = str => str.length <= length
    val repoNameLowercaseValidation: String => Boolean = str => str.toLowerCase.equals(str)

    Seq(
      mkConstraint("constraints.repoNameWhitespaceCheck")(constraint = repoNameWhiteSpaceValidation, error = "Repository name cannot include whitespace, use hyphens instead"),
      mkConstraint("constraints.repoNameUnderscoreCheck")(constraint = repoNameUnderscoreValidation, error = "Repository name cannot include underscores, use hyphens instead"),
      mkConstraint("constraints.repoNameLengthCheck")(constraint = repoNameLengthValidation, error = s"Repository name can have a maximum of $length characters"),
      mkConstraint("constraints.repoNameCaseCheck")(constraint = repoNameLowercaseValidation, error = "Repository name should only contain lowercase characters")
    )
  }

  def ensureSuffix(data: Map[String, Seq[String]], suffix: String): Map[String, Seq[String]] = {
    data.map { case (key, values) =>
      if (key == "repositoryName") (key, values.map(str => if (str.endsWith(suffix)) str else str + suffix))
      else (key, values)
    }
  }
}


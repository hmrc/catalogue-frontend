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

import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText, optional, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.I18nSupport
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Writes, __}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, UserManagementConnector}
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, ResourceType, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.CreateARepositoryPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateARepositoryController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createARepositoryPage        : CreateARepositoryPage,
   buildDeployApiConnector      : BuildDeployApiConnector
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport {

  def createRepositoryPermission(teamName: String): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/$teamName"), IAAction("CREATE_REPOSITORY"))

  def createARepositoryLanding(): Action[AnyContent] = {
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createARepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) { implicit request =>
        val userTeams = cleanseUserTeams(request.retrieval)
        Ok(createARepositoryPage(CreateRepoForm.form, userTeams, CreateRepositoryType.values))
      }
  }

  def createARepository(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateARepositoryController.createARepositoryLanding(),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_REPOSITORY")))
    ) .async { implicit request =>
    CreateRepoForm.form.bindFromRequest.fold(
      formWithErrors => {
        val userTeams = cleanseUserTeams(request.retrieval)
        Future.successful(BadRequest(createARepositoryPage(formWithErrors, userTeams, CreateRepositoryType.values)))
      },
      validForm      => {
        import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes
        for{
          _             <- auth.authorised(Some(createRepositoryPermission(validForm.teamName)))
          _             <- buildDeployApiConnector.createARepository(validForm)
          } yield Redirect(routes.ServiceCommissioningStatusController.getCommissioningState(validForm.repositoryName))
      }
    )
  }

  private def cleanseUserTeams(resources: Set[Resource]): Seq[String] =
    resources.map(_.resourceLocation.value.stripPrefix("teams/"))
      .filterNot(_.contains("app_group_"))
      .toSeq
      .sorted
}

case class CreateRepoForm(
   repositoryName     : String,
   makePrivate        : Boolean,
   teamName           : String,
   repoType           : String,
)

object CreateRepoForm {

  implicit val writes: Writes[CreateRepoForm] =
    ( (__ \ "repositoryName").write[String]
      ~ (__ \ "makePrivate").write[Boolean]
      ~ (__ \ "teamName").write[String]
      ~ (__ \ "repoType").write[String]
      )(unlift(CreateRepoForm.unapply))

  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] = {
    Constraint(constraintName)({ toBeValidated => if(constraint(toBeValidated)) Valid else Invalid(error) })
  }

  val repoNameWhiteSpaceValidation: (String => Boolean) = str => !str.matches(".*\\s.*")
  val repoNameUnderscoreValidation: (String => Boolean) = str => !str.contains("_")
  val repoNameLengthValidation:     (String => Boolean) = str => str.length < 48
  val repoNameLowercaseValidation:  (String => Boolean) = str => str.toLowerCase.equals(str)

  val repoTypeValidation:           (String => Boolean) = str => CreateRepositoryType.parse(str).nonEmpty

  val conflictingFieldsValidation1: (CreateRepoForm => Boolean) = crf => !(crf.repoType.toLowerCase.contains("backend")  && crf.repositoryName.toLowerCase.contains("frontend"))
  val conflictingFieldsValidation2: (CreateRepoForm => Boolean) = crf => !(crf.repoType.toLowerCase.contains("frontend")  && crf.repositoryName.toLowerCase.contains("backend"))
  val frontendValidation1:           (CreateRepoForm => Boolean) = crf => !(crf.repoType.toLowerCase.contains("frontend")  && !crf.repositoryName.toLowerCase.contains("frontend"))
  val frontendValidation2:           (CreateRepoForm => Boolean) = crf => !(crf.repositoryName.toLowerCase.contains("frontend") && !crf.repoType.toLowerCase.contains("frontend"))

  val repoNameConstraints = Seq(
    mkConstraint("constraints.repoNameWhitespaceCheck")(constraint = repoNameWhiteSpaceValidation, error = "Repository name cannot include whitespace, use hyphens instead"),
    mkConstraint("constraints.repoNameUnderscoreCheck")(constraint = repoNameUnderscoreValidation, error = "Repository name cannot include underscores, use hyphens instead"),
    mkConstraint("constraints.repoNameLengthCheck")(constraint = repoNameLengthValidation, error = s"Repository name can have a maximum of 47 characters"),
    mkConstraint("constraints.repoNameCaseCheck")(constraint = repoNameLowercaseValidation, error = "Repository name should only contain lowercase characters")
  )

  val repoTypeConstraint: Constraint[String] = mkConstraint("constraints.repoTypeCheck")(constraint = repoTypeValidation, error = CreateRepositoryType.parsingError)

  val repoTypeAndNameConstraints = Seq(
    mkConstraint("constraints.conflictingFields1")(constraint = conflictingFieldsValidation1, error = "You have chosen a backend repo type, but have included 'frontend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.conflictingFields2")(constraint = conflictingFieldsValidation2, error = "You have chosen a frontend repo type, but have included 'backend' in your repo name. Change either the repo name or repo type"),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation1, error = "Repositories with a frontend repo type require 'frontend' to be present in their repo name."),
    mkConstraint("constraints.frontendCheck")(constraint = frontendValidation2, error = "Repositories with 'frontend' in their repo name require a frontend repo type")
  )

  val form: Form[CreateRepoForm] = Form(
    mapping(
      "repositoryName"      -> nonEmptyText.verifying(repoNameConstraints :_*),
      "makePrivate"         -> boolean,
      "teamName"            -> nonEmptyText,
      "repoType"            -> nonEmptyText.verifying(repoTypeConstraint),
    )(CreateRepoForm.apply)(CreateRepoForm.unapply)
      .verifying(repoTypeAndNameConstraints :_*)
  )
}



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

import cats.data.EitherT
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText, optional, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, Writes, __}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.cataloguefrontend.auth.{AuthController, CatalogueAuthBuilders}
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, UserManagementConnector}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.CreateARepositoryPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateARepositoryController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createARepositoryPage        : CreateARepositoryPage,
   userManagementConnector      : UserManagementConnector,
   buildDeployApiConnector      : BuildDeployApiConnector
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders {

  private val logger = Logger(getClass)

  def createARepositoryLanding(): Action[AnyContent] = Action.async { implicit request =>
    val username = request.session.data.getOrElse("username", "")
    (for {
      teamDetailsForUser <- EitherT(userManagementConnector.getTeamsForUser(username))
      userTeams           = teamDetailsForUser.map(_.team)
    } yield Ok(
      createARepositoryPage(form, userTeams, CreateRepositoryType.values)
    )).getOrElse(NotFound)
    //To do: confirm whether this is appropriate fallback Result (as it masks the status code returned from teamDetailsForUser)
  }

  def createARepository(): Action[AnyContent] = Action.async { implicit request =>
    val username = request.session.data.getOrElse("username", "")
    form.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(createARepositoryPage(formWithErrors, Seq.empty,  CreateRepositoryType.values)))
      },
      validForm      => {
        for {
          response <- buildDeployApiConnector.createARepository(validForm)
          _         = logger.info(s"Status: ${response.success}, Message: ${response.message}")
        } yield Ok(
          createARepositoryPage(form.fill(validForm), Seq.empty,  CreateRepositoryType.values))} //For now!
    )
  }

  def constraintFactory[T](constraintName: String, constraint: T => Boolean, error: String): Constraint[T] = {
    Constraint(constraintName)({ toBeValidated => if(constraint(toBeValidated)) Valid else Invalid(error) })
  }

  def teamNameValidation(team: String)(implicit : Boolean

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
    constraintFactory(constraintName = "constraints.repoNameWhitespaceCheck", constraint = repoNameWhiteSpaceValidation, error = "Repository name cannot include whitespace, use hyphens instead"),
    constraintFactory(constraintName = "constraints.repoNameUnderscoreCheck", constraint = repoNameUnderscoreValidation, error = "Repository name cannot include underscores, use hyphens instead"),
    constraintFactory(constraintName = "constraints.repoNameLengthCheck",     constraint = repoNameLengthValidation,     error = s"Repository name can have a maximum of 47 characters"),
    constraintFactory(constraintName = "constraints.repoNameCaseCheck",       constraint = repoNameLowercaseValidation,  error = "Repository name should only contain lowercase characters")
  )

  val repoTypeConstraint: Constraint[String] = constraintFactory(constraintName = "constraints.repoTypeCheck", constraint = repoTypeValidation, error = CreateRepositoryType.parsingError)

  val repoTypeAndNameConstraints = Seq(
    constraintFactory(constraintName = "constraints.conflictingFields1", constraint = conflictingFieldsValidation1, error = "You have chosen a backend repo type, but have included 'frontend' in your repo name. Change either the repo name or repo type"),
    constraintFactory(constraintName = "constraints.conflictingFields2", constraint = conflictingFieldsValidation2, error = "You have chosen a frontend repo type, but have included 'backend' in your repo name. Change either the repo name or repo type"),
    constraintFactory(constraintName = "constraints.frontendCheck",      constraint = frontendValidation1,          error = "Repositories with a frontend repo type require 'frontend' to be present in their repo name."),
    constraintFactory(constraintName = "constraints.frontendCheck",      constraint = frontendValidation2,          error = "Repositories with 'frontend' in their repo name require a frontend repo type")
  )

  val form: Form[CreateRepoForm] = Form(
    mapping(
      "repositoryName"      -> nonEmptyText.verifying(repoNameConstraints :_*),
      "makePrivate"         -> boolean,
      "teamName"            -> nonEmptyText,
      "repoType"            -> nonEmptyText.verifying(repoTypeConstraint),
      "bootstrapTag"        -> optional(text)
    )(CreateRepoForm.apply)(CreateRepoForm.unapply)
      .verifying(repoTypeAndNameConstraints :_*)
  )
}

case class CreateRepoForm(
   repositoryName     : String,
   makePrivate        : Boolean,
   teamName           : String,
   repoType           : String,
   bootstrapTag       : Option[String]
)

object CreateRepoForm {

  implicit val writes: Writes[CreateRepoForm] =
    ( (__ \ "repositoryName").write[String]
      ~ (__ \ "makePrivate").write[Boolean]
      ~ (__ \ "teamName").write[String]
      ~ (__ \ "repoType").write[String]
      ~ (__ \ "bootstrapTag").writeNullable[String]
      )(unlift(CreateRepoForm.unapply))

}



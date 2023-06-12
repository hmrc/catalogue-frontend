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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.CreateARepositoryPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CreateARepositoryController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createARepositoryPage        : CreateARepositoryPage
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders {

  def createARepositoryLanding(): Action[AnyContent] = Action { implicit request =>
    //Need to reach out and get all the users ldap teams!
    Ok(createARepositoryPage(CreateRepoForm.form, Seq("team1"), CreateRepositoryType.values))
  }

  def createARepository(): Action[AnyContent] = Action { implicit request =>
    CreateRepoForm.form.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(createARepositoryPage(formWithErrors, Seq.empty,  CreateRepositoryType.values))
      },
      validForm      => {
        Ok(createARepositoryPage(CreateRepoForm.form.fill(validForm), Seq.empty,  CreateRepositoryType.values))} //For now!
    )
  }
}

case class CreateRepoForm(
   repositoryName     : String,
   makePrivate        : Boolean,
   teamName           : String,
   repoType           : String,
   bootstrapTag       : Option[String]
)

object CreateRepoForm {

  def constraintFactory[T](constraintName: String, constraint: T => Boolean, error: String): Constraint[T] = {
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



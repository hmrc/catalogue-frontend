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
        println("ERRORS")
        println(formWithErrors)
        BadRequest(createARepositoryPage(formWithErrors, Seq.empty,  CreateRepositoryType.values))
      },
      validForm      => {
        println("VALID")
        println(validForm)
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
  val repoNameWhiteSpaceConstraint: Constraint[String] =
    Constraint("constraints.repoNameCheck")(str => {
      if (str.matches(".*\\s.*")) Invalid("Repository name cannot include whitespace, use hyphens instead") else Valid
    }
    )

  val repoNameUnderscoreConstraint: Constraint[String] =
    Constraint("constraints.repoNameCheck")({ str =>
      if(str.contains("_")) Invalid("Repository name cannot include underscores, use hyphens instead") else Valid
    })

  val repoNameLengthConstraint: Constraint[String] =
    Constraint("constraints.repoNameCheck")({ str =>
      if(str.length > 47) Invalid("Repository name can have a maximum of 47 characters") else Valid
    })

  val repoNameLowercaseConstraint: Constraint[String] =
    Constraint("constraints.repoNameCheck")({ str =>
      if(str.toLowerCase.equals(str)) Valid else Invalid("Repository name should only contain lowercase characters")
    })

  val repoNameConstraints = Seq(repoNameWhiteSpaceConstraint, repoNameUnderscoreConstraint, repoNameLengthConstraint, repoNameLowercaseConstraint)

  val repoTypeConstraint: Constraint[String] = Constraint("constraints.repoTypeCheck")({ str =>
    CreateRepositoryType.parse(str).map(_ => Valid)
      .getOrElse(Invalid(CreateRepositoryType.parsingError))
  })

  val repoTypeAndRepoNameConstraint: Constraint[CreateRepoForm] = Constraint[CreateRepoForm]("constraints.repoTypeAndRepoName")({ submittedForm =>
    val repoName = submittedForm.repositoryName
    val repoType = submittedForm.repoType

    if      (repoType.contains("Backend")  && repoName.toLowerCase.contains("frontend"))  Invalid("You have chosen a backend repo type, but have included 'frontend' in your repo name. Change either the repo name or repo type")
    else if (repoType.contains("Frontend") && repoName.toLowerCase.contains("backend"))   Invalid("You have chosen a frontend repo type, but have included 'backend' in your repo name. Change either the repo name or repo type")
    else if (repoType.contains("Frontend") && !repoName.toLowerCase.contains("frontend")) Invalid("You have chosen a frontend repo type, but have not 'frontend' in your repo name. Ensure this is added as a suffix to your repo name")
    else Valid
  })

  val form: Form[CreateRepoForm] = Form(
    mapping(
      "repositoryName"      -> nonEmptyText.verifying(repoNameConstraints :_*),
      "makePrivate"         -> boolean,
      "teamName"            -> nonEmptyText,
      "repoType"            -> nonEmptyText.verifying(repoTypeConstraint),
      "bootstrapTag"        -> optional(text)
    )(CreateRepoForm.apply)(CreateRepoForm.unapply)
      .verifying(repoTypeAndRepoNameConstraint)
  )
}



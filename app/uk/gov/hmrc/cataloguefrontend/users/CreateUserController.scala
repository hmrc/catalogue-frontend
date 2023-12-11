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

package uk.gov.hmrc.cataloguefrontend.users

import cats.data.EitherT
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, _}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.users.CreateUserPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateUserController @Inject()(
  override val auth: FrontendAuthComponents,
  override val mcc : MessagesControllerComponents,
  createUserPage   : CreateUserPage,
  configuration    : Configuration,
    teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  userManagementConnector: UserManagementConnector
) (implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders {

  private val logger = Logger(getClass)

  def createUserPermission(userName: String): Predicate = ???

  def createUserLanding(isServiceAccount: Boolean): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        teams <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString.toLowerCase))
      } yield Ok(createUserPage(CreateUserForm.form, teams.map(_.name.asString), Organisation.values, isServiceAccount))
  }

  def createUser(isServiceAccount: Boolean): Action[AnyContent] =
    Action.async { implicit request =>
      (
        for {
          allTeams  <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
          form      <- EitherT.fromEither[Future](CreateUserForm.form.bindFromRequest().fold(
                         formWithErrors => {
                           println(formWithErrors)
                           Left(
                             BadRequest(
                               createUserPage(
                                 form             = formWithErrors,
                                 teamNames        = allTeams.map(_.name.asString),
                                 organisations    = Organisation.values,
                                 isServiceAccount = isServiceAccount
                               )
                             )
                           )
                         },
                         validForm => Right(validForm)
                       ))
          res       <- EitherT(userManagementConnector.createUser(
                         CreateUserRequest(
                           givenName          = form.givenName,
                           familyName         = form.familyName,
                           organisation       = form.organisation,
                           contactEmail       = form.contactEmail,
                           contactComments    = form.contactComments,
                           team               = form.team,
                           isReturningUser    = form.isReturningUser,
                           isTransitoryUser   = form.isTransitoryUser,
                           isServiceAccount   = false,
                           isExistingLDAPUser = false,
                           ldap               = true,
                           vpn                = form.vpn,
                           jira               = form.jira,
                           confluence         = form.confluence,
                           googleApps         = form.googleApps,
                           environments       = form.environments
                         )
                       )).leftMap { errMsg =>
                        logger.info(s"createUser failed with: $errMsg")
                        InternalServerError(
                          createUserPage(
                            form = CreateUserForm.form.bindFromRequest().withGlobalError(errMsg),
                            teamNames = Seq.empty,
                            organisations = Seq.empty,
                            isServiceAccount = isServiceAccount
                          )
                        )
                       }
          _        = logger.info(s"user management result: $res:")

          } yield Ok(res)
      ).merge
    }
}

sealed trait Organisation { def asString: String; }

object Organisation {
  case object Mdtp extends  Organisation { val asString = "MDTP"  }
  case object Voa  extends  Organisation { val asString = "VOA"   }
  case object Other extends Organisation { val asString = "Other" }

  val values: List[Organisation] = List(Mdtp, Voa, Other)
}

//case class CreateUserForm(
//  familyName        : String,
//  givenName         : String,
//  organisation      : String,
//  contactEmail      : String,
//  contactComments   : String,
//  team              : String,
//  platformLocation  : Option[String], //platform_location in payload
//  isReturningUser   : Boolean,
//  //isServiceAccount  : Boolean = false, can be set in payload
//  isTransitoryUser  : Boolean,
//  //isExistingLDAPUser: Boolean = false, should default to false in payload
//  vpn          : Boolean,
//  jira         : Boolean,
//  confluence   : Boolean,
//  environments : Boolean, // developer tools
//  googleApps   : Boolean, // g suite
//  //ldap         : Boolean = true, should default to true in payload
//)

object CreateUserForm {

  val form: Form[CreateUserRequest] = Form(
    mapping(
      "givenName"        -> text.verifying(CreateUserConstraints.nameConstraints("givenName") :_*).verifying(CreateUserConstraints.containsServiceConstraint),
      "familyName"       -> text.verifying(CreateUserConstraints.nameConstraints("familyName") :_*),
      "organisation"     -> nonEmptyText,
      "contactEmail"     -> nonEmptyText.verifying(CreateUserConstraints.emailConstraints() :_*),
      "contactComments"  -> default(text, "").verifying(CreateUserConstraints.commentLengthConstraint),
      "team"             -> nonEmptyText,
      "isReturningUser"  -> boolean,
      "isTransitoryUser" -> boolean,
      "vpn"              -> boolean,
      "jira"            -> boolean,
      "confluence"      -> boolean,
      "environments"    -> boolean,
      "googleApps"      -> boolean
    )(CreateUserRequest.apply)(CreateUserRequest.unapply)
  )
}

object CreateUserConstraints {
  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] = {
    Constraint(constraintName)({ toBeValidated => if (constraint(toBeValidated)) Valid else Invalid(error) })
  }

  def nameConstraints(fieldName: String): Seq[Constraint[String]] = {
    val nameLengthValidation : String => Boolean = str => str.length >= 2 && str.length <= 30
    val whiteSpaceValidation : String => Boolean = str => !str.matches(".*\\s.*")
    val underscoreValidation : String => Boolean = str => !str.contains("_")
    val lowercaseValidation  : String => Boolean = str => str.toLowerCase.equals(str)

    Seq(
      mkConstraint(s"constraints.${fieldName}LengthCheck"    )(constraint = nameLengthValidation,  error = "Should be between 2 and 30 characters long"),
      mkConstraint(s"constraints.${fieldName}WhitespaceCheck")(constraint = whiteSpaceValidation,  error = "Cannot contain whitespace"),
      mkConstraint(s"constraints.${fieldName}UnderscoreCheck")(constraint = underscoreValidation,  error = "Cannot contain underscores"),
      mkConstraint(s"constraints.${fieldName}CaseCheck"      )(constraint = lowercaseValidation,   error = "Should only contain lowercase characters")
    )
  }

  private val containsServiceValidation: String => Boolean = str => !str.matches(".*\\bservice\\b.*")
  val containsServiceConstraint: Constraint[String] =
    mkConstraint("constraints.givenNameNotAServiceCheck")(
      constraint = containsServiceValidation,
      error      = "Should not contain 'service' - please use <a href=\"/create-user?isServiceAccount=true\"'>Create A Service Account</a> instead"
    )

  private val commentLengthValidation: String => Boolean = str => str.length <= 512
  val commentLengthConstraint: Constraint[String] =
    mkConstraint("constraints.commentLengthCheck")(
      constraint = commentLengthValidation,
      error      = "Cannot exceed 512 characters"
    )

  def emailConstraints(): Seq[Constraint[String]] = {
    //val emailValidation       : String => Boolean = str => str.matches("/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}/i")
    val emailValidation: String => Boolean = str => !str.matches("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}$")
    //val digitalEmailValidation: String => Boolean = str => !str.matches("digital.hmrc.gov.uk$")
    val digitalEmailValidation: String => Boolean = str => !str.matches(".*digital\\.hmrc\\.gov\\.uk.*")

    Seq(
      mkConstraint("constraints.emailCheck"       )(constraint = emailValidation,        error = "Invalid email address"),
      mkConstraint("constraints.digitalEmailCheck")(constraint = digitalEmailValidation, error = "Cannot be a digital email such as: digital.hmrc.gov.uk")
    )
  }


}
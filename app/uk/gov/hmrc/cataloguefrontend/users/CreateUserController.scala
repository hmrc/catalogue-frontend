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
import play.api.Logger
import play.api.data.Forms._
import play.api.data.validation.Constraints.maxLength
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.users.{CreateUserPage, CreateUserRequestSentPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateUserController @Inject()(
  override val auth        : FrontendAuthComponents,
  override val mcc         : MessagesControllerComponents,
  createUserPage           : CreateUserPage,
  createUserRequestSentPage: CreateUserRequestSentPage,
  userManagementConnector  : UserManagementConnector
) (implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders
  with play.api.i18n.I18nSupport {

  private val logger = Logger(getClass)

  private def createUserPermission(teamName: TeamName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("CREATE_USER"))

  def requestSent(isServiceAccount: Boolean, givenName: String, familyName: String): Action[AnyContent] = Action { implicit request =>
    Ok(createUserRequestSentPage(isServiceAccount, givenName, familyName))
  }

  def createUserLanding(isServiceAccount: Boolean): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateUserController.createUserLanding(isServiceAccount),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_USER")))
    ).apply { implicit request =>
      Ok(createUserPage(CreateUserForm.form, cleanseUserTeams(request.retrieval), Organisation.values, isServiceAccount))
    }

  def createUser(isServiceAccount: Boolean): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateUserController.createUserLanding(isServiceAccount),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_USER")))
    ).async { implicit request =>
      (
        for {
          form <- EitherT.fromEither[Future](CreateUserForm.form.bindFromRequest().fold(
                    formWithErrors => {
                      Left(
                        BadRequest(
                          createUserPage(
                            form             = formWithErrors,
                            teamNames        = cleanseUserTeams(request.retrieval),
                            organisations    = Organisation.values,
                            isServiceAccount = isServiceAccount
                          )
                        )
                      )
                    },
                    validForm => Right(validForm)
                  ))
          _    <- EitherT.liftF(auth.authorised(Some(createUserPermission(form.team))))
          res  <- EitherT.right[Result](userManagementConnector.createUser(
                  form.copy(isServiceAccount = isServiceAccount)
                  ))
          _    = logger.info(s"user management result: $res:")
        } yield Redirect(uk.gov.hmrc.cataloguefrontend.users.routes.CreateUserController.requestSent(isServiceAccount, form.givenName, form.familyName))
      ).merge
    }

  private def cleanseUserTeams(resources: Set[Resource]): Seq[TeamName] =
    resources.map(_.resourceLocation.value.stripPrefix("teams/"))
      .map(TeamName.apply)
      .toSeq
      .sorted
}

object CreateUserForm {
  val form: Form[CreateUserRequest] = Form(
    mapping(
      "givenName"        -> text.verifying(CreateUserConstraints.nameConstraints("givenName") :_*).verifying(CreateUserConstraints.containsServiceConstraint),
      "familyName"       -> text.verifying(CreateUserConstraints.nameConstraints("familyName") :_*),
      "organisation"     -> nonEmptyText,
      "contactEmail"     -> Forms.email.verifying(CreateUserConstraints.digitalEmailConstraint),
      "contactComments"  -> default(text, "").verifying(maxLength(512)),
      "team"             -> nonEmptyText.transform[TeamName](TeamName.apply, _.asString),
      "isReturningUser"  -> boolean,
      "isTransitoryUser" -> boolean,
      "isServiceAccount" -> boolean,
      "vpn"             -> boolean,
      "jira"            -> boolean,
      "confluence"      -> boolean,
      "googleApps"      -> boolean,
      "environments"    -> boolean
    )(CreateUserRequest.apply)(CreateUserRequest.unapply)
  )
}

object CreateUserConstraints {
  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName){ toBeValidated => if (constraint(toBeValidated)) Valid else Invalid(error) }


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
      error      = "Should not contain 'service' - if you are trying to create a non human user, please use <a href=\"/create-service-user\"'>Create A Service Account</a> instead"
    )

  private val digitalEmailValidation: String => Boolean = str => !str.matches(".*digital\\.hmrc\\.gov\\.uk.*")
  val digitalEmailConstraint: Constraint[String] =
    mkConstraint("constraints.digitalEmailCheck")(
      constraint = digitalEmailValidation,
      error      = "Cannot be a digital email such as: digital.hmrc.gov.uk"
    )
}

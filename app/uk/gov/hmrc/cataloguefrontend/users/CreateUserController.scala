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
import play.api.Logging
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.mvc.*
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.cataloguefrontend.users.view.html.{CreateUserPage, CreateUserRequestSentPage}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateUserController @Inject()(
  override val auth        : FrontendAuthComponents,
  override val mcc         : MessagesControllerComponents,
  createUserPage           : CreateUserPage,
  createUserRequestSentPage: CreateUserRequestSentPage,
  userManagementConnector  : UserManagementConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport
     with Logging:

  private def createUserPermission(teamName: TeamName): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("CREATE_USER"))

  def requestSent(isServiceAccount: Boolean, givenName: String, familyName: String): Action[AnyContent] =
    Action: request =>
      given RequestHeader = request
      Ok(createUserRequestSentPage(isServiceAccount, givenName, familyName))

  def createUserLanding(isServiceAccount: Boolean): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateUserController.createUserLanding(isServiceAccount),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_USER")))
    ).async: request =>
      given RequestHeader = request
      for
        teams <-
                 if request.retrieval.contains(Resource.from("catalogue-frontend", "teams/*")) then
                   userManagementConnector.getAllTeams().map(_.map(_.teamName))
                 else
                   Future.successful(cleanseUserTeams(request.retrieval))
      yield Ok(createUserPage(CreateUserForm.form, teams, Organisation.values.toSeq, isServiceAccount))

  def createUser(isServiceAccount: Boolean): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateUserController.createUserLanding(isServiceAccount),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_USER")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      (for
         teams <- EitherT.liftF:
                    if request.retrieval.contains(Resource.from("catalogue-frontend", "teams/*")) then
                      userManagementConnector.getAllTeams().map(_.map(_.teamName))
                    else
                      Future.successful(cleanseUserTeams(request.retrieval))
         form  <- EitherT.fromEither[Future]:
                    CreateUserForm.form.bindFromRequest()
                      .fold(
                        formWithErrors =>
                          Left(
                            BadRequest(
                              createUserPage(
                                form             = formWithErrors,
                                teamNames        = teams,
                                organisations    = Organisation.values.toSeq,
                                isServiceAccount = isServiceAccount
                              )
                            )
                          ),
                        validForm => Right(validForm)
                      )
         _     <- EitherT.liftF(auth.authorised(Some(createUserPermission(form.team))))
         res   <- EitherT.right[Result]:
                    userManagementConnector.createUser(form.copy(isServiceAccount = isServiceAccount))
         _     =  logger.info(s"user management result: $res:")
       yield Redirect(routes.CreateUserController.requestSent(isServiceAccount, form.givenName, form.familyName))
      ).merge

  private def cleanseUserTeams(resources: Set[Resource]): Seq[TeamName] =
    resources.map(_.resourceLocation.value.stripPrefix("teams/"))
      .map(TeamName.apply)
      .toSeq
      .sorted

end CreateUserController

object CreateUserForm:
  val form: Form[CreateUserRequest] =
    Form(
      Forms.mapping(
        "givenName"        -> Forms.text.verifying(CreateUserConstraints.containsServiceConstraint)
                                        .verifying(CreateUserConstraints.nameConstraints("givenName")*),
        "familyName"       -> Forms.text.verifying(CreateUserConstraints.nameConstraints("familyName")*),
        "organisation"     -> Forms.nonEmptyText,
        "contactEmail"     -> Forms.email.verifying(CreateUserConstraints.digitalEmailConstraint),
        "contactComments"  -> Forms.default(Forms.text, "").verifying(Constraints.maxLength(512)),
        "team"             -> Forms.of[TeamName],
        "isReturningUser"  -> Forms.boolean,
        "isTransitoryUser" -> Forms.boolean,
        "isServiceAccount" -> Forms.boolean,
        "vpn"              -> Forms.boolean,
        "jira"             -> Forms.boolean,
        "confluence"       -> Forms.boolean,
        "googleApps"       -> Forms.boolean,
        "environments"     -> Forms.boolean,
        "bitwarden"        -> Forms.boolean
      )(CreateUserRequest.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object CreateUserConstraints:
  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)


  def nameConstraints(fieldName: String): Seq[Constraint[String]] =
    val nameLengthValidation: String => Boolean = str => str.length >= 2 && str.length <= 30

    Seq(
      mkConstraint(s"constraints.${fieldName}LengthCheck"    )(constraint = nameLengthValidation,  error = "Should be between 2 and 30 characters long")
    )

  private val containsServiceValidation: String => Boolean =
    !_.toLowerCase.matches(".*\\bservice\\b.*")

  val containsServiceConstraint: Constraint[String] =
    mkConstraint("constraints.givenNameNotAServiceCheck")(
      constraint = containsServiceValidation,
      error      = "Should not contain 'service' - if you are trying to create a non human user, please use <a href=\"/create-service-user\"'>Create A Service Account</a> instead"
    )

  private val digitalEmailValidation: String => Boolean =
    !_.matches(".*digital\\.hmrc\\.gov\\.uk.*")

  val digitalEmailConstraint: Constraint[String] =
    mkConstraint("constraints.digitalEmailCheck")(
      constraint = digitalEmailValidation,
      error      = "Cannot be a digital email such as: digital.hmrc.gov.uk"
    )

end CreateUserConstraints

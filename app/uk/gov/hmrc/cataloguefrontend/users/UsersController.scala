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

import play.api.Logging
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.mvc.*
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.view.html.{LdapResetRequestSentPage, UserInfoPage, UserListPage, UserSearchResults, VpnRequestSentPage}
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class UsersController @Inject()(
  userManagementConnector : UserManagementConnector
, userInfoPage            : UserInfoPage
, userListPage            : UserListPage
, userSearchResults       : UserSearchResults
, vpnRequestSentPage      : VpnRequestSentPage
, ldapResetRequestSentPage: LdapResetRequestSentPage
, umpConfig               : UserManagementPortalConfig
, override val mcc        : MessagesControllerComponents
, override val auth       : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport
     with Logging:

  private def showUserInfoPage(
    username       : UserName,
    retrieval      : Option[Set[Resource]],
    resultType     : Html => Result,
    ldapForm       : Form[ResetLdapPassword],
    userDetailsForm: Form[EditUserDetailsRequest]
  )(using HeaderCarrier, RequestHeader): Future[Result] =
    for
      userOpt     <- userManagementConnector.getUser(username)
      userTooling <- userManagementConnector.getUserAccess(username).map(Right(_)).recover:
                       case e: UpstreamErrorResponse =>
                         logger.warn(s"Received a ${e.statusCode} response when getting access for user: $username. " +
                           s"Error: ${e.message}.")
                         Left("Unable to access User Management Portal to retrieve tooling. Please check again later.")
    yield
      userOpt match
        case Some(user) =>
          val umpProfileUrl = s"${umpConfig.userManagementProfileBaseUrl}/${user.username.asString}"
          resultType(userInfoPage(ldapForm, userDetailsForm, isAdminForUser(retrieval, user), userTooling, user, umpProfileUrl))
        case None =>
          NotFound(error_404_template())

  def user(username: UserName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        retrieval <- auth.verify(Retrieval.locations(
                       resourceType = Some(ResourceType("catalogue-frontend")),
                       action       = Some(IAAction("EDIT_USER"))
                     ))
        result    <- showUserInfoPage(username, retrieval, Ok(_), LdapResetForm.form, EditUserDetailsForm.form)
      yield result

  def updateUserDetails(username: UserName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      EditUserDetailsForm.form.bindFromRequest().fold(
        formWithErrors =>
          for
            retrieval <- auth.verify(Retrieval.locations(
                           resourceType = Some(ResourceType("catalogue-frontend")),
                           action       = Some(IAAction("EDIT_USER"))
                         ))
            result    <- showUserInfoPage(username, retrieval, BadRequest(_), LdapResetForm.form, formWithErrors)
          yield result
        , editRequest =>
          userManagementConnector.editUserDetails(editRequest)
            .map: _ =>
              Redirect(routes.UsersController.user(UserName(editRequest.username)))
                .flashing("success" -> s"User's ${editRequest.attribute.description} has been updated successfully")
            .recover:
              case NonFatal(e) =>
                Redirect(routes.UsersController.user(UserName(editRequest.username)))
                  .flashing(s"error" -> s"Error updating User Details for user's ${editRequest.attribute.description}. Contact #team-platops")
      )

  def requestLdapReset(username: UserName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_USER")))
    ).async: request =>
        given AuthenticatedRequest[AnyContent, Set[Resource]] = request
        LdapResetForm.form.bindFromRequest().fold(
          formWithErrors =>
            showUserInfoPage(username, Some(request.retrieval), BadRequest(_), formWithErrors, EditUserDetailsForm.form)
        , formData =>
            userManagementConnector.resetLdapPassword(formData).map: ticketOpt =>
              Ok(ldapResetRequestSentPage(username, ticketOpt))
            .recover:
              case NonFatal(e) =>
                logger.error(s"Error requesting LDAP password reset: ${e.getMessage}", e)
                Redirect(routes.UsersController.user(username)).flashing("error" -> "Error requesting LDAP password reset. Contact #team-platops")
        )

  private def isAdminForUser(retrieval: Option[Set[Resource]], user: User): Boolean =
    val teams = retrieval.fold(Set.empty[TeamName])(_.map(_.resourceLocation.value.stripPrefix("teams/")).map(TeamName.apply))
    teams.contains(TeamName("*")) || teams.exists(user.teamNames.contains) // Global admin or admin for user's team

  val requestNewVpnCert: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      auth.verify(Retrieval.locations(
        resourceType = Some(ResourceType("catalogue-frontend")),
        action       = Some(IAAction("EDIT_USER"))
      )).flatMap: retrieval =>
        request.body.asFormUrlEncoded.flatMap(_.get("username").flatMap(_.headOption)).map(UserName.apply)
          .fold(
            Future.failed(RuntimeException("Could not request a new vpn certificate as hidden username form field was empty"))
          ): username =>
            userManagementConnector.getUser(username).flatMap:
              case Some(user) =>
                if(isAdminForUser(retrieval, user)) then
                  userManagementConnector.requestNewVpnCert(username)
                    .map(ticketOpt => Created(vpnRequestSentPage(user, ticketOpt)))
                    .recover:
                      case NonFatal(e) =>
                        logger.error(s"Error requesting new VPN certificate: ${e.getMessage}", e)
                        Redirect(routes.UsersController.user(username)).flashing("error" -> "Error requesting VPN Certificate. Contact #team-platops")
                else
                  Future.successful(Redirect(routes.UsersController.user(username)).flashing("error" -> "Permission denied"))
              case _          =>
                Future.successful(Redirect(routes.UsersController.user(username)).flashing("error" -> "Unable to find user"))

  val users: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        retrieval      <- auth.verify:
                            Retrieval.locations(
                              resourceType = Some(ResourceType("catalogue-frontend")),
                              action       = Some(IAAction("CREATE_USER"))
                            )
        canCreateUsers =  retrieval.exists(_.nonEmpty)
      yield Ok(userListPage(canCreateUsers))

  def userSearch(
    query          : String,
    includeDeleted : Boolean,
    includeNonHuman: Boolean
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      val searchTerms = query.split("\\s+").toIndexedSeq // query is space-delimited
      userManagementConnector
        .searchUsers(searchTerms, includeDeleted, includeNonHuman)
        .map(matches => Ok(userSearchResults(matches)))

end UsersController

object LdapResetForm:
  val form: Form[ResetLdapPassword] =
    Form(
      Forms.mapping(
        "username" -> Forms.nonEmptyText,
        "email"    -> Forms.text.verifying(Constraints.emailAddress(errorMessage = "Please provide a valid email address."))
      )(ResetLdapPassword.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object EditUserDetailsForm:
  val form: Form[EditUserDetailsRequest] = Form(
    Forms.mapping(
      "username" -> Forms.nonEmptyText,
      "attribute" -> Forms.nonEmptyText.transform[UserAttribute](UserAttribute.fromString(_).get, _.name),
      "displayName" -> Forms.optional(Forms.text),
      "phoneNumber" -> Forms.optional(Forms.text),
      "github" -> Forms.optional(Forms.text),
      "organisation" -> Forms.optional(Forms.text)
    ) { (username, attribute, displayNameOpt, phoneNumberOpt, githubOpt, organisationOpt) =>
      val value = attribute match
        case UserAttribute.DisplayName  => displayNameOpt.getOrElse("")
        case UserAttribute.PhoneNumber  => phoneNumberOpt.getOrElse("")
        case UserAttribute.Github       => githubOpt.getOrElse("")
        case UserAttribute.Organisation => organisationOpt.getOrElse("")
      EditUserDetailsRequest(username, attribute, value)
    } { editUserDetailsRequest =>
      Some((
        editUserDetailsRequest.username,
        editUserDetailsRequest.attribute,
        if editUserDetailsRequest.attribute == UserAttribute.DisplayName then Some(editUserDetailsRequest.value) else None,
        if editUserDetailsRequest.attribute == UserAttribute.PhoneNumber then Some(editUserDetailsRequest.value) else None,
        if editUserDetailsRequest.attribute == UserAttribute.Github then Some(editUserDetailsRequest.value) else None,
        if editUserDetailsRequest.attribute == UserAttribute.Organisation then Some(editUserDetailsRequest.value) else None
      ))
    }.verifying(validateByAttribute)
  )

  private def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)

  private val nameConstraints: Constraint[String] =
    val nameLengthValidation: String => Boolean = str => str.length >= 2 && str.length <= 30

    mkConstraint(s"constraints.displayNameLengthCheck")(
      constraint = nameLengthValidation,
      error = "Name should be between 2 and 30 characters long"
    )

  private val phoneNumberConstraint: Constraint[String] =
    val phoneNumberValidation: String => Boolean =
      _.matches("""^(?=.*\d)[\d\s/+]*$""")

    mkConstraint("constraints.phoneNumber")(
      constraint = phoneNumberValidation,
      error = "Phone number can only contain digits, spaces, plus signs, or slashes."
    )

  private val githubUsernameConstraint: Constraint[String] =
    val githubUsernameValidation: String => Boolean =
      !_.isBlank

    mkConstraint("constraints.githubUsername")(
      constraint = githubUsernameValidation,
      error = "GitHub username cannot be set to empty once it has been provided."
    )

  private val organisationConstraint: Constraint[String] =
    val organisationValidation: String => Boolean =
      Organisation.values.map(_.asString).contains(_)

    mkConstraint("constraints.organisation")(
      constraint = organisationValidation,
      error = "Organisation must be MDTP, VOA, or Other."
    )

  private def validateByAttribute: Constraint[EditUserDetailsRequest] = Constraint("constraints.editUserDetailsRequest") { editUserDetailsRequest =>
    editUserDetailsRequest.attribute match
      case UserAttribute.DisplayName  => nameConstraints(editUserDetailsRequest.value)
      case UserAttribute.PhoneNumber  => phoneNumberConstraint(editUserDetailsRequest.value)
      case UserAttribute.Github       => githubUsernameConstraint(editUserDetailsRequest.value)
      case UserAttribute.Organisation => organisationConstraint(editUserDetailsRequest.value)
  }


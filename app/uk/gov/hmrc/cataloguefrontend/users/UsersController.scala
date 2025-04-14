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
import cats.implicits.*
import play.api.Logging
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.libs.json.Json
import play.api.mvc.*
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.view.html.{LdapResetRequestSentPage, UserInfoPage, UserListPage, UserSearchResults, VpnRequestSentPage}
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
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
, teamsAndReposConnector  : TeamsAndRepositoriesConnector
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
    resultType     : Html => Result,
    ldapForm       : Form[ResetLdapPassword],
    googleForm     : Form[ResetGooglePassword],
    userDetailsForm: Form[EditUserDetailsRequest],
    userRolesForm  : Form[UserRoles]
  )(using HeaderCarrier, RequestHeader): Future[Result] =


    for
      userOpt              <- userManagementConnector.getUser(username)
      retrieval            <- auth.verify(
                                Retrieval.locations(Some(ResourceType("catalogue-frontend")), Some(IAAction("EDIT_USER"))) ~
                                Retrieval.locations(Some(ResourceType("catalogue-frontend")), Some(IAAction("MANAGE_USER")))
                              )
      (editR, manageUserR) =  retrieval match
                                case Some(retrievalA ~ retrievalB) => (Some(retrievalA), Some(retrievalB))
                                case None              => (None, None)
      canManageUsers       =  manageUserR.exists(_.exists(_.resourceLocation.value.contains("teams/*")))
      currentRoles         <- if canManageUsers then userManagementConnector.getUserRoles(username) else Future.successful(UserRoles(Seq.empty[UserRole]))
      userTooling          <- userManagementConnector.getUserAccess(username).map(Right(_)).recover:
                                case e: UpstreamErrorResponse =>
                                  logger.warn(s"Received a ${e.statusCode} response when getting access for user: $username. " +
                                    s"Error: ${e.message}.")
                                  Left("Unable to access User Management Portal to retrieve tooling. Please check again later.")
      adminGithubTeams     <- editR match
                                case None            => Future.successful(Set.empty[TeamName])
                                case Some(resources) =>
                                  teamsAndReposConnector.allTeams().map(_.map(_.name).toSet).map(getAdminGithubTeams(resources, _))
    yield
      userOpt match
        case Some(user) =>
          val umpProfileUrl = s"${umpConfig.userManagementProfileBaseUrl}/${user.username.asString}"
          resultType(userInfoPage(ldapForm, googleForm, userDetailsForm, userRolesForm, isAdminForUser(editR, user), canManageUsers, currentRoles, userTooling, UserRoles(UserRole.values.toSeq), user, umpProfileUrl, adminGithubTeams))
        case None =>
          NotFound(error_404_template())

  def user(username: UserName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      showUserInfoPage(username, Ok(_), LdapResetForm.form, GoogleResetForm.form, EditUserDetailsForm.form, EditUserRolesForm.form)

  def updateUserDetails(username: UserName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      EditUserDetailsForm.form.bindFromRequest().fold(
        formWithErrors =>
          showUserInfoPage(username, BadRequest(_), LdapResetForm.form, GoogleResetForm.form, formWithErrors, EditUserRolesForm.form)
        , editRequest =>
          userManagementConnector.editUserDetails(editRequest)
            .map: _ =>
              Redirect(routes.UsersController.user(UserName(editRequest.username)))
                .flashing(
                  "success"   -> s"${editRequest.attribute.description} has been updated successfully for ${username.asString}.",
                  "attribute" -> editRequest.attribute.name
                )
            .recover:
              case NonFatal(e) =>
                Redirect(routes.UsersController.user(UserName(editRequest.username)))
                  .flashing(s"error" -> s"Error updating User Details for user's ${editRequest.attribute.description}. Contact #team-platops")
      )

  def updateUserRoles(username: UserName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("MANAGE_USER")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      EditUserRolesForm.form.bindFromRequest().fold(
        formWithErrors =>
            showUserInfoPage(username, BadRequest(_), LdapResetForm.form, GoogleResetForm.form, EditUserDetailsForm.form, formWithErrors)
      , userRoles =>
          userManagementConnector.editUserRoles(username, userRoles)
            .map: _ =>
              Redirect(routes.UsersController.user(username))
                .flashing(
                  "success" -> s"Authorisation roles have been updated successfully for ${username.asString}."
                )
            .recover:
              case NonFatal(e) =>
                Redirect(routes.UsersController.user(username))
                  .flashing(s"error" -> s"Error updating authorisation roles for ${username.asString}. Contact #team-platops")
      )

  def manageVpnAccess(username: UserName, enableVpn: Boolean): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("MANAGE_USER")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      userManagementConnector.manageVpnAccess(username, enableVpn)
        .map: _ =>
          Redirect(routes.UsersController.user(username))
            .flashing(
              "success" -> s"VPN access has been ${if enableVpn then "added" else "removed"} successfully for ${username.asString}."
            )
        .recover:
          case NonFatal(e) =>
            Redirect(routes.UsersController.user(username))
              .flashing(s"error" -> s"Error updating VPN access for ${username.asString}. Contact #team-platops")

  def manageDevToolsAccess(username: UserName, enableDevTools: Boolean): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("MANAGE_USER")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      userManagementConnector.manageDevToolsAccess(username, enableDevTools)
        .map: _ =>
          Redirect(routes.UsersController.user(username))
            .flashing(
              "success" -> s"Developer Tools have been ${if enableDevTools then "added" else "removed"} successfully for ${username.asString}."
            )
        .recover:
          case NonFatal(e) =>
            Redirect(routes.UsersController.user(username))
              .flashing(s"error" -> s"Error updating Developer Tools for ${username.asString}. Contact #team-platops")

  def requestLdapReset(username: UserName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_USER")))
    ).async: request =>
        given AuthenticatedRequest[AnyContent, Set[Resource]] = request
        LdapResetForm.form.bindFromRequest().fold(
          formWithErrors =>
            showUserInfoPage(username, BadRequest(_), formWithErrors, GoogleResetForm.form, EditUserDetailsForm.form, EditUserRolesForm.form)
        , formData =>
            userManagementConnector.resetLdapPassword(formData).map: ticketOpt =>
              Ok(ldapResetRequestSentPage(username, ticketOpt))
            .recover:
              case NonFatal(e) =>
                logger.error(s"Error requesting LDAP password reset: ${e.getMessage}", e)
                Redirect(routes.UsersController.user(username)).flashing("error" -> "Error requesting LDAP password reset. Contact #team-platops")
        )

  def requestGoogleReset(username: UserName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_USER")))
    ).async: request =>
        given AuthenticatedRequest[AnyContent, Set[Resource]] = request
        GoogleResetForm.form.bindFromRequest().fold(
          formWithErrors =>
              showUserInfoPage(username, BadRequest(_), LdapResetForm.form, formWithErrors, EditUserDetailsForm.form, EditUserRolesForm.form)
          , formData =>
            userManagementConnector.resetGooglePassword(formData).map: _ =>
              Redirect(routes.UsersController.user(username)).flashing("success" -> s"Request to reset Google password for ${username.asString} sent successfully.")
            .recover:
              case NonFatal(e) =>
                logger.error(s"Error requesting Google password reset: ${e.getMessage}", e)
                Redirect(routes.UsersController.user(username)).flashing("error" -> "Error requesting Google password reset. Contact #team-platops")
        )

  private def isAdminForUser(retrieval: Option[Set[Resource]], user: User): Boolean =
    val teams = retrieval.fold(Set.empty[TeamName])(_.map(_.resourceLocation.value.stripPrefix("teams/")).map(TeamName.apply))
    teams.contains(TeamName("*")) || teams.exists(user.teamNames.contains) // Global admin or admin for user's team

  private def getAdminGithubTeams(resources: Set[Resource], githubTeams: Set[TeamName]): Set[TeamName] =
      val adminTeams = resources.map(_.resourceLocation.value.stripPrefix("teams/")).map(TeamName.apply)
      if adminTeams.contains(TeamName("*")) then
        githubTeams
      else
        githubTeams.intersect(adminTeams)

  def addToGithubTeam(username: UserName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.UsersController.user(username),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_USER")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      AddToGithubTeamForm.form.bindFromRequest().fold(
        formWithErrors =>
          showUserInfoPage(username, BadRequest(_), LdapResetForm.form, GoogleResetForm.form, EditUserDetailsForm.form, EditUserRolesForm.form)
      , formData =>
          userManagementConnector.addToGithubTeam(formData).map: _ =>
            Redirect(routes.UsersController.user(username)).flashing("success" -> s"Request to add user to Github team: ${formData.team} sent successfully.")
          .recover:
            case NonFatal(e) =>
              logger.error(s"Error requesting user ${formData.username} be added to github team ${formData.team} - ${e.getMessage}", e)
              Redirect(routes.UsersController.user(username)).flashing("error" -> "Error processing request. Contact #team-platops")
      )
  
  val requestNewVpnCert: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      auth.verify(Retrieval.locations(
        resourceType = Some(ResourceType("catalogue-frontend")),
        action       = Some(IAAction("EDIT_USER"))
      )).flatMap: retrieval =>
        request.body.asFormUrlEncoded.flatMap(_.get("username").flatMap(_.headOption)).map(UserName.apply).fold(
          Future.failed(RuntimeException("Could not request a new vpn certificate as hidden username form field was empty"))
        ): username =>
          ( for
              user    <- EitherT.fromOptionF(userManagementConnector.getUser(username)      , "Unable to find user")
              _       <- EitherT.fromOption(Option.when(isAdminForUser(retrieval, user))(()), "Permission Denied")
              oTicket <- EitherT:
                           userManagementConnector
                             .requestNewVpnCert(username)
                             .map(Right.apply)
                             .recover:
                               case NonFatal(e) =>
                                 logger.error(s"Error requesting new VPN certificate: ${e.getMessage}", e)
                                 Left("Error requesting VPN Certificate. Contact #team-platops")
            yield Created(vpnRequestSentPage(user, oTicket))
          ).fold(
            message => Redirect(routes.UsersController.user(username)).flashing("error" -> message)
          , created => created
          )

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

  def addUserToTeamSearch(
    query          : String
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      val searchTerms = query.split("\\s+").toIndexedSeq // query is space-delimited
      userManagementConnector
        .searchUsers(searchTerms, includeDeleted = false, includeNonHuman = true)
        .map: matches =>
          val usernames = matches.map(_.username.asString)
          Ok(Json.toJson(usernames))

end UsersController

object LdapResetForm:
  val form: Form[ResetLdapPassword] =
    Form(
      Forms.mapping(
        "username" -> Forms.nonEmptyText,
        "email"    -> Forms.text.verifying(Constraints.emailAddress(errorMessage = "Please provide a valid email address."))
      )(ResetLdapPassword.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object GoogleResetForm:
  val form: Form[ResetGooglePassword] =
    Form(
      Forms.mapping(
        "username" -> Forms.nonEmptyText,
        "password" -> Forms.text.verifying(UserConstraints.passwordConstraint: _*).transform[SensitiveString](SensitiveString.apply, _.decryptedValue)
      )(ResetGooglePassword.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object AddToGithubTeamForm:
  val form: Form[AddToGithubTeamRequest] =
    Form(
      Forms.mapping(
        "username" -> Forms.nonEmptyText,
        "team"     -> Forms.nonEmptyText
      )(AddToGithubTeamRequest.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object EditUserRolesForm:
  val form: Form[UserRoles] =
    Form(
      Forms.mapping(
        "roles" -> Forms.list(Forms.text)
      ) ( roleStrings => UserRoles(roleStrings.flatMap(UserRole.fromString)) )
        ( userRoles   => Some(userRoles.roles.map(_.role).toList) )
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
    }.verifying(UserConstraints.validateByAttribute)
  )

object UserConstraints:
  private def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)

  val passwordConstraint: Seq[Constraint[String]] =
    val passwordValidation: String => Boolean =
      _.matches("""^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[!@#$%^&*()\-_=+{};:,<.>/?])(?!.*£).+$""")

    val passwordLengthValidation: String => Boolean = str => str.length >= 8 && str.length <= 100

    Seq(
      mkConstraint (s"constraints.passwordLengthCheck")(
        constraint = passwordLengthValidation,
        error = "Password must be between 8 and 100 characters long"
      ),
      mkConstraint(s"constraints.passwordValidCheck")(
        constraint = passwordValidation,
        error = "Password must contain uppercase, lowercase, numeric and special characters, and must not contain a pound symbol (£)"
      )
    )

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

  def validateByAttribute: Constraint[EditUserDetailsRequest] = Constraint("constraints.editUserDetailsRequest") { editUserDetailsRequest =>
    editUserDetailsRequest.attribute match
      case UserAttribute.DisplayName  => nameConstraints(editUserDetailsRequest.value)
      case UserAttribute.PhoneNumber  => phoneNumberConstraint(editUserDetailsRequest.value)
      case UserAttribute.Github       => githubUsernameConstraint(editUserDetailsRequest.value)
      case UserAttribute.Organisation => organisationConstraint(editUserDetailsRequest.value)
  }


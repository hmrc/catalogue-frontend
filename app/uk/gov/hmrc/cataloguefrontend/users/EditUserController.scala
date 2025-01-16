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
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.mvc.*
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.view.html.{EditUserAccessPage, EditUserRequestSentPage}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EditUserController @Inject()(
  override val auth      : FrontendAuthComponents,
  override val mcc       : MessagesControllerComponents,
  editUserAccessPage     : EditUserAccessPage,
  editUserRequestSentPage: EditUserRequestSentPage,
  userManagementConnector: UserManagementConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with Logging
     with play.api.i18n.I18nSupport:

  private def editUserPermission(teamNames: Seq[TeamName]): Predicate =
    Predicate.or(
      (teamNames.map: teamName =>
        Predicate.Permission(Resource.from("catalogue-frontend", s"teams/${teamName.asString}"), IAAction("EDIT_USER"))
      ): _*
    )

  def requestSent(username: UserName): Action[AnyContent] =
    Action: request =>
      given RequestHeader = request
      Ok(editUserRequestSentPage(username))

  def editUserLanding(username: UserName, organisation: Option[String]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.EditUserController.editUserLanding(username),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_USER")))
    ).async: request =>
      given RequestHeader = request
      userManagementConnector.getUserAccess(username).map: existingAccess =>
        Ok(editUserAccessPage(EditUserAccessForm.form, username, organisation, existingAccess))

  def editUserAccess(username: UserName, organisation: Option[String]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.EditUserController.editUserLanding(username, organisation),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_USER")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      (for
        existingTooling <- EitherT.liftF(userManagementConnector.getUserAccess(username))
        userOpt         <- EitherT.liftF[Future, Result, Option[User]](userManagementConnector.getUser(username))
        teams           =  userOpt.fold(Seq.empty)(_.teamNames)
        form            <- EitherT.fromEither[Future]:
                             EditUserAccessForm.form.bindFromRequest()
                               .fold(
                                 formWithErrors =>
                                   Left(
                                     BadRequest(
                                       editUserAccessPage(
                                         form             = formWithErrors,
                                         username         = username,
                                         organisation     = organisation,
                                         existingAccess   = existingTooling
                                       )
                                     )
                                   ),
                                 validForm => Right(validForm)
                               )
        changesToSubmit =  EditUserAccessRequest(
                             username     = form.username,
                             organisation = form.organisation,
                             vpn          = form.vpn && !existingTooling.vpn,
                             jira         = form.jira && !existingTooling.jira,
                             confluence   = form.confluence && !existingTooling.confluence,
                             googleApps   = form.googleApps && !existingTooling.googleApps,
                             environments = form.environments && !existingTooling.devTools,
                             bitwarden    = form.bitwarden
                           )
        _               <- EitherT.liftF(auth.authorised(Some(editUserPermission(teams))))
        res             <- EitherT.right[Result](userManagementConnector.editUserAccess(changesToSubmit))
        _               =  logger.info(s"user management result: $res:")
       yield Redirect(uk.gov.hmrc.cataloguefrontend.users.routes.EditUserController.requestSent(username))
      ).merge

end EditUserController

object EditUserAccessForm:
  val form: Form[EditUserAccessRequest] =
    Form:
      Forms.mapping(
        "username"     -> Forms.nonEmptyText,
        "organisation" -> Forms.nonEmptyText,
        "vpn"          -> Forms.boolean,
        "jira"         -> Forms.boolean,
        "confluence"   -> Forms.boolean,
        "googleApps"   -> Forms.boolean,
        "environments" -> Forms.boolean,
        "bitwarden"    -> Forms.boolean
      )(EditUserAccessRequest.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying(EditUserConstraints.accessHasChanged)

object EditUserConstraints:
  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)

  //we only want to send any new access and having the checkboxes disabled returns the value as false
  private val hasChanges: EditUserAccessRequest => Boolean =
    access =>
      Seq(
        access.vpn,
        access.jira,
        access.confluence,
        access.googleApps,
        access.environments,
        access.bitwarden
      ).contains(true)

  val accessHasChanged: Constraint[EditUserAccessRequest] =
    mkConstraint("constraints.accessHasChangedCheck")(
      constraint = hasChanges,
      error      = "At least one new tooling access must be requested"
    )

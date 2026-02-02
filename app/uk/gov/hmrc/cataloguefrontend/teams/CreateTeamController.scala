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

package uk.gov.hmrc.cataloguefrontend.teams

import play.api.Logging
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.mvc.*
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.teams.view.html.{CreateTeamPage}
import uk.gov.hmrc.cataloguefrontend.users.Organisation
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class CreateTeamController @Inject()(
  override val auth        : FrontendAuthComponents,
  override val mcc         : MessagesControllerComponents,
  createTeamPage           : CreateTeamPage,
  userManagementConnector  : UserManagementConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport
     with Logging:

  private def createTeamPermission: Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"teams/*"), IAAction("MANAGE_TEAM"))

  def createTeamLanding: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateTeamController.createTeamLanding,
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("MANAGE_TEAM")))
    ).async: request =>
      given RequestHeader = request
      userManagementConnector.getAvailablePlatforms().map: platforms =>
        Ok(createTeamPage(CreateTeamForm.form, platforms, Organisation.values.toSeq))

  def createTeam: Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateTeamController.createTeamLanding,
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("MANAGE_TEAM")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      CreateTeamForm.form.bindFromRequest().fold(
        formWithErrors =>
          userManagementConnector.getAvailablePlatforms().map: platforms =>
            BadRequest(createTeamPage(formWithErrors, platforms, Organisation.values.toSeq))
      , validForm =>
          for
            _      <- auth.authorised(Some(createTeamPermission))
            result <- userManagementConnector.createTeam(validForm).map: _ =>
                        Redirect(routes.TeamsController.allTeams()).flashing("success" -> s"Request to create team ${validForm.team} in the ${validForm.organisation} organisation sent successfully.")
                      .recover:
                        case NonFatal(e) =>
                          logger.error(s"Error requesting Team Creation: ${e.getMessage}", e)
                          Redirect(routes.CreateTeamController.createTeamLanding).flashing("error" -> "Error requesting Team Creation. Contact #team-platops")
          yield result
      )

end CreateTeamController

object CreateTeamForm:
  val form: Form[CreateTeamRequest] =
    Form(
      Forms.mapping(
        "organisation"     -> Forms.nonEmptyText,
        "team"             -> Forms.text
                                .verifying(CreateTeamConstraints.teamNameConstraints: _*)
                                .transform(_.trim, _.toString)
      )(CreateTeamRequest.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object CreateTeamConstraints:
  private def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)

  val teamNameConstraints: Seq[Constraint[String]] =
    val nonEmptyValidation: String => Boolean = _.trim.nonEmpty
    val validPattern: String => Boolean = _.matches("^[a-zA-Z0-9_\\- ]+$")
    val validLength: String => Boolean = _.length <= 30

    Seq(
      mkConstraint("constraints.nonEmptyTeamNameCheck")(
        constraint = nonEmptyValidation,
        error = "Team name cannot be empty"
      ),
      mkConstraint("constraints.teamNameLengthCheck")(
        constraint = validLength,
        error = "Team name must be less than 30 characters long"
      ),
      mkConstraint("constraints.teamNameValidCheck")(
        constraint = validPattern,
        error = "Team name can only contain letters, numbers, spaces, underscores (_), or hyphens (-)"
      )
    )

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

import cats.data.EitherT
import cats.implicits.*
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, RequestHeader, Result}
import play.api.Logging
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.data.{Form, Forms}
import play.api.i18n.I18nSupport
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, EditTeamDetails, Environment, SlugInfoFlag, TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.teams.view.html.{DigitalServicePage, TeamInfoPage, TeamsListPage}
import uk.gov.hmrc.cataloguefrontend.users.{ManageTeamMembersRequest, UmpTeam}
import uk.gov.hmrc.cataloguefrontend.healthmetrics.HealthMetricsConnector
import uk.gov.hmrc.cataloguefrontend.view.html.{OutOfDateTeamDependenciesPage, error_404_template}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class TeamsController @Inject()(
  userManagementConnector            : UserManagementConnector
, teamsAndRepositoriesConnector      : TeamsAndRepositoriesConnector
, serviceDependenciesConnector       : ServiceDependenciesConnector
, healthMetricsConnector             : HealthMetricsConnector
, teamInfoPage                       : TeamInfoPage
, digitalServicePage                 : DigitalServicePage
, outOfDateTeamDependenciesPage      : OutOfDateTeamDependenciesPage
, override val mcc                   : MessagesControllerComponents
, override val auth                  : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with Logging
     with I18nSupport:

  private def canCreateAndDeleteTeams(retrieval: Option[Set[Resource]]): Boolean =
    val teams = retrieval.fold(Set.empty[String])(_.map(_.resourceLocation.value))
    teams.contains("teams/*")

  private def showTeamPage(
    teamName: TeamName,
    resultType: Html => Result,
    teamDetailsForm: Form[EditTeamDetails]
  )(using HeaderCarrier, RequestHeader): Future[Result] =
    (for
      umpTeam <- EitherT.fromOptionF[Future, Result, UmpTeam](userManagementConnector.getTeam(teamName), NotFound(error_404_template()))
      editR   <- EitherT.right:
                   auth.verify:
                    Retrieval.locations(
                      resourceType = Some(ResourceType("catalogue-frontend")),
                      action       = Some(IAAction("EDIT_TEAM"))
                    )
      deleteR <- EitherT.right:
                   auth.verify:
                     Retrieval.locations(
                       resourceType = Some(ResourceType("catalogue-frontend")),
                       action       = Some(IAAction("MANAGE_TEAM"))
                     )
      results <- EitherT.right:
                   ( teamsAndRepositoriesConnector.allTeams(Some(teamName)).map(_.headOption.map(_.githubUrl))
                   , teamsAndRepositoriesConnector.allRepositories(team = Some(teamName), archived = Some(false))
                   , teamsAndRepositoriesConnector.openPullRequestsRaisedByMembersOfTeam(teamName).map: openPrs =>
                       val openPRsRaisedByMembersOfTeamUrl = s"https://github.com/search?q=org:hmrc+is:pr+is:open+archived:false+${openPrs.map(_.author).distinct.map { a => s"author:$a" }.mkString("+")}&type=pullrequests"
                       url"$openPRsRaisedByMembersOfTeamUrl"
                   , teamsAndRepositoriesConnector.openPullRequestsForReposOwnedByTeam(teamName).map: openPrs =>
                       val openPRsForReposOwnedByTeamUrl   = s"https://github.com/search?q=${openPrs.map(_.repoName).distinct.map { r => s"repo:hmrc/$r" }.mkString("+")}+is:pr+is:open&type=pullrequests"
                       url"$openPRsForReposOwnedByTeamUrl"
                   , healthMetricsConnector.latestTeamHealthMetrics(umpTeam.teamName)
                   ).tupled
     yield
       resultType(
         teamInfoPage(
           teamName                        = umpTeam.teamName
         , umpTeam                         = umpTeam
         , teamDetailsForm                 = teamDetailsForm
         , gitHubUrl                       = results._1
         , repos                           = results._2
         , openPRsRaisedByMembersOfTeamUrl = results._3
         , openPRsForReposOwnedByTeamUrl   = results._4
         , healthMetrics                   = results._5
         , canEditTeam                     = canEditTeam(editR, teamName)
         , canDeleteTeams                  = canCreateAndDeleteTeams(deleteR))
         )
    ).merge

  def team(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      showTeamPage(teamName, Ok(_), TeamDetailsForm.form())

  def digitalService(digitalService: DigitalService): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        results <- ( teamsAndRepositoriesConnector.allRepositories(digitalService = Some(digitalService), archived = Some(false))
                   , teamsAndRepositoriesConnector.openPullRequestsForReposOwnedByDigitalService(digitalService).map: openPrs =>
                       val openPRsForReposOwnedByDigitalServiceUrl = s"https://github.com/search?q=${openPrs.map(_.repoName).distinct.map { r => s"repo:hmrc/$r" }.mkString("+")}+is:pr+is:open&type=pullrequests"
                       url"$openPRsForReposOwnedByDigitalServiceUrl"
                   , healthMetricsConnector.latestDigitalServiceHealthMetrics(digitalService)
                   ).tupled
      yield
        Ok(digitalServicePage(
          digitalService          = digitalService
        , repos                   = results._1
        , openPRsForOwnedReposUrl = results._2
        , healthMetrics           = results._3
        ))

  def allTeams(name: Option[String]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      ( userManagementConnector.getAllTeams()
      , teamsAndRepositoriesConnector.allTeams()
        // This retrieval is not happening in original auth call to make unauthenticated testing easier
      , auth.verify(Retrieval.locations(Some(ResourceType("catalogue-frontend")), Some(IAAction("MANAGE_TEAM"))))
      ).mapN: (umpTeams, gitHubTeams, retrieval) =>
        Ok(TeamsListPage(
          teams = umpTeams.map(umpTeam => (umpTeam, gitHubTeams.find(_.name == umpTeam.teamName))),
          name,
          canCreateAndDeleteTeams(retrieval)
        ))

  def outOfDateTeamDependencies(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      ( teamsAndRepositoriesConnector.allRepositories(team = Some(teamName), archived = Some(false))
      , serviceDependenciesConnector.dependenciesForTeam(teamName)
      , serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
      ).mapN:
        ( repos
        , masterTeamDependencies
        , prodDependencies
        ) =>
        val repoLookup = repos.map(r => r.name -> r).toMap
        Ok(outOfDateTeamDependenciesPage(
          teamName,
          masterTeamDependencies.flatMap(mtd => repoLookup.get(mtd.repositoryName).map(gr => RepoAndDependencies(gr, mtd))),
          prodDependencies
        ))

  private def canEditTeam(retrieval: Option[Set[Resource]], team: TeamName): Boolean =
    val teams = retrieval.fold(Set.empty[TeamName])(_.map(_.resourceLocation.value.stripPrefix("teams/")).map(TeamName.apply))
    teams.contains(TeamName("*")) || teams.contains(team)

  def editTeamDetails(teamName: TeamName, fieldBeingEdited: Option[String]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.TeamsController.team(teamName),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_TEAM")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      TeamDetailsForm.form(fieldBeingEdited).bindFromRequest().fold(
        formWithErrors =>
          showTeamPage(teamName, BadRequest(_), formWithErrors)
      , formData =>
          userManagementConnector.editTeamDetails(formData)
            .map: _ =>
              Redirect(routes.TeamsController.team(teamName)).flashing("success" -> s"Request to edit team details for ${formData.team} sent successfully.")
            .recover:
              case NonFatal(e) =>
                logger.error(s"Error updating team details for team ${formData.team} - ${e.getMessage}", e)
                Redirect(routes.TeamsController.team(teamName)).flashing("error" -> "Error processing request. Contact #team-platops")
      )

  def deleteTeam(teamName: TeamName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.TeamsController.team(teamName),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("MANAGE_TEAM")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      userManagementConnector.deleteTeam(teamName)
        .map: _ =>
          Redirect(routes.TeamsController.allTeams()).flashing("success" -> s"Request to delete team ${teamName.asString} sent successfully.")
        .recover:
          case NonFatal(e) =>
            logger.error(s"Error deleting team ${teamName.asString} - ${e.getMessage}", e)
            Redirect(routes.TeamsController.team(teamName)).flashing("error" -> "Error processing request to delete team. Contact #team-platops")

  def addUserToTeam(teamName: TeamName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.TeamsController.team(teamName),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_TEAM")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      ManageTeamMembersForm.form.bindFromRequest().fold(
        formWithErrors =>
          logger.error(s"Unexpected error reading Add User To Team form - $formWithErrors")
          showTeamPage(teamName, BadRequest(_), TeamDetailsForm.form())
      , formData =>
          userManagementConnector.addUserToTeam(formData)
            .map: _ =>
              Redirect(routes.TeamsController.team(teamName)).flashing("success" -> s"Request to add user to team: ${formData.team} sent successfully.")
            .recover:
              case NonFatal(e) =>
                logger.error(s"Error requesting user ${formData.username} be added to team ${formData.team} - ${e.getMessage}", e)
                Redirect(routes.TeamsController.team(teamName)).flashing("error" -> "Error processing request. Contact #team-platops")
      )

  def removeUserFromTeam(teamName: TeamName): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.TeamsController.team(teamName),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("EDIT_TEAM")))
    ).async: request =>
      given AuthenticatedRequest[AnyContent, Set[Resource]] = request
      ManageTeamMembersForm.form.bindFromRequest().fold(
        formWithErrors =>
          logger.error(s"Unexpected error handling Remove User From Team form - $formWithErrors")
          showTeamPage(teamName, BadRequest(_), TeamDetailsForm.form())
      , formData =>
          userManagementConnector.getUser(UserName(formData.username))
            .flatMap:
              case None =>
                Future.successful(
                  Redirect(routes.TeamsController.team(teamName))
                    .flashing("error" -> "Unable to determine if the user belongs to more than one team. Contact #team-platops")
                )
              case Some(user) if user.teamNames.length <= 1 =>
                Future.successful(
                  Redirect(routes.TeamsController.team(teamName))
                    .flashing("error" -> s"Cannot remove user from their only team. Please add them to another team first.")
                )
              case Some(_) =>
                userManagementConnector.removeUserFromTeam(formData).map: _ =>
                  Redirect(routes.TeamsController.team(teamName))
                    .flashing("success" -> s"Request to remove user from team: ${formData.team} sent successfully.")
            .recover:
              case NonFatal(e) =>
                logger.error(s"Error requesting user ${formData.username} be removed from team ${formData.team} - ${e.getMessage}", e)
                Redirect(routes.TeamsController.team(teamName))
                  .flashing("error" -> "Error processing request. Contact #team-platops")
      )

end TeamsController

object ManageTeamMembersForm:
  val form: Form[ManageTeamMembersRequest] =
    Form(
      Forms.mapping(
        "team"     -> Forms.nonEmptyText,
        "username" -> Forms.nonEmptyText
      )(ManageTeamMembersRequest.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object TeamDetailsForm:
  def form(fieldBeingEdited: Option[String] = None): Form[EditTeamDetails] =
    Form(
      Forms.mapping(
        "team"              -> Forms.nonEmptyText,
        "description"       -> Forms.optional(Forms.text).verifying(TeamConstraints.descriptionConstraint(fieldBeingEdited): _*),
        "documentation"     -> Forms.optional(Forms.text).verifying(TeamConstraints.documentationConstraint(fieldBeingEdited): _*),
        "slack"             -> Forms.optional(Forms.text).verifying(TeamConstraints.slackTeamConstraint(fieldBeingEdited): _*),
        "slackNotification" -> Forms.optional(Forms.text).verifying(TeamConstraints.slackNotificationConstraint(fieldBeingEdited): _*)
      )(EditTeamDetails.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

object TeamConstraints:
  private def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName): toBeValidated =>
      if constraint(toBeValidated) then Valid else Invalid(error)

  private val nonEmptyValidation: String => Boolean =
    _.trim.nonEmpty

  private val slackConstraints: Seq[Constraint[Option[String]]] =
    val slackNameValidation: String => Boolean =
      _.matches("^[a-z0-9._-]+$")

    val slackLengthValidation: String => Boolean =
      _.length < 80

    Seq(
      mkConstraint("constraints.nonEmptySlackCheck")(
        constraint = _.exists(nonEmptyValidation),
        error = "Slack channel name cannot be empty"
      ),
      mkConstraint("constraints.slackLengthCheck")(
        constraint = _.forall(slackLengthValidation),
        error = "Slack channel name must be less than 80 characters long"
      ),
      mkConstraint("constraints.slackValidCheck")(
        constraint = _.forall(slackNameValidation),
        error = "Slack channel name must only contain letters, numbers, periods (.), underscores (_), or hyphens (-)"
      )
    )

  def descriptionConstraint(fieldBeingEdited: Option[String]): Seq[Constraint[Option[String]]] =
    if fieldBeingEdited.contains("description") then
      Seq(
        mkConstraint("constraints.nonEmptyDescriptionCheck")(
          constraint = _.exists(nonEmptyValidation),
          error = "Description cannot be empty"
        )
      )
    else Seq.empty

  def documentationConstraint(fieldBeingEdited: Option[String]): Seq[Constraint[Option[String]]] =
    if fieldBeingEdited.contains("documentation") then
      val urlValidation: String => Boolean =
        _.matches("""^(https?://)([\w.-]+)(:[0-9]+)?(/.*)?$""")

      Seq(
        mkConstraint("constraints.nonEmptyUrlCheck")(
          constraint = _.exists(nonEmptyValidation),
          error = "Documentation URL cannot be empty"
        ),
        mkConstraint("constraints.validUrlCheck")(
          constraint = _.forall(urlValidation),
          error = "Documentation URL must be a valid URL starting with http:// or https://"
        )
      )
    else Seq.empty

  def slackTeamConstraint(fieldBeingEdited: Option[String]): Seq[Constraint[Option[String]]] =
    if fieldBeingEdited.contains("slack") then
      slackConstraints
    else Seq.empty

  def slackNotificationConstraint(fieldBeingEdited: Option[String]): Seq[Constraint[Option[String]]] =
    if fieldBeingEdited.contains("slackNotification") then
      slackConstraints
    else Seq.empty

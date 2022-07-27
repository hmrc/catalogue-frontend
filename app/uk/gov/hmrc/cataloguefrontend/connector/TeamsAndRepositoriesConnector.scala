/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

sealed trait RepoType { def asString: String }

object RepoType {
  case object Service   extends RepoType { override val asString = "Service"   }
  case object Library   extends RepoType { override val asString = "Library"   }
  case object Prototype extends RepoType { override val asString = "Prototype" }
  case object Other     extends RepoType { override val asString = "Other"     }

  val values: List[RepoType] = List(Service, Library, Prototype, Other)

  def parse(s: String): Either[String, RepoType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid repoType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[RepoType] =
    new Format[RepoType] {
      override def reads(json: JsValue): JsResult[RepoType] =
        json match {
          case JsString(s) => parse(s).fold(msg => JsError(msg), rt => JsSuccess(rt))
          case _           => JsError("String value expected")
        }

      override def writes(rt: RepoType): JsValue =
        JsString(rt.asString)
    }
}

case class Link(name: String, displayName: String, url: String) {
  val id: String = displayName.toLowerCase.replaceAll(" ", "-")
}

object Link {
  val format: Format[Link] =
    Json.format[Link]
}

case class JenkinsLink(service: String, jenkinsURL: String)

case class GitRepository(
  name                : String,
  description         : String,
  githubUrl           : String,
  createdDate         : Instant,
  lastActiveDate      : Instant,
  isPrivate           : Boolean                  = false,
  repoType            : RepoType                 = RepoType.Other,
  digitalServiceName  : Option[String]           = None,
  owningTeams         : Seq[String]              = Nil,
  language            : Option[String],
  isArchived          : Boolean,
  defaultBranch       : String,
  branchProtection    : Option[BranchProtection] = None,
  isDeprecated        : Boolean                  = false,
  teamNames           : Seq[String]              = Nil,
  jenkinsURL          : Option[String]           = None,
  prototypeUrl        : Option[String]           = None
) {
// Repos with joint ownership of 8+ teams, defines the repo as shared
  def isShared: Boolean = teamNames.length >= 8

  def teamNameDisplay: String = if (isShared) s"Shared by ${teamNames.length} teams" else teamNames.sortBy(_.toLowerCase).mkString(",")

  def status: Option[String] = (isArchived, isDeprecated) match {
    case (true, false) => Some("archived")
    case (false, true) => Some("deprecated")
    case _             => None
  }
  def branchProtectionEnabled = branchProtection.map(_.isProtected)
}

object GitRepository {
  val apiFormat: OFormat[GitRepository] = {
    implicit val rtf = RepoType.format

    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[Instant]
    ~ (__ \ "lastActiveDate"    ).format[Instant]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "isArchived"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"     ).format[String]
    ~ (__ \ "branchProtection"  ).formatNullable(BranchProtection.format)
    ~ (__ \ "isDeprecated"      ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"         ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "jenkinsUrl"       ).formatNullable[String]
    ~ (__ \ "prototypeUrl"      ).formatNullable[String]
    ) (apply, unlift(unapply))
  }
}

final case class BranchProtection(
  requiresApprovingReviews: Boolean,
  dismissesStaleReviews: Boolean,
  requiresCommitSignatures: Boolean
) {

  def isProtected: Boolean =
    requiresApprovingReviews && dismissesStaleReviews && requiresCommitSignatures
}

object BranchProtection {

  val format: Format[BranchProtection] =
    ( (__ \ "requiresApprovingReviews").format[Boolean]
    ~ (__ \ "dismissesStaleReviews"   ).format[Boolean]
    ~ (__ \ "requiresCommitSignatures").format[Boolean]
    )(apply, unlift(unapply))
}

case class Team(
  name           : TeamName,
  createdDate    : Option[Instant],
  lastActiveDate : Option[Instant],
  repos          : Int = 0
)

object Team {
  val format: OFormat[Team] = {
    implicit val tnf = TeamName.format
    ( (__ \ "name"          ).format[TeamName]
    ~ (__ \ "createdDate"   ).formatNullable[Instant]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).format[Int]
    )(apply, unlift(unapply))
  }
}

object TeamsAndRepositoriesEnvironment {
  val format: Format[Environment] =
    new Format[Environment] {
      override def reads(json: JsValue) =
        json
          .validate[String]
          .flatMap {
            case "Production"    => JsSuccess(Environment.Production)
            case "External Test" => JsSuccess(Environment.ExternalTest)
            case "QA"            => JsSuccess(Environment.QA)
            case "Staging"       => JsSuccess(Environment.Staging)
            case "Integration"   => JsSuccess(Environment.Integration)
            case "Development"   => JsSuccess(Environment.Development)
            case s               => JsError(__, s"Invalid Environment '$s'")
          }

      override def writes(e: Environment) =
        JsString(e.asString)
    }
}

@Singleton
class TeamsAndRepositoriesConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._
  import TeamsAndRepositoriesConnector._

  private val logger = Logger(getClass)

  private val teamsAndServicesBaseUrl: String =
    servicesConfig.baseUrl("teams-and-repositories")

  private implicit val tf   = Team.format
  private implicit val jf   = Json.format[JenkinsLink]
  private implicit val ghrf = GitRepository.apiFormat // v2 model

  def lookupLink(service: String)(implicit hc: HeaderCarrier): Future[Option[Link]] = {
    val url = url"$teamsAndServicesBaseUrl/api/jenkins-url/$service"
    httpClientV2
      .get(url)
      .execute[Option[JenkinsLink]]
      .map(_.map(l => Link(l.service, "Build", l.jenkinsURL)))
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          None
      }
  }

  def allTeams(implicit hc: HeaderCarrier): Future[Seq[Team]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/teams")
      .execute[Seq[Team]]

  def repositoriesForTeam(teamName: TeamName, includeArchived: Option[Boolean] = None)(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] = {
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories?team=${teamName.asString}&archived=$includeArchived"

    httpClientV2
      .get(url)
      .execute[Seq[GitRepository]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Nil
      }
  }

  def allRepositories(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories")
      .execute[Seq[GitRepository]]

  def allServices(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories?repoType=Service")
      .execute[Seq[GitRepository]]

  def archivedRepositories(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$teamsAndServicesBaseUrl/api/v2/repositories?archived=true")
      .execute[Seq[GitRepository]]

  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[GitRepository]] = {
    for {
      repo    <- httpClientV2
                   .get(url"$teamsAndServicesBaseUrl/api/v2/repositories/$name")
                   .execute[Option[GitRepository]]
      jenkins <- lookupLink(name)
      withLink = repo.map(_.copy(jenkinsURL = jenkins.map(_.url)))
    } yield withLink
  }

  def allTeamsByService()(implicit hc: HeaderCarrier): Future[Map[ServiceName, Seq[TeamName]]] =
    for {
      repos         <- httpClientV2
                         .get(url"$teamsAndServicesBaseUrl/api/v2/repositories")
                         .execute[Seq[GitRepository]]
      teamsByService = repos.map(r => r.name -> r.teamNames.map(TeamName.apply)).toMap
    } yield teamsByService

  def enableBranchProtection(repoName: String)(implicit hc: HeaderCarrier): Future[Unit] =
    httpClientV2
      .post(url"$teamsAndServicesBaseUrl/api/v2/repositories/$repoName/branch-protection/enabled")
      .withBody(JsBoolean(true))
      .execute[Unit](HttpReads.Implicits.throwOnFailure(implicitly[HttpReads[Either[UpstreamErrorResponse, Unit]]]), implicitly[ExecutionContext])
}

object TeamsAndRepositoriesConnector {

  type ServiceName = String

  sealed trait TeamsAndRepositoriesError

  case class HTTPError(code: Int) extends TeamsAndRepositoriesError

  case class ConnectionError(exception: Throwable) extends TeamsAndRepositoriesError {
    override def toString: String = exception.getMessage
  }
}

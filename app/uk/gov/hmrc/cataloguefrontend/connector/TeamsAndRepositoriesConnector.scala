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
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalDateTime}
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
                          createdDate         : LocalDateTime,
                          lastActiveDate      : LocalDateTime,
                          isPrivate           : Boolean        = false,
                          repoType            : RepoType       = RepoType.Other,
                          digitalServiceName  : Option[String] = None,
                          owningTeams         : Seq[String]    = Nil,
                          language            : Option[String],
                          isArchived          : Boolean,
                          defaultBranch       : String,
                          isDeprecated        : Boolean        = false,
                          teamNames           : Seq[String]    = Nil,
                          jenkinsURL          : Option[String] = None
                        )

object GitRepository {
  val apiFormat: OFormat[GitRepository] = {
    implicit val rtf = RepoType.format

    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[LocalDateTime]
    ~ (__ \ "lastActiveDate"    ).format[LocalDateTime]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "isArchived"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"     ).format[String]
    ~ (__ \ "isDeprecated"      ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"         ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "jenkinsUrl"       ).formatNullable[String]
    ) (apply, unlift(unapply))
  }
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
class TeamsAndRepositoriesConnector @Inject()(http: HttpClient, servicesConfig: ServicesConfig)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._
  import TeamsAndRepositoriesConnector._

  private val logger = Logger(getClass)

  private val teamsAndServicesBaseUrl: String =
    servicesConfig.baseUrl("teams-and-repositories")

  private implicit val tf   = Team.format
  private implicit val tnf  = TeamName.format
  private implicit val jf   = Json.format[JenkinsLink]
  private implicit val ghrf = GitRepository.apiFormat // v2 model

  def lookupLink(service: String)(implicit hc: HeaderCarrier): Future[Option[Link]] = {
    val url = url"$teamsAndServicesBaseUrl/api/jenkins-url/$service"
    http
      .GET[Option[JenkinsLink]](url)
      .map(_.map(l => Link(l.service, "Build", l.jenkinsURL)))
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          None
      }
  }

  def allTeams(implicit hc: HeaderCarrier): Future[Seq[Team]] =
    http.GET[Seq[Team]](url"$teamsAndServicesBaseUrl/api/v2/teams")

  def repositoriesForTeam(teamName: TeamName, includeArchived: Option[Boolean] = None)(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] = {
    val url = url"$teamsAndServicesBaseUrl/api/v2/repositories?team=${teamName.asString}&archived=$includeArchived"

    http
      .GET[Seq[GitRepository]](url)
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Nil
      }
  }

  def allRepositories(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    http.GET[Seq[GitRepository]](url"$teamsAndServicesBaseUrl/api/v2/repositories")

  def archivedRepositories(implicit hc: HeaderCarrier): Future[Seq[GitRepository]] =
    http.GET[Seq[GitRepository]](url"$teamsAndServicesBaseUrl/api/v2/repositories?archived=true")

  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[GitRepository]] = {
    for {
      repo    <- http.GET[Option[GitRepository]] (url"$teamsAndServicesBaseUrl/api/v2/repositories/$name")
      jenkins <- lookupLink(name)
      withLink = repo.map(_.copy(jenkinsURL = jenkins.map(_.url)))
    } yield withLink
  }

  def allTeamsByService()(implicit hc: HeaderCarrier): Future[Map[ServiceName, Seq[TeamName]]] =
    for {
      repos <- http.GET[Seq[GitRepository]](url"$teamsAndServicesBaseUrl/api/v2/repositories")
      teamsByService = repos.map(r => r.name -> r.teamNames.map(TeamName.apply)).toMap
    } yield teamsByService

}

object TeamsAndRepositoriesConnector {

  type ServiceName = String

  sealed trait TeamsAndRepositoriesError

  case class HTTPError(code: Int) extends TeamsAndRepositoriesError

  case class ConnectionError(exception: Throwable) extends TeamsAndRepositoriesError {
    override def toString: String = exception.getMessage
  }
}

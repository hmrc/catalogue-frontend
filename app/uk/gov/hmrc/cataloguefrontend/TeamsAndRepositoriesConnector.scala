/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.net.URLEncoder
import java.time.LocalDateTime

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

//import uk.gov.hmrc.cataloguefrontend.JavaDateTimeJsonFormatter._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object RepoType extends Enumeration {

  type RepoType = Value

  val Service, Library, Other = Value

  implicit val repoType = new Reads[RepoType] {
    override def reads(json: JsValue): JsResult[RepoType] = json match {
      case JsString(s) => JsSuccess(RepoType.withName(s))
    }
  }

}

case class Link(name: String, displayName: String, url: String)

case class Environment(name: String, services: Seq[Link])

case class RepositoryDetails(name: String,
                             description: String,
                             createdAt: LocalDateTime,
                             lastActive: LocalDateTime,
                             teamNames: Seq[String],
                             githubUrls: Seq[Link],
                             ci: Seq[Link],
                             environments: Option[Seq[Environment]],
                             repoType: RepoType.RepoType)

case class RepositoryDisplayDetails(name:String, createdAt: LocalDateTime, lastUpdatedAt: LocalDateTime, repoType : RepoType.RepoType)
object RepositoryDisplayDetails {
  implicit val repoDetailsFormat = Json.format[RepositoryDisplayDetails]
}

case class Team(name:String, firstActiveDate: Option[LocalDateTime], lastActiveDate: Option[LocalDateTime], firstServiceCreationDate : Option[LocalDateTime], repos: Option[Map[String, Seq[String]]])
object Team {
  implicit val format = Json.format[Team]
}


trait TeamsAndRepositoriesConnector extends ServicesConfig {
  type ServiceName = String
  type TeamName = String

  val http: HttpGet with HttpPost

  def teamsAndServicesBaseUrl: String

  implicit val linkFormats = Json.format[Link]
  implicit val environmentsFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  val CacheTimestampHeaderName = "X-Cache-Timestamp"
  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def allTeams(implicit hc: HeaderCarrier): Future[Timestamped[Seq[Team]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/teams").map {
      timestamp[Seq[Team]]
    }
  }

  def teamInfo(teamName: String)(implicit hc: HeaderCarrier): Future[Option[Timestamped[Team]]] = {
    val url = teamsAndServicesBaseUrl + s"/api/teams_with_details/${URLEncoder.encode(teamName, "UTF-8")}"

    http.GET[HttpResponse](url)
      .map { response =>
        response.status match {
          case 404 => None
          case 200 => Some(timestamp[Team](response))
        }
      }.recover {
      case ex =>
        Logger.error(s"An error occurred getting teamInfo when connecting to $url: ${ex.getMessage}", ex)
        None
    }
  }

  def allRepositories(implicit hc: HeaderCarrier): Future[Timestamped[Seq[RepositoryDisplayDetails]]] =
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories").map {
      timestamp[Seq[RepositoryDisplayDetails]]
    }

  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[Timestamped[RepositoryDetails]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories/$name").map { response =>
      response.status match {
        case 404 => None
        case 200 => Some(timestamp[RepositoryDetails](response))
      }
    }
  }

  def teamsByService(serviceNames: Seq[String])(implicit hc: HeaderCarrier): Future[Timestamped[Map[ServiceName, Seq[TeamName]]]] = {
    http.POST[Seq[String], HttpResponse](teamsAndServicesBaseUrl + s"/api/services?teamDetails=true", serviceNames).map {
      timestamp[Map[ServiceName, Seq[TeamName]]]
    }
  }

  def allTeamsByService()(implicit hc: HeaderCarrier): Future[Timestamped[Map[ServiceName, Seq[TeamName]]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/services?teamDetails=true").map {
      timestamp[Map[ServiceName, Seq[TeamName]]]
    }
  }

  def timestamp[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): Timestamped[T] =
    Timestamped.fromStringInstant(
      r.json.as[T],
      r.header(CacheTimestampHeaderName))

  def toCachedItemOption[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): Option[Timestamped[T]] =
    r.status match {
      case 404 => None
      case 200 => Some(timestamp(r))
    }
}

object TeamsAndRepositoriesConnector extends TeamsAndRepositoriesConnector {
  override val http = WSHttp

  override def teamsAndServicesBaseUrl: String = baseUrl("teams-and-services")
}

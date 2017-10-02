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
import javax.inject.{Inject, Singleton}

import play.api.libs.json._
import play.api.{Configuration, Logger, Environment => PlayEnvironment}
import uk.gov.hmrc.cataloguefrontend.DigitalService.DigitalServiceRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object RepoType extends Enumeration {

  type RepoType = Value

  val Service, Library, Prototype, Other = Value

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

case class DigitalService(name: String, lastUpdatedAt: Long, repositories: Seq[DigitalServiceRepository])

object DigitalService {
  case class DigitalServiceRepository(name:String, createdAt: LocalDateTime, lastUpdatedAt: LocalDateTime, repoType : RepoType.RepoType, teamNames: Seq[String])

  implicit val repoDetailsFormat = Json.format[DigitalServiceRepository]


  implicit val digitalServiceFormat = Json.format[DigitalService]
}

@Singleton
class TeamsAndRepositoriesConnector @Inject()(http : HttpClient, override val runModeConfiguration:Configuration, environment : PlayEnvironment) extends ServicesConfig {
  type ServiceName = String
  type TeamName = String

  import TeamsAndRepositoriesConnector._

  override protected def mode = environment.mode

  def teamsAndServicesBaseUrl: String = baseUrl("teams-and-services")



  implicit val linkFormats = Json.format[Link]
  implicit val environmentsFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  val CacheTimestampHeaderName = "X-Cache-Timestamp"
  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def allTeams(implicit hc: HeaderCarrier): Future[Seq[Team]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/teams").map {
      toJson[Seq[Team]]
    }
  }

  def allDigitalServices(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/digital-services").map {
      toJson[Seq[String]]
    }
  }

  def teamInfo(teamName: String)(implicit hc: HeaderCarrier): Future[Option[Team]] = {
    val url = teamsAndServicesBaseUrl + s"/api/teams_with_details/${URLEncoder.encode(teamName, "UTF-8")}"

    http.GET[HttpResponse](url)
      .map { response =>
        response.status match {
          case 404 => None
          case 200 => Some(toJson[Team](response))
        }
      }.recover {
      case ex =>
        Logger.error(s"An error occurred getting teamInfo when connecting to $url: ${ex.getMessage}", ex)
        None
    }
  }

  def teamsWithRepositories()(implicit hc: HeaderCarrier): Future[Seq[Team]] = {
    val url = teamsAndServicesBaseUrl + s"/api/teams_with_repositories"

    http.GET[HttpResponse](url)
      .map { response =>
        response.status match {
          case 404 => Nil
          case 200 => response.json.as[Seq[Team]]
        }
      }.recover {
      case ex =>
        Logger.error(s"An error occurred getting teamsWithRepositories when connecting to $url: ${ex.getMessage}", ex)
        Seq.empty[Team]
    }
  }

  def digitalServiceInfo(digitalServiceName: String)(implicit hc: HeaderCarrier): Future[Either[TeamsAndRepositoriesError, DigitalService]] = {
    val url = teamsAndServicesBaseUrl + s"/api/digital-services/${URLEncoder.encode(digitalServiceName, "UTF-8")}"

    http.GET[HttpResponse](url)
      .map { response =>
        response.status match {
          case 200 => Right(toJson[DigitalService](response))
          case statusCode => Left(HTTPError(statusCode))
        }
      }.recover {
      case ex =>
        Logger.error(s"An error occurred getting digitalServiceInfo when connecting to $url: ${ex.getMessage}", ex)
        Left(ConnectionError(ex))
    }
  }

  def allRepositories(implicit hc: HeaderCarrier): Future[Seq[RepositoryDisplayDetails]] =
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories").map {
      toJson[Seq[RepositoryDisplayDetails]]
    }

  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[RepositoryDetails]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories/$name").map { response =>
      response.status match {
        case 404 => None
        case 200 => Some(toJson[RepositoryDetails](response))
      }
    }
  }

  def teamsByService(serviceNames: Seq[String])(implicit hc: HeaderCarrier): Future[Map[ServiceName, Seq[TeamName]]] = {
    http.POST[Seq[String], HttpResponse](teamsAndServicesBaseUrl + s"/api/services?teamDetails=true", serviceNames).map {
      toJson[Map[ServiceName, Seq[TeamName]]]
    }
  }

  def allTeamsByService()(implicit hc: HeaderCarrier): Future[Map[ServiceName, Seq[TeamName]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/services?teamDetails=true").map {
      toJson[Map[ServiceName, Seq[TeamName]]]
    }
  }

  def toJson[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): T =
    r.json.as[T]


}

object TeamsAndRepositoriesConnector {

  type ServiceName = String
  type TeamName = String

  sealed trait TeamsAndRepositoriesError

  case class HTTPError(code: Int) extends TeamsAndRepositoriesError

  case class ConnectionError(exception: Throwable) extends TeamsAndRepositoriesError {
    override def toString: String = exception.getMessage
  }
}

/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}

import scala.concurrent.Future

object RepoType extends Enumeration {

  type RepoType = Value

  val Deployable, Library, Other = Value

  implicit val repoType = new Format[RepoType] {
    override def reads(json: JsValue): JsResult[RepoType] = json match {
      case JsString(s) => JsSuccess(RepoType.withName(s))
    }

    override def writes(o: RepoType): JsValue = ???
  }

}

case class Link(name: String, displayName: String, url: String)

case class Environment(name: String, services: Seq[Link])

case class RepositoryDetails(name: String, teamNames: Seq[String], githubUrls: Seq[Link], ci: Seq[Link], environments: Option[Seq[Environment]], repoType: RepoType.RepoType)

trait TeamsAndServicesConnector extends ServicesConfig {
  val http: HttpGet
  val teamsAndServicesBaseUrl: String

  implicit val linkFormats = Json.format[Link]
  implicit val environmentsFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  val CacheTimestampHeaderName = "X-Cache-Timestamp"
  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def allTeams(implicit hc: HeaderCarrier): Future[CachedList[String]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/teams").map {
      toCachedList[String]
    }
  }

  def teamInfo(teamName: String)(implicit hc: HeaderCarrier): Future[Option[CachedItem[Map[String, List[String]]]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/teams/${URLEncoder.encode(teamName, "UTF-8")}")
      .map {
      toCachedItem[Map[String, List[String]]]
    }
  }

  def allServiceNames(implicit hc: HeaderCarrier): Future[CachedList[String]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/services").map {
      toCachedList[String]
    }
  }

  def allLibraryNames(implicit hc: HeaderCarrier): Future[CachedList[String]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/libraries").map {
      toCachedList[String]
    }
  }


  def repositoryDetails(name: String)(implicit hc: HeaderCarrier): Future[Option[CachedItem[RepositoryDetails]]] = {
    http.GET[HttpResponse](teamsAndServicesBaseUrl + s"/api/repositories/$name").map {
      toCachedItem[RepositoryDetails]
    }
  }

  def toCachedItem[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): Option[CachedItem[T]] = {
    r.status match {
      case 404 => None
      case 200 => Some(new CachedItem(
        r.json.as[T],
        r.header(CacheTimestampHeaderName).getOrElse("(None)")))
    }
  }

  def toCachedList[T](r: HttpResponse)(implicit fjs: play.api.libs.json.Reads[T]): CachedList[T] = {
    new CachedList(
      r.json.as[Seq[T]],
      r.header(CacheTimestampHeaderName).getOrElse("(None)"))
  }
}

object TeamsAndServicesConnector extends TeamsAndServicesConnector {
  override val http = WSHttp
  override lazy val teamsAndServicesBaseUrl: String = baseUrl("teams-and-services")
}

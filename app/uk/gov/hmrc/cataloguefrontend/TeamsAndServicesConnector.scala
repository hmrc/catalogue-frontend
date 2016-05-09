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

import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

case class Team(teamName: String, repositories: List[Repository])

case class Repository(name: String, url: String, isMicroservice: Boolean)

case class Link(name: String, url: String)

case class Service(name: String, githubUrl: Link, ci: List[Link])

object Link {
  implicit val formats = Json.format[Link]
}

object Service {
  implicit val formats = Json.format[Service]
}

object Repository {
  implicit val formats = Json.format[Repository]
}

object Team {
  implicit val formats = Json.format[Team]
}

trait TeamsAndServicesConnector extends ServicesConfig {
  val http: HttpGet
  val teamsAndServicesBaseUrl: String

  def allTeams(implicit hc: HeaderCarrier): Future[CachedList[Team]] = {
    http.GET[CachedList[Team]](teamsAndServicesBaseUrl + s"/api/teams")
  }

  def teamServices(teamName : String)(implicit hc: HeaderCarrier): Future[CachedList[Service]] = {
    http.GET[CachedList[Service]](teamsAndServicesBaseUrl + s"/api/${URLEncoder.encode(teamName,"UTF-8")}/services")
  }
}

object TeamsAndServicesConnector extends TeamsAndServicesConnector {
  override val http = WSHttp
  override lazy val teamsAndServicesBaseUrl: String = baseUrl("teams-and-services")
}

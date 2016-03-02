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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

case class Team(teamName: String, repositories: List[Repository])

case class Repository(name: String, url: String, isMicroservice:Boolean)

object Repository {
  implicit val formats = Json.format[Repository]
}

object Team {
  implicit val formats = Json.format[Team]
}

trait CatalogueConnector extends ServicesConfig {
  val http: HttpGet
  val catalogUrl: String

  def allTeams(implicit hc: HeaderCarrier): Future[List[Team]] = {
    http.GET[List[Team]](catalogUrl + s"/catalogue/api/teams")
  }
}



object CatalogueConnector extends CatalogueConnector {
  override val http = WSHttp
  override lazy val catalogUrl: String = baseUrl("catalogue")
}

/*
 * Copyright 2024 HM Revenue & Customs
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

import cats.implicits.toTraverseOps
import play.api.Logging
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model.{Log, TeamName, UserLog}
import uk.gov.hmrc.cataloguefrontend.search.{SearchController, SearchTerm}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatopsAuditingConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
, userManagementConnector: UserManagementConnector
, searchController: SearchController
)(implicit
  ec: ExecutionContext
) extends Logging {

  import HttpReads.Implicits._

  private val baseUrl = servicesConfig.baseUrl("platops-auditing")

  def userLogs(userName: String)(implicit hc: HeaderCarrier): Future[Option[UserLog]] = {
    val url: URL = url"$baseUrl/getUserLog?userName=${userName}"

    implicit val lr: Reads[UserLog] = UserLog.userLogFormat

    httpClientV2
      .get(url)
      .execute[Option[UserLog]]
  }
  
  def teamLogs(teamName: TeamName)(implicit headerCarrier: HeaderCarrier): Future[Seq[Log]] =
    for {
      team            <- userManagementConnector.getTeam(teamName)
      members         =  team.map(_.members).getOrElse(Seq.empty)
      teamUserLogs    <- members.traverse(member => userLogs(member.username))
      teamLogs        =  teamUserLogs.flatMap {
        case Some(memberLogs) => memberLogs.logs
        case None             => Seq.empty[Log]
      }
    } yield (teamLogs)
  
  def teamsLogs(teams: Seq[TeamName])(implicit headerCarrier: HeaderCarrier): Future[Seq[Log]] =
    for {
      manyLogs <- teams.traverse(team => teamLogs(team))
      teamsLogs = manyLogs.flatten
    } yield (teamsLogs)
    
  
  def globalLogs()(implicit hc: HeaderCarrier): Future[Option[Seq[Log]]] = {
    val url: URL = url"$baseUrl/getGlobalLogs"

    implicit val lr: Reads[Log] = Log.format

    httpClientV2
      .get(url)
      .execute[Option[Seq[Log]]]
  }
  
  
}

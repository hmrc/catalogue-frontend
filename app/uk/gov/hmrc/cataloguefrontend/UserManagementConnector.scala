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

import java.time.LocalDateTime

import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future



trait UserManagementConnector extends ServicesConfig {
  val http: HttpGet
  val userManagementBaseUrl: String

  import UserManagementConnector._

  implicit val teamMemberReads = Json.reads[TeamMember]

  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def getTeamMembers(team: String)(implicit hc: HeaderCarrier): Future[Either[ConnectorError, Seq[TeamMember]]] = {

    val url = s"$userManagementBaseUrl/v1/organisations/mdtp/teams/$team/members"

    http.GET[HttpResponse](url).map { response =>
      response.status match {
        case 200 => extractMembers(response)
        case httpCode => Left(HTTPError(httpCode))
      }
    }.recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $userManagementBaseUrl: ${ex.getMessage}", ex)
        Left(ConnectionError(ex))
    }
  }

  private def extractMembers(response: HttpResponse): Either[ConnectorError, Seq[TeamMember]] = {
    (response.json \\ "members")
      .headOption
      .map(js => Right(js.as[Seq[TeamMember]]))
      .getOrElse(Left(NoMembersField))
  }

}

object UserManagementConnector extends UserManagementConnector {
  override val http = WSHttp
  override lazy val userManagementBaseUrl: String = baseUrl("user-management")

  sealed trait ConnectorError
  case object NoMembersField extends ConnectorError
  case class HTTPError(code: Int) extends ConnectorError
  case class ConnectionError(exception: Throwable) extends ConnectorError

  case class TeamMember(displayName: Option[String],
                         familyName: Option[String],
                         givenName: Option[String],
                         primaryEmail: Option[String],
                         serviceOwnerFor: Option[Seq[String]],
                         username: Option[String])
}

//GET : http://example.com//v1/organisations/mdtp/teams/DDCOps/members
//Headers :
//Token : None
//requester: None

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

import play.api.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.cataloguefrontend.config.Constant

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object PrCommenterConnector {
  import uk.gov.hmrc.cataloguefrontend.connector.model.Version
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  case class Report(
    name     : String,
    teamNames: List[String],
    version  : Version,
    comments : Seq[Comment],
    created  : Instant
  ) {
    // Repos with joint ownership of 8+ teams, defines the repo as shared
    def isShared: Boolean = teamNames.length >= Constant.sharedRepoTeamsCutOff

    def teamNameDisplay: String = if (isShared) s"Shared by ${teamNames.length} teams" else teamNames.sortBy(_.toLowerCase).mkString(",")
  }

  case class Comment(
    message    : String,
    commentType: String
  )

  private implicit val commentReads: Reads[Comment] =
    ( (__ \ "message"      ).read[String]
    ~ (__ \ "params" \ "id").read[String]
    )(Comment.apply _)

  val reportReads: Reads[Report] =
    ( (__ \ "name"     ).read[String]
    ~ (__ \ "teamNames").read[List[String]]
    ~ (__ \ "version"  ).read[Version](Version.format)
    ~ (__ \ "comments" ).read[Seq[Comment]]
    ~ (__ \ "created"  ).read[Instant]
    )(Report.apply _)
}

@Singleton
class PrCommenterConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) extends Logging {
  import HttpReads.Implicits._
  import PrCommenterConnector._

  private val baseUrl = servicesConfig.baseUrl("pr-commenter")

  private implicit val rf = PrCommenterConnector.reportReads

  def report(name: String)(implicit hc: HeaderCarrier): Future[Option[Report]] =
    httpClientV2
      .get(url"$baseUrl/pr-commenter/repositories/$name/report")
      .execute[Option[Report]]

  def search(name: Option[String], teamName: Option[String], commentType: Option[String])(implicit hc: HeaderCarrier): Future[Seq[Report]] =
    httpClientV2
      .get(url"$baseUrl/pr-commenter/reports?name=$name&teamName=$teamName&commentType=$commentType")
      .execute[Seq[Report]]
}

/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import play.api.Logger
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class HealthIndicatorsConnector @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private implicit val repositoryRatingReads: Reads[RepositoryRating] = RepositoryRating.reads

  private val healthIndicatorsBaseUrl: String = servicesConfig.baseUrl("health-indicators")

  def getHealthIndicators(repoName: String)(implicit hc: HeaderCarrier): Future[Option[RepositoryRating]] = {
    val url = s"$healthIndicatorsBaseUrl/health-indicators/repositories/$repoName"
    http
      .GET[Option[RepositoryRating]](url)
  }

  def getAllHealthIndicators()(implicit hc: HeaderCarrier): Future[Seq[RepositoryRating]] = {
    val url = s"$healthIndicatorsBaseUrl/health-indicators/repositories/?sort=desc"
    http
      .GET[Seq[RepositoryRating]](url)
  }
}
sealed trait RatingType

object RatingType {
  val format: Format[RatingType] = new Format[RatingType] {
    override def reads(json: JsValue): JsResult[RatingType] =
      json.validate[String].flatMap {
        case "ReadMe"        => JsSuccess(ReadMe)
        case "LeakDetection" => JsSuccess(LeakDetection)
        case "BobbyRule"     => JsSuccess(BobbyRule)
        case "BuildStability" => JsSuccess(BuildStability)
        case s               => JsError(s"Invalid RatingType: $s")
      }

    override def writes(o: RatingType): JsValue =
      o match {
        case ReadMe        => JsString("ReadMe")
        case LeakDetection => JsString("LeakDetection")
        case BobbyRule     => JsString("BobbyRule")
        case BuildStability => JsString("BuildStability")
        case s             => JsString(s"$s")
      }
  }
  case object ReadMe extends RatingType
  case object LeakDetection extends RatingType
  case object BobbyRule extends RatingType
  case object BuildStability extends RatingType
}

case class Score(points: Int, description: String, href: Option[String])

object Score {
  val reads: Reads[Score] =
    ((__ \ "points").read[Int]
      ~ (__ \ "description").read[String]
      ~ (__ \ "href").readNullable[String])(Score.apply _)
}

case class Rating(ratingType: RatingType, ratingScore: Int, breakdown: Seq[Score])

object Rating {
  val reads: Reads[Rating] = {
    implicit val sR: Reads[Score]       = Score.reads
    implicit val rtR: Reads[RatingType] = RatingType.format
    ((__ \ "ratingType").read[RatingType]
      ~ (__ \ "ratingScore").read[Int]
      ~ (__ \ "breakdown").read[Seq[Score]])(Rating.apply _)
  }
}

case class RepositoryRating(repositoryName: String, repositoryType: RepoType, repositoryScore: Int, ratings: Seq[Rating])

object RepositoryRating {
  val reads: Reads[RepositoryRating] = {
    implicit val sR: Reads[Rating]    = Rating.reads
    implicit val rtR: Reads[RepoType] = RepoType.format
    ((__ \ "repositoryName").read[String]
      ~ (__ \ "repositoryType").read[RepoType]
      ~ (__ \ "repositoryScore").read[Int]
      ~ (__ \ "ratings").read[Seq[Rating]])(RepositoryRating.apply _)
  }
}

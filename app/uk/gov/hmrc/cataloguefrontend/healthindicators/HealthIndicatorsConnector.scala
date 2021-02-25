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
import play.api.libs.json.{Reads, __}
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
  private val logger = Logger(getClass)

  private val healthIndicatorsBaseUrl: String = servicesConfig.baseUrl("health-indicators")

  def getHealthIndicators(repoName: String)(implicit hc: HeaderCarrier): Future[Option[RepositoryRating]] = {
    implicit val rrR: Reads[RepositoryRating] = RepositoryRating.reads
    val url                                   = s"$healthIndicatorsBaseUrl/health-indicators/repositories/$repoName"
    http
      .GET[Option[RepositoryRating]](url)
  }
}

case class Score(points: Int, description: String, href: Option[String])

object Score {
  val reads: Reads[Score] =
    ((__ \ "points").read[Int]
      ~ (__ \ "description").read[String]
      ~ (__ \ "href").readNullable[String])(Score.apply _)
}

case class Rating(ratingType: String, ratingScore: Int, breakdown: Seq[Score])

object Rating {
  val reads: Reads[Rating] = {
    implicit val sR: Reads[Score] = Score.reads
    ((__ \ "ratingType").read[String]
      ~ (__ \ "ratingScore").read[Int]
      ~ (__ \ "breakdown").read[Seq[Score]])(Rating.apply _)
  }
}

case class RepositoryRating(repositoryName: String, repositoryScore: Int, ratings: Option[Seq[Rating]])

object RepositoryRating {
  val reads: Reads[RepositoryRating] = {
    implicit val sR: Reads[Rating] = Rating.reads
    ((__ \ "repositoryName").read[String]
      ~ (__ \ "repositoryScore").read[Int]
      ~ (__ \ "ratings").readNullable[Seq[Rating]])(RepositoryRating.apply _)
  }
}

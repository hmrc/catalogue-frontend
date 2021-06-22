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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
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

  private implicit val indicatorReads: Reads[Indicator] = Indicator.reads

  private val healthIndicatorsBaseUrl: String = servicesConfig.baseUrl("health-indicators")

  def getIndicator(repoName: String)(implicit hc: HeaderCarrier): Future[Option[Indicator]] = {
    val url = s"$healthIndicatorsBaseUrl/health-indicators/repositories/$repoName"
    http
      .GET[Option[Indicator]](url)
  }

  def getAllIndicators(repoType: RepoType)(implicit hc: HeaderCarrier): Future[Seq[Indicator]] = {
    // AllTypes is represented on the backend by sending no RepoType parameter
    val repoTypeQueryP = if(repoType == RepoType.AllTypes) "" else "&repoType=" + repoType.asString
    val url = s"$healthIndicatorsBaseUrl/health-indicators/repositories/?sort=desc" ++ repoTypeQueryP
    http
      .GET[Seq[Indicator]](url)
  }
}
sealed trait MetricType

object MetricType {
  val format: Format[MetricType] = new Format[MetricType] {
    override def reads(json: JsValue): JsResult[MetricType] =
      json.validate[String].flatMap {
        case "read-me"         => JsSuccess(ReadMe)
        case "leak-detection"  => JsSuccess(LeakDetection)
        case "bobby-rule"      => JsSuccess(BobbyRule)
        case "build-stability" => JsSuccess(BuildStability)
        case "alert-config"    => JsSuccess(AlertConfig)
        case "open-pr"         => JsSuccess(OpenPR)
        case s                => JsError(s"Invalid MetricType: $s")
      }

    override def writes(o: MetricType): JsValue =
      o match {
        case ReadMe         => JsString("read-me")
        case LeakDetection  => JsString("leak-detection")
        case BobbyRule      => JsString("bobby-rule")
        case BuildStability => JsString("build-stability")
        case AlertConfig    => JsString("alert-config")
        case OpenPR         => JsString("open-pr")
        case s              => JsString(s"$s")
      }
  }
  case object ReadMe extends MetricType
  case object LeakDetection extends MetricType
  case object BobbyRule extends MetricType
  case object BuildStability extends MetricType
  case object AlertConfig extends MetricType
  case object OpenPR extends MetricType
}

case class Breakdown(points: Int, description: String, href: Option[String])

object Breakdown {
  val reads: Reads[Breakdown] =
    ((__ \ "points").read[Int]
      ~ (__ \ "description").read[String]
      ~ (__ \ "href").readNullable[String])(Breakdown.apply _)
}

case class WeightedMetric(metricType: MetricType, score: Int, breakdown: Seq[Breakdown])

object WeightedMetric {
  val reads: Reads[WeightedMetric] = {
    implicit val sR: Reads[Breakdown]   = Breakdown.reads
    implicit val rtR: Reads[MetricType] = MetricType.format
    ((__ \ "metricType").read[MetricType]
      ~ (__ \ "score").read[Int]
      ~ (__ \ "breakdown").read[Seq[Breakdown]])(WeightedMetric.apply _)
  }
}

case class Indicator(repoName: String, repoType: RepoType, overallScore: Int, weightedMetrics: Seq[WeightedMetric])

object Indicator {
  val reads: Reads[Indicator] = {
    implicit val sR: Reads[WeightedMetric]   = WeightedMetric.reads
    implicit val rtR: Reads[RepoType]        = RepoType.format
    ((__ \ "repoName").read[String]
      ~ (__ \ "repoType").read[RepoType]
      ~ (__ \ "overallScore").read[Int]
      ~ (__ \ "weightedMetrics").read[Seq[WeightedMetric]])(Indicator.apply _)
  }
}

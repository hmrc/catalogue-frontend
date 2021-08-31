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

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class HealthIndicatorsConnector @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private implicit val indicatorReads: Reads[Indicator] = Indicator.reads
  private implicit val historicIndicatorReads: Reads[HistoricIndicatorAPI] = HistoricIndicatorAPI.format
  private implicit val averageReads: Reads[AveragePlatformScore] = AveragePlatformScore.format

  private val healthIndicatorsBaseUrl: String = servicesConfig.baseUrl("health-indicators")

  def getIndicator(repoName: String)(implicit hc: HeaderCarrier): Future[Option[Indicator]] = {
    val url = s"$healthIndicatorsBaseUrl/health-indicators/indicators/$repoName"
    http
      .GET[Option[Indicator]](url)
  }

  def getAllIndicators(repoType: RepoType)(implicit hc: HeaderCarrier): Future[Seq[Indicator]] = {
    // AllTypes is represented on the backend by sending no RepoType parameter
    val repoTypeQueryP = if (repoType != RepoType.AllTypes) Seq("repoType" -> repoType.asString) else Seq.empty
    val allQueryParams = Seq("sort" -> "desc") ++ repoTypeQueryP
    http
      .GET[Seq[Indicator]](s"$healthIndicatorsBaseUrl/health-indicators/indicators", allQueryParams)
  }

  def getHistoricIndicators(repoName: String)(implicit hc: HeaderCarrier): Future[Option[HistoricIndicatorAPI]] = {
    val url = s"$healthIndicatorsBaseUrl/health-indicators/history/$repoName"
    http
      .GET[Option[HistoricIndicatorAPI]](url)
  }

  def getAveragePlatformScore(implicit hc: HeaderCarrier): Future[Option[AveragePlatformScore]] = {
    val url = s"$healthIndicatorsBaseUrl/health-indicators/platform-average"
    http
      .GET[Option[AveragePlatformScore]](url)
  }
}
sealed trait MetricType

object MetricType {

  val reads: Reads[MetricType] = new Reads[MetricType] {
    override def reads(json: JsValue): JsResult[MetricType] =
      json.validate[String].flatMap {
        case "github"          => JsSuccess(GitHub)
        case "leak-detection"  => JsSuccess(LeakDetection)
        case "bobby-rule"      => JsSuccess(BobbyRule)
        case "build-stability" => JsSuccess(BuildStability)
        case "alert-config"    => JsSuccess(AlertConfig)
        case s                 => JsError(s"Invalid MetricType: $s")
      }
  }

  case object GitHub extends MetricType
  case object LeakDetection extends MetricType
  case object BobbyRule extends MetricType
  case object BuildStability extends MetricType
  case object AlertConfig extends MetricType
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
    implicit val rtR: Reads[MetricType] = MetricType.reads
    ((__ \ "metricType").read[MetricType]
      ~ (__ \ "score").read[Int]
      ~ (__ \ "breakdown").read[Seq[Breakdown]])(WeightedMetric.apply _)
  }
}

case class Indicator(repoName: String, repoType: RepoType, overallScore: Int, weightedMetrics: Seq[WeightedMetric])

object Indicator {
  val reads: Reads[Indicator] = {
    implicit val sR: Reads[WeightedMetric] = WeightedMetric.reads
    implicit val rtR: Reads[RepoType]      = RepoType.format
    ((__ \ "repoName").read[String]
      ~ (__ \ "repoType").read[RepoType]
      ~ (__ \ "overallScore").read[Int]
      ~ (__ \ "weightedMetrics").read[Seq[WeightedMetric]])(Indicator.apply _)
  }
}


case class DataPoint(timestamp: Instant, overallScore: Int)

object DataPoint {
  val format: OFormat[DataPoint] = {
    implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    ((__ \ "timestamp").format[Instant]
      ~ (__ \ "overallScore").format[Int])(DataPoint.apply, unlift(DataPoint.unapply))
  }
}

case class HistoricIndicatorAPI(repoName: String, dataPoints: Seq[DataPoint])

object HistoricIndicatorAPI {
  val format: OFormat[HistoricIndicatorAPI] = {
    implicit val dataFormat: Format[DataPoint] = DataPoint.format
    ((__ \ "repoName").format[String]
      ~ (__ \ "dataPoints").format[Seq[DataPoint]])(HistoricIndicatorAPI.apply, unlift(HistoricIndicatorAPI.unapply))
  }
}

case class AveragePlatformScore(timestamp: Instant, averageScore: Int)

object AveragePlatformScore {
  val format: Format[AveragePlatformScore] = {
    implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    ((__ \ "timestamp").format[Instant]
      ~ (__ \ "averageScore").format[Int])(AveragePlatformScore.apply, unlift(AveragePlatformScore.unapply))
  }
}


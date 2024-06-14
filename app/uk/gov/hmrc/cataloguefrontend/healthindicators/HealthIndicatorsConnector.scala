/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class HealthIndicatorsConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private implicit val indicatorReads        : Reads[Indicator]            = Indicator.reads
  private implicit val historicIndicatorReads: Reads[HistoricIndicatorAPI] = HistoricIndicatorAPI.format
  private implicit val averageReads          : Reads[AveragePlatformScore] = AveragePlatformScore.format

  private val healthIndicatorsBaseUrl: String =
    servicesConfig.baseUrl("health-indicators")

  def getIndicator(repoName: String)(implicit hc: HeaderCarrier): Future[Option[Indicator]] =
    httpClientV2
      .get(url"$healthIndicatorsBaseUrl/health-indicators/indicators/$repoName")
      .execute[Option[Indicator]]

  def getIndicators(repoType: Option[RepoType])(implicit hc: HeaderCarrier): Future[Seq[Indicator]] =
    httpClientV2
      .get(url"$healthIndicatorsBaseUrl/health-indicators/indicators?sort=desc&repoType=$repoType")
      .execute[Seq[Indicator]]

  def getHistoricIndicators(repoName: String)(implicit hc: HeaderCarrier): Future[Option[HistoricIndicatorAPI]] =
    httpClientV2
      .get(url"$healthIndicatorsBaseUrl/health-indicators/history/$repoName")
      .execute[Option[HistoricIndicatorAPI]]

  def getAveragePlatformScore(implicit hc: HeaderCarrier): Future[Option[AveragePlatformScore]] =
    httpClientV2
      .get(url"$healthIndicatorsBaseUrl/health-indicators/platform-average")
      .execute[Option[AveragePlatformScore]]
}

enum MetricType:
  case GitHub         extends MetricType
  case LeakDetection  extends MetricType
  case BuildStability extends MetricType
  case AlertConfig    extends MetricType

object MetricType {
  val reads: Reads[MetricType] = new Reads[MetricType] {
    override def reads(json: JsValue): JsResult[MetricType] =
      json.validate[String].flatMap {
        case "github"          => JsSuccess(GitHub)
        case "leak-detection"  => JsSuccess(LeakDetection)
        case "build-stability" => JsSuccess(BuildStability)
        case "alert-config"    => JsSuccess(AlertConfig)
        case s                 => JsError(s"Invalid MetricType: $s")
      }
  }
}

case class Breakdown(
  points     : Int,
  description: String,
  href       : Option[String]
)

object Breakdown {
  val reads: Reads[Breakdown] =
    ( (__ \ "points"     ).read[Int]
    ~ (__ \ "description").read[String]
    ~ (__ \ "href"       ).readNullable[String]
    )(Breakdown.apply)
}

case class WeightedMetric(
  metricType: MetricType,
  score     : Int,
  breakdown : Seq[Breakdown]
)

object WeightedMetric {
  val reads: Reads[WeightedMetric] = {
    implicit val sR: Reads[Breakdown]   = Breakdown.reads
    implicit val rtR: Reads[MetricType] = MetricType.reads
    ( (__ \ "metricType").read[MetricType]
    ~ (__ \ "score"     ).read[Int]
    ~ (__ \ "breakdown" ).read[Seq[Breakdown]]
    )(WeightedMetric.apply)
  }
}

case class Indicator(
  repoName       : String,
  repoType       : RepoType,
  overallScore   : Int,
  weightedMetrics: Seq[WeightedMetric]
)

object Indicator {
  val reads: Reads[Indicator] = {
    implicit val sR: Reads[WeightedMetric] = WeightedMetric.reads
    implicit val rtR: Reads[RepoType]      = RepoType.format
    ( (__ \ "repoName"       ).read[String]
    ~ (__ \ "repoType"       ).read[RepoType]
    ~ (__ \ "overallScore"   ).read[Int]
    ~ (__ \ "weightedMetrics").read[Seq[WeightedMetric]]
    )(Indicator.apply)
  }
}


case class DataPoint(
  timestamp   : Instant,
  overallScore: Int
)

object DataPoint {
  val format: OFormat[DataPoint] = {
    implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    ( (__ \ "timestamp"   ).format[Instant]
    ~ (__ \ "overallScore").format[Int]
    )(DataPoint.apply, r => Tuple.fromProductTyped(r))
  }
}

case class HistoricIndicatorAPI(repoName: String, dataPoints: Seq[DataPoint])

object HistoricIndicatorAPI {
  val format: OFormat[HistoricIndicatorAPI] = {
    implicit val dataFormat: Format[DataPoint] = DataPoint.format
    ( (__ \ "repoName"  ).format[String]
    ~ (__ \ "dataPoints").format[Seq[DataPoint]
    ])(HistoricIndicatorAPI.apply, r => Tuple.fromProductTyped(r))
  }
}

case class AveragePlatformScore(
  timestamp   : Instant,
  averageScore: Int
)

object AveragePlatformScore {
  val format: Format[AveragePlatformScore] = {
    implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
    ( (__ \ "timestamp"   ).format[Instant]
    ~ (__ \ "averageScore").format[Int]
    )(AveragePlatformScore.apply, r => Tuple.fromProductTyped(r))
  }
}

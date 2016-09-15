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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.Logger
import play.api.libs.json.Json
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}

import scala.concurrent.Future
import scala.xml.Elem

case class MedianDataPoint(median: Int)

case class FprDataPoint(period: String, from: LocalDate, to: LocalDate, leadTime: Option[MedianDataPoint], interval: Option[MedianDataPoint])

trait IndicatorsConnector extends ServicesConfig {
  val http: HttpGet
  val indicatorsBaseUrl: String

  import JavaDateTimeJsonFormatter._

  implicit val medianFormats = Json.reads[MedianDataPoint]
  implicit val fprFormats = Json.reads[FprDataPoint]
  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }

  def fprForService(name: String)(implicit hc: HeaderCarrier): Future[Option[Seq[FprDataPoint]]] = {
    val url = indicatorsBaseUrl + s"/service/$name/fpr"
    http.GET[HttpResponse](url).map { r =>
      r.status match {
        case 404 => Some(Seq())
        case 200 => Some(r.json.as[Seq[FprDataPoint]])
      }
    }.recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
        None
    }
  }
}

object IndicatorsConnector extends IndicatorsConnector {
  override lazy val indicatorsBaseUrl: String = baseUrl("indicators") + "/api/indicators"
  override val http = WSHttp
}

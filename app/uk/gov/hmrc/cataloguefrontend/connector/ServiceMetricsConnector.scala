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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceMetricsConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ec: ExecutionContext
):
  import ServiceMetricsConnector._
  import HttpReads.Implicits._

  private val serviceMetricsBaseUrl: String =
    servicesConfig.baseUrl("service-metrics")

  def logMetrics(service: ServiceName)(using HeaderCarrier): Future[Seq[LogMetric]] =
    given Reads[LogMetric] = LogMetric.reads
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/${service.asString}/log-metrics")
      .execute[Seq[LogMetric]]

  def getCollections(service: ServiceName)(using HeaderCarrier): Future[Seq[MongoCollectionSize]] =
    given Reads[MongoCollectionSize] = MongoCollectionSize.reads
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/${service.asString}/collections")
      .execute[Seq[MongoCollectionSize]]

object ServiceMetricsConnector:
  case class LogMetric(
    id          : String
  , displayName : String
  , environments: Map[Environment, EnvironmentResult]
  )

  case class EnvironmentResult(
    kibanaLink: String
  , count     : Int
  )

  object LogMetric:
    val reads: Reads[LogMetric] =
      given Reads[EnvironmentResult] =
        ( (__ \ "kibanaLink").read[String]
        ~ (__ \ "count"     ).read[Int]
        )(EnvironmentResult.apply)

      ( (__ \ "id"          ).read[String]
      ~ (__ \ "displayName" ).read[String]
      ~ (__ \ "environments").read[Map[Environment, EnvironmentResult]]
      )(apply)

  case class MongoCollectionSize(
    database   : String,
    collection : String,
    sizeBytes  : BigDecimal,
    date       : LocalDate,
    environment: Environment,
    service    : Option[String],
  )

  object MongoCollectionSize:
    val reads: Reads[MongoCollectionSize] =
      ( (__ \ "database"   ).read[String]
      ~ (__ \ "collection" ).read[String]
      ~ (__ \ "sizeBytes"  ).read[BigDecimal]
      ~ (__ \ "date"       ).read[LocalDate]
      ~ (__ \ "environment").read[Environment]
      ~ (__ \ "service"    ).readNullable[String]
      )(apply)

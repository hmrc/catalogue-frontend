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
)(implicit
  ec: ExecutionContext
) {
  import ServiceMetricsConnector._
  import HttpReads.Implicits._

  private val serviceMetricsBaseUrl: String =
    servicesConfig.baseUrl("service-metrics")

  def nonPerformantQueriesForService(service: ServiceName)(implicit hc: HeaderCarrier): Future[Seq[NonPerformantQueries]] = {
    implicit val npqr = NonPerformantQueries.reads
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/${service.asString}/non-performant-queries")
      .execute[Seq[NonPerformantQueries]]
  }

  def getCollections(service: ServiceName)(implicit hc: HeaderCarrier): Future[Seq[MongoCollectionSize]] = {
    implicit val mcsR = MongoCollectionSize.reads
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/${service.asString}/collections")
      .execute[Seq[MongoCollectionSize]]
  }
}

object ServiceMetricsConnector {
  case class NonPerformantQueries(
    service    : ServiceName,
    environment: Environment,
    queryTypes : Seq[String],
  )

  object NonPerformantQueries{
    val reads: Reads[NonPerformantQueries] =
      ( (__ \ "service"    ).read[ServiceName](ServiceName.format)
      ~ (__ \ "environment").read[Environment](Environment.format)
      ~ (__ \ "queryTypes" ).read[Seq[String]]
      )(NonPerformantQueries.apply)
  }

  case class MongoCollectionSize(
    database   : String,
    collection : String,
    sizeBytes  : BigDecimal,
    date       : LocalDate,
    environment: Environment,
    service    : Option[String],
  )

  object MongoCollectionSize {
    val reads: Reads[MongoCollectionSize] = {
      implicit val envR = Environment.format
      ( (__ \ "database"   ).read[String]
      ~ (__ \ "collection" ).read[String]
      ~ (__ \ "sizeBytes"  ).read[BigDecimal]
      ~ (__ \ "date"       ).read[LocalDate]
      ~ (__ \ "environment").read[Environment]
      ~ (__ \ "service"    ).readNullable[String]
      )(apply)
    }
  }
}

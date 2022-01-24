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

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceUsageConnector @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {

  private val baseUrl =
    s"${servicesConfig.baseUrl("service-configs")}/resource-usage"

  implicit val resourceUsageFormat: Format[ResourceUsage] =
    ResourceUsage.format

  def historicResourceUsageForService(
    serviceName: String,
    generateFakeData: Boolean = false)(implicit hc: HeaderCarrier): Future[List[ResourceUsage]] = {
    if (generateFakeData)
      Future.successful(fakeData())
    else
      http.GET[List[ResourceUsage]](url = url"$baseUrl/services/$serviceName/snapshots")
  }

  def fakeData(): List[ResourceUsage] = {

    def fakeFor(date: Instant, environment: Environment): ResourceUsage = {
      val r = scala.util.Random
      val slots =
        environment match {
          case Environment.Development  =>  5 + r.nextInt(2)
          case Environment.Integration  =>  0
          case Environment.QA           =>  5 + r.nextInt(2)
          case Environment.Staging      => 10 + r.nextInt(5)
          case Environment.ExternalTest => 20 + r.nextInt(10)
          case Environment.Production   => 25 + r.nextInt(15)
        }

      ResourceUsage(date, "", environment, slots, 1)
    }

    val dates =
      Stream
        .iterate(Instant.now(), 50)(_.minusSeconds(432000))
        .toList
        .sorted

    for {
      date <- dates
      env  <- Environment.values.toList
    } yield fakeFor(date, env)
  }
}

object ResourceUsageConnector {

  final case class ResourceUsage(
    date: Instant,
    serviceName: String,
    environment: Environment,
    slots: Int,
    instances: Int
  )

  object ResourceUsage {
    val format: Format[ResourceUsage] =
      (   (__ \ "date"        ).format[Instant]
        ~ (__ \ "serviceName" ).format[String]
        ~ (__ \ "environment" ).format[Environment](Environment.format)
        ~ (__ \ "slots"       ).format[Int]
        ~ (__ \ "instances"   ).format[Int]
        ) (ResourceUsage.apply, unlift(ResourceUsage.unapply))
  }
}

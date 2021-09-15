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

package uk.gov.hmrc.cataloguefrontend.metrics.connector

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.metrics.model._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MetricsConnector.Impl])
trait MetricsConnector {
  def query(maybeTeam: Option[TeamName]): Future[MetricsResponse]
}

object MetricsConnector{

  class Impl @Inject() (
    httpClient: HttpClient,
    servicesConfig: ServicesConfig
  )(implicit val ec: ExecutionContext) extends MetricsConnector {
    import uk.gov.hmrc.http._
    import cats.syntax.flatMap._
    import cats.syntax.applicativeError._

    private implicit val hc: HeaderCarrier = HeaderCarrier()
    private val platformProgressMetricsBaseURL: String = servicesConfig.baseUrl("platform-progress-metrics")
    implicit val rF: Reads[MetricsResponse] = MetricsResponse.reads
    val logger                           = Logger(this.getClass)

    override def query(maybeTeam: Option[TeamName]): Future[MetricsResponse] = {
      val url = url"$platformProgressMetricsBaseURL/platform-progress-metrics/metrics?team=${maybeTeam.map(_.asString)}"
      httpClient
        .GET[MetricsResponse](url)
        .flatTap( response =>
          Future.successful(
            logger.info(s"received the following response: $response from query to: $url")
          )
        )
        .onError {
          case e =>
            Future.successful(
              logger.error(s"An error occurred when connecting to platform progress metrics service. baseUrl: $platformProgressMetricsBaseURL", e)
            )
        }
    }
  }
}

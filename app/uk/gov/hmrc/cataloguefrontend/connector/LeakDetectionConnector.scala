/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class LeakDetectionConnector @Inject()(
  http: HttpClient,
  environment: Environment,
  servicesConfig: ServicesConfig
) {

  def url: String = servicesConfig.baseUrl("leak-detection")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] = {
    val hcAcceptingJson = hc.withExtraHeaders("Accept" -> "application/json")
    http.GET[Seq[RepositoryWithLeaks]](s"$url/reports/repositories")(implicitly, hcAcceptingJson, implicitly)
  }

}

final case class RepositoryWithLeaks(name: String) extends AnyVal

object RepositoryWithLeaks {
  implicit val reads: Reads[RepositoryWithLeaks] =
    implicitly[Reads[String]].map(RepositoryWithLeaks.apply)
}

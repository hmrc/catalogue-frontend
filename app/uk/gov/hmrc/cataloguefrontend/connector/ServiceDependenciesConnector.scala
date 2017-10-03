/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.{Configuration, Logger, Environment => PlayEnvironment}
import uk.gov.hmrc.cataloguefrontend.UrlHelper
import uk.gov.hmrc.cataloguefrontend.connector.model.Dependencies
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future



@Singleton
class ServiceDependenciesConnector @Inject()(http : HttpClient, override val runModeConfiguration:Configuration, environment : PlayEnvironment) extends ServicesConfig {

  def servicesDependenciesBaseUrl: String = baseUrl("service-dependencies") + "/api/service-dependencies"

  override protected def mode = environment.mode

  def getDependencies(repositoryName: String)(implicit hc: HeaderCarrier): Future[Option[Dependencies]] = {
    val url = s"$servicesDependenciesBaseUrl"

    http.GET[Option[Dependencies]](s"${url.appendSlash}dependencies/$repositoryName")
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $servicesDependenciesBaseUrl: ${ex.getMessage}", ex)
          None
      }
  }

  def getAllDependencies()(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    val url = s"$servicesDependenciesBaseUrl"

    http.GET[Seq[Dependencies]](s"${url.appendSlash}dependencies")
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $servicesDependenciesBaseUrl: ${ex.getMessage}", ex)
          Nil
      }
  }

}

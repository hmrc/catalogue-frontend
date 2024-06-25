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

package uk.gov.hmrc.cataloguefrontend.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class BuildDeployApiConfig @Inject()(
  configuration: Configuration,
  servicesConfig: ServicesConfig
):
  val baseUrl: String =
    configuration.get[String]("build-deploy-api.url")

  val host: String =
    configuration.get[String]("build-deploy-api.host")

  val awsRegion: String =
    configuration.get[String]("build-deploy-api.aws-region")

  val platopsBndApiBaseUrl: String =
    servicesConfig.baseUrl("platops-bnd-api")

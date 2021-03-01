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

package uk.gov.hmrc.cataloguefrontend

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait FakeApplicationBuilder
  extends AnyWordSpecLike
  with GuiceOneServerPerSuite
  with WireMockEndpoints{

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "microservice.services.health-indicators.port" -> endpointPort,
          "microservice.services.health-indicators.host" -> host,
          "microservice.services.teams-and-repositories.port" -> endpointPort,
          "microservice.services.teams-and-repositories.host" -> host,
          "microservice.services.indicators.port"             -> endpointPort,
          "microservice.services.indicators.host"             -> host,
          "microservice.services.service-dependencies.host"   -> host,
          "microservice.services.service-dependencies.port"   -> endpointPort,
          "microservice.services.leak-detection.port"         -> endpointPort,
          "microservice.services.leak-detection.host"         -> host,
          "microservice.services.service-configs.port"        -> endpointPort,
          "microservice.services.service-configs.host"        -> host,
          "microservice.services.shutter-api.port"            -> endpointPort,
          "microservice.services.shutter-api.host"            -> host,
          "microservice.services.releases-api.port"           -> endpointPort,
          "microservice.services.releases-api.host"           -> host,
          "microservice.services.user-management.url"          -> endpointMockUrl,
          "github.open.api.rawurl"                            -> endpointMockUrl,
          "github.open.api.token"                             -> "",
          "play.http.requestHandler"                          -> "play.api.http.DefaultHttpRequestHandler",
          "usermanagement.portal.url"                          -> "http://usermanagement/link",
          "microservice.services.user-management.frontPageUrl" -> "http://some.ump.fontpage.com",
          "microservice.services.user-management.myTeamsUrl"   -> "http://some.ump.com/myTeams",
          "play.ws.ssl.loose.acceptAnyCertificate"             -> true,
          "play.http.requestHandler"                           -> "play.api.http.DefaultHttpRequestHandler",
          "team.hideArchivedRepositories"                      -> true,
          "metrics.jvm"                                        -> false
        ))
      .build()

}

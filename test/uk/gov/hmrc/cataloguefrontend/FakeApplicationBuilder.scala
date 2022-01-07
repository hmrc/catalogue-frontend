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

package uk.gov.hmrc.cataloguefrontend

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait FakeApplicationBuilder
  extends AnyWordSpecLike
     with GuiceOneServerPerSuite
     with WireMockEndpoints {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "microservice.services.health-indicators.port"       -> wireMockPort,
          "microservice.services.health-indicators.host"       -> wireMockHost,
          "microservice.services.teams-and-repositories.port"  -> wireMockPort,
          "microservice.services.teams-and-repositories.host"  -> wireMockHost,
          "microservice.services.indicators.port"              -> wireMockPort,
          "microservice.services.indicators.host"              -> wireMockHost,
          "microservice.services.service-dependencies.host"    -> wireMockHost,
          "microservice.services.service-dependencies.port"    -> wireMockPort,
          "microservice.services.leak-detection.port"          -> wireMockPort,
          "microservice.services.leak-detection.host"          -> wireMockHost,
          "microservice.services.service-configs.port"         -> wireMockPort,
          "microservice.services.service-configs.host"         -> wireMockHost,
          "microservice.services.shutter-api.port"             -> wireMockPort,
          "microservice.services.shutter-api.host"             -> wireMockHost,
          "microservice.services.releases-api.port"            -> wireMockPort,
          "microservice.services.releases-api.host"            -> wireMockHost,
          "microservice.services.user-management.url"          -> wireMockUrl,
          "github.open.api.rawurl"                             -> wireMockUrl,
          "github.open.api.token"                              -> "",
          "play.http.requestHandler"                           -> "play.api.http.DefaultHttpRequestHandler",
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

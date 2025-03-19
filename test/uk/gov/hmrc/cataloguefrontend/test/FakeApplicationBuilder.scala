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

package uk.gov.hmrc.cataloguefrontend.test

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{DefaultWSCookie, WSCookie, WSClient, WSRequest}
import play.api.mvc.{Session, SessionCookieBaker}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto

trait FakeApplicationBuilder
  extends AnyWordSpecLike
     with GuiceOneServerPerSuite
     with WireMockEndpoints {

  protected lazy val wsClient = app.injector.instanceOf[WSClient]
  protected lazy val sessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  protected lazy val sessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  override def fakeApplication(): Application = guiceApplicationBuilder.build()

  def guiceApplicationBuilder: GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        Map(
          "microservice.services.internal-auth.port"                -> wireMockPort,
          "microservice.services.internal-auth.host"                -> wireMockHost,
          "microservice.services.teams-and-repositories.port"       -> wireMockPort,
          "microservice.services.teams-and-repositories.host"       -> wireMockHost,
          "microservice.services.indicators.port"                   -> wireMockPort,
          "microservice.services.indicators.host"                   -> wireMockHost,
          "microservice.services.service-dependencies.host"         -> wireMockHost,
          "microservice.services.service-dependencies.port"         -> wireMockPort,
          "microservice.services.leak-detection.port"               -> wireMockPort,
          "microservice.services.leak-detection.host"               -> wireMockHost,
          "microservice.services.service-configs.port"              -> wireMockPort,
          "microservice.services.service-configs.host"              -> wireMockHost,
          "microservice.services.shutter-api.port"                  -> wireMockPort,
          "microservice.services.shutter-api.host"                  -> wireMockHost,
          "microservice.services.releases-api.port"                 -> wireMockPort,
          "microservice.services.releases-api.host"                 -> wireMockHost,
          "microservice.services.user-management.port"              -> wireMockPort,
          "microservice.services.user-management.host"              -> wireMockHost,
          "microservice.services.pr-commenter.port"                 -> wireMockPort,
          "microservice.services.pr-commenter.host"                 -> wireMockHost,
          "microservice.services.platform-initiatives.port"         -> wireMockPort,
          "microservice.services.platform-initiatives.host"         -> wireMockHost,
          "microservice.services.vulnerabilities.port"              -> wireMockPort,
          "microservice.services.vulnerabilities.host"              -> wireMockHost,
          "build-deploy-api.url"                                    -> wireMockUrl,
          "build-deploy-api.host"                                   -> wireMockHost,
          "build-deploy-api.aws-region"                             -> "eu-west-2",
          "microservice.services.platops-bnd-api.port"              -> wireMockPort,
          "microservice.services.platops-bnd-api.host"              -> wireMockHost,
          "github.open.api.rawurl"                                  -> wireMockUrl,
          "github.open.api.token"                                   -> "",
          "play.http.requestHandler"                                -> "play.api.http.DefaultHttpRequestHandler",
          "ump.teamBaseUrl"                                         -> "http://some.ump.com/myTeams",
          "play.ws.ssl.loose.acceptAnyCertificate"                  -> true,
          "play.http.requestHandler"                                -> "play.api.http.DefaultHttpRequestHandler",
          "team.hideArchivedRepositories"                           -> true,
          "play.filters.csrf.header.bypassHeaders.X-Requested-With" -> "*",
          "play.filters.csrf.header.bypassHeaders.Csrf-Token"       -> "nocheck",
          "microservice.services.service-metrics.port"              -> wireMockPort,
          "microservice.services.service-metrics.host"              -> wireMockHost,
          "microservice.services.service-commissioning-status.port" -> wireMockPort,
          "microservice.services.service-commissioning-status.host" -> wireMockHost
        )
      )

  def cookieForAuth(value: String): WSCookie =
    val sessionCookie =
      sessionCookieBaker
        .encodeAsCookie(Session(Map(SessionKeys.authToken -> value)))
    DefaultWSCookie(
      sessionCookie.name,
      sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
    )

  extension (request: WSRequest)
    def withAuthToken(token: String): WSRequest =
      request.withCookies(cookieForAuth(token))
}

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

package uk.gov.hmrc.cataloguefrontend

import com.github.tomakehurst.wiremock.client.{WireMock, ResponseDefinitionBuilder}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.RequestMethod
import org.scalatest.Suite
import uk.gov.hmrc.http.test.WireMockSupport

trait WireMockEndpoints extends WireMockSupport {
  this: Suite =>

  def setupAuthEndpoint(): Unit =
    if (CatalogueFrontendSwitches.requiresLogin.isEnabled)
      serviceEndpoint(RequestMethod.POST, "/internal-auth/auth", willRespondWith = (200, Some("""{"retrievals": []}""")))

  def setupEnableBranchProtectionAuthEndpoint(): Unit =
    serviceEndpoint(RequestMethod.POST, "/internal-auth/auth", willRespondWith = (403, None))

  def setupChangePrototypePasswordAuthEndpoint(hasAuth: Boolean): Unit =
    val (status, body) = if (hasAuth) (200, Some("""{ "retrievals": [ true ] }""")) else (403, None)
    serviceEndpoint(RequestMethod.POST, "/internal-auth/auth", willRespondWith = (status, body))

  def serviceEndpoint(
    method         : RequestMethod,
    url            : String,
    requestHeaders : Map[String, String]   = Map.empty,
    queryParameters: Seq[(String, String)] = Seq.empty,
    willRespondWith: (Int, Option[String]),
    givenJsonBody  : Option[String]        = None
  ): Unit =
    val queryParamsAsString = queryParameters match
      case params if params.isEmpty => ""
      case params                   => params.map((k, v) => s"$k=$v").mkString("?", "&", "")

    val builder = WireMock.request(method.getName, urlEqualTo(s"$url$queryParamsAsString"))

    requestHeaders.foreach((k, v) => builder.withHeader(k, equalTo(v)))
    givenJsonBody.foreach(b => builder.withRequestBody(equalToJson(b)))

    val response = ResponseDefinitionBuilder().withStatus(willRespondWith._1)
    builder.willReturn(willRespondWith._2.fold(response)(response.withBody))

    wireMockServer.addStubMapping(builder.build)
}

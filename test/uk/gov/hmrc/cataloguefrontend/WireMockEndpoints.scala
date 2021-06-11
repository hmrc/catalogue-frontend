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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{MappingBuilder, ResponseDefinitionBuilder}
import com.github.tomakehurst.wiremock.http.RequestMethod
import org.scalatest.Suite
import uk.gov.hmrc.http.test.WireMockSupport

trait WireMockEndpoints extends WireMockSupport {
  this: Suite =>

  def serviceEndpoint(
    method         : RequestMethod,
    url            : String,
    extraHeaders   : Map[String, String]   = Map.empty,
    requestHeaders : Map[String, String]   = Map.empty,
    queryParameters: Seq[(String, String)] = Nil,
    willRespondWith: (Int, Option[String]),
    givenJsonBody  : Option[String]        = None
  ): Unit = {
    val queryParamsAsString = queryParameters match {
      case Nil    => ""
      case params => params.map { case (k, v) => s"$k=$v" }.mkString("?", "&", "")
    }

    val builder = new MappingBuilder(method, urlEqualTo(s"$url$queryParamsAsString"))

    requestHeaders.foreach { case (k, v) => builder.withHeader(k, equalTo(v)) }
    givenJsonBody.foreach(b => builder.withRequestBody(equalToJson(b)))

    val response = new ResponseDefinitionBuilder().withStatus(willRespondWith._1)
    val resp = willRespondWith._2.fold(response)(response.withBody)
    extraHeaders.foreach { case (n, v) => resp.withHeader(n, v) }
    builder.willReturn(resp)

    wireMockServer.addStubMapping(builder.build)
  }
}

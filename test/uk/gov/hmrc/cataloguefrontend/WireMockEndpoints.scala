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

package uk.gov.hmrc.cataloguefrontend

import java.net.ServerSocket

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{ResponseDefinitionBuilder, MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.github.tomakehurst.wiremock.http.RequestMethod
import org.scalatest.{Suite, BeforeAndAfterEach, BeforeAndAfterAll}

import scala.util.Try
import scala.collection.JavaConversions._

trait WireMockEndpoints extends Suite with BeforeAndAfterAll with BeforeAndAfterEach{

  val host: String = "localhost"

  val endpointPort: Int = wireMockPort()
  val endpointMock = new WireMock(host, endpointPort)
  val endpointMockUrl = s"http://$host:$endpointPort"
  val endpointServer: WireMockServer = new WireMockServer(wireMockConfig().port(endpointPort))

  def startWireMock() = endpointServer.start()
  def stopWireMock() = endpointServer.stop()

  def wireMockPort():Int = PortTester.findPort()

  override def beforeEach():Unit={
    endpointMock.resetMappings()
    endpointMock.resetScenarios()
  }
  override def afterAll(): Unit ={
    endpointServer.stop()
  }
  override def beforeAll(): Unit ={
    println(s"starting endpoint server on $endpointPort")
    endpointServer.start()
  }

  def printMappings(): Unit ={
    println(s"endpointMockUrl = $endpointMockUrl")
    endpointMock.allStubMappings().getMappings.toList.foreach { s =>
      println(s)
    }
  }

  def serviceEndpoint(method: RequestMethod,
                       url: String,
                       extraHeaders: Map[String, String] = Map(),
                       requestHeaders: Map[String, String] = Map(),
                       willRespondWith: (Int, Option[String]),
                       givenJsonBody: Option[String] = None): Unit = {

    val builder = new MappingBuilder(method, urlEqualTo(url))
    requestHeaders.map { case (k, v) => builder.withHeader(k, equalTo(v)) }
    //    .withHeader("Content-Type", equalTo("application/json"))

    givenJsonBody.map { b =>
      builder.withRequestBody(equalToJson(b))
    }.getOrElse(builder)

    val response: ResponseDefinitionBuilder = new ResponseDefinitionBuilder()
      .withStatus(willRespondWith._1)

    val resp = willRespondWith._2.map { b =>
      response.withBody(b)
    }.getOrElse(response)

    extraHeaders.foreach { case (n, v) =>
      resp.withHeader(n, v)
    }

    builder.willReturn(resp)

    endpointMock.register(builder)
  }

}

object PortTester {

  def findPort(excluded: Int*): Int = {
    (6001 to 7000).find(port => !excluded.contains(port) && isFree(port)).getOrElse(throw new Exception("No free port"))
  }

  private def isFree(port: Int): Boolean = {
    val triedSocket = Try {
      val serverSocket = new ServerSocket(port)
      Try(serverSocket.close())
      serverSocket
    }
    triedSocket.isSuccess
  }
}

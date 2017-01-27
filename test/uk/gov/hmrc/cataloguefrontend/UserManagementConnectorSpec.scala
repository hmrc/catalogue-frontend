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

import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.when
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.{ConnectionError, ConnectorError, HTTPError, NoData, TeamMember}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future
import scala.io.Source

class UserManagementConnectorSpec extends FunSpec with Matchers with TypeCheckedTripleEquals with BeforeAndAfter
  with OneServerPerSuite with WireMockEndpoints with ScalaFutures with EitherValues with MockitoSugar with OptionValues {


  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.user-management.url" -> endpointMockUrl,
    "microservice.services.user-management.myTeamsUrl" -> "http://some.ump.com/myTeams",
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler"
  ).build()


  describe("User management connector") {
    it("should get the team members from the user-management service") {
      val teamMembers: Seq[TeamMember] = callExternalMockedService("team-chicken", Some("/user-management-response.json")).right.value

      teamMembers should have length 2

      teamMembers(0) shouldBe TeamMember(
        displayName = Some("Jim Willman"),
        familyName = Some("Willman"),
        givenName = Some("Jim"),
        primaryEmail = Some("jim.willman@digital.hmrc.gov.uk"),
        serviceOwnerFor = Some(Seq("MATO")),
        username = Some("jim.willman")
      )

      teamMembers(1) shouldBe TeamMember(displayName = Some("Karl GoJarvis"),
        familyName = Some("GoJarvis"),
        givenName = Some("Karl"),
        primaryEmail = Some("karl.gojarvis@hmrc.gsi.gov.uk"),
        serviceOwnerFor = Some(Seq("CATO", "SOME-SERVICE")),
        username = Some("karl.gojarvis"))

    }

    it("has an empty members array in json") {

      val error: ConnectorError = callExternalMockedService("team-chicken", Some("/user-management-empty-members.json")).left.value
      error should ===(NoData("http://some.ump.com/myTeams/team-chicken?edit"))
    }

    it("no members field in json") {
      val error: ConnectorError = callExternalMockedService("team-chicken", Some("/user-management-no-members.json")).left.value

      error should ===(NoData("http://some.ump.com/myTeams/team-chicken?edit"))
    }

    it("api returns an error code") {
      val error: ConnectorError = callExternalMockedService("team-chicken", None, 404).left.value

      error should ===(HTTPError(404))
    }

    it("api returns a connection error") {

      val mockedHttpGet = mock[HttpGet]

      val userManagementConnector = new UserManagementConnector {
        override val userManagementBaseUrl = "http://some.non.existing.url.com"
        override val http = mockedHttpGet
      }

      val expectedException = new RuntimeException("some error")

      when(mockedHttpGet.GET(anyString())(any(), any())).thenReturn(Future.failed(expectedException))

      val error: ConnectorError = userManagementConnector.getTeamMembers("teamName")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue.left.value
      error should ===(ConnectionError(expectedException))
    }



    it("should get the team details from the user-management service") {
      stubUserManagementEndPoint(
        url = "/v1/organisations/mdtp/teams/TEAM-A",
        jsonFileNameOpt = Some("/user-management-team-details-response.json")
      )

      val teamDetails = UserManagementConnector.getTeamDetails("TEAM-A")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue.right.value

      teamDetails.description.value shouldBe "TEAM-A is a great team"
      teamDetails.location.value shouldBe "STLPD"
      teamDetails.organisation.value shouldBe "ORGA"
      teamDetails.slack.value shouldBe "https://slack.host/messages/team-A"
      teamDetails.documentation.value shouldBe "https://some.documentation.url"
    }


    it("no organization/data field in json for team details") {
      stubUserManagementEndPoint(
        url = "/v1/organisations/mdtp/teams/TEAM-A",
        jsonFileNameOpt = Some("/user-management-team-details-nodata-response.json")
      )

      val teamDetails = UserManagementConnector.getTeamDetails("TEAM-A")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue.left.value

      teamDetails should ===(NoData("http://some.ump.com/myTeams/TEAM-A?edit"))
    }

    it("api returns an error code for team details") {
      stubUserManagementEndPoint(
        url = "/v1/organisations/mdtp/teams/TEAM-A",
        jsonFileNameOpt = None,
        httpCode = 404
      )
      val teamDetails = UserManagementConnector.getTeamDetails("TEAM-A")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue.left.value

      teamDetails should ===(HTTPError(404))
    }

    it("api returns a connection error for team details") {

      val mockedHttpGet = mock[HttpGet]

      val userManagementConnector = new UserManagementConnector {
        override val userManagementBaseUrl = "http://some.non.existing.url.com"
        override val http = mockedHttpGet
      }

      val expectedException = new RuntimeException("some error")

      when(mockedHttpGet.GET(anyString())(any(), any())).thenReturn(Future.failed(expectedException))

      val error: ConnectorError = userManagementConnector.getTeamDetails("TEAM-A")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue.left.value
      error should ===(ConnectionError(expectedException))
    }
  }


  def callExternalMockedService(teamName: String, jsonFileNameOpt: Option[String], httpCode: Int = 200): Either[ConnectorError, Seq[TeamMember]] = {

    stubUserManagementEndPoint(
      GET,
      httpCode,
      s"/v1/organisations/mdtp/teams/$teamName/members",
      jsonFileNameOpt)

    val errorOrResponse: Either[ConnectorError, Seq[TeamMember]] = UserManagementConnector.getTeamMembers(teamName)(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue
    errorOrResponse

  }

  def stubUserManagementEndPoint(method: RequestMethod = GET, httpCode: Int = 200, url: String, jsonFileNameOpt: Option[String]): Unit = {
    val json: Option[String] = jsonFileNameOpt.map(x => Source.fromURL(getClass.getResource(x)).getLines().mkString("\n"))

    serviceEndpoint(
      method = method,
      url = url,
      requestHeaders = Map("Token" -> "None", "requester" -> "None"),
      willRespondWith = (httpCode, json))
  }
}

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

package uk.gov.hmrc.cataloguefrontend.teams

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.BeforeAndAfter
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.cataloguefrontend.test.{FakeApplicationBuilder, UnitSpec}


class TeamsSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  "Teams list" should {
    "show a list of teams" in {
      setupAuthEndpoint()
      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (
          200,
          Some(
            s"""
                [
                  {
                    "name":"teamA",
                    "lastActiveDate": "2021-07-09T10:00:49Z",
                    "repos": ["repo-one", "repo-two", "repo-three", "repo-four"]
                  }
                ]
            """
          ))
      )
      serviceEndpoint(
        GET,
        "/user-management/teams?includeNonHuman=true",
        willRespondWith = (
          200,
          Some(
            s"""
                [
                  {
                    "members": [],
                    "teamName":"teamA",
                    "description": "team a",
                    "documentation": "stuff"
                  }
                ]
            """
          ))
      )

      val response = wsClient.url(s"http://localhost:$port/teams").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body   should include("""href="/teams/teamA">teamA</a>""")
    }
  }
}

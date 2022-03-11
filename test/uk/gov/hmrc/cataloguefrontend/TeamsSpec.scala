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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.BeforeAndAfter
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

import java.time.LocalDateTime

class TeamsSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  "Teams list" should {

    "show a list of teams" in {
      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (
          200,
          Some(
            s"""
                [
                  {
                    "name":"teamA"
                  }
                ]
            """
          ))
      )

      val response = WS.url(s"http://localhost:$port/teams").get.futureValue

      response.status shouldBe 200
      response.body   should include("""<a class="team-name" href="/teams/teamA">teamA</a>""")
    }
  }
}

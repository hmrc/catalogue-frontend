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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.BeforeAndAfter
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

import java.time.{LocalDateTime, ZoneOffset}

class TeamsSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  "Teams list" should {

    "show a list of teams" in {
      import uk.gov.hmrc.cataloguefrontend.DateHelper._

      val now: LocalDateTime = LocalDateTime.now()
      val firstactivityDate  = now.minusYears(2)
      val lastactivityDate   = now.minusDays(2)

      serviceEndpoint(
        GET,
        "/api/teams?includeRepos=true",
        willRespondWith = (
          200,
          Some(
            s"""
                [
                  {
                    "name":"teamA",
                    "firstActiveDate": "${firstactivityDate.toInstant(ZoneOffset.UTC)}",
                    "lastActiveDate": "${lastactivityDate.toInstant(ZoneOffset.UTC)}",
                    "repos": {},
                    "ownedRepos": []
                  }
                ]
            """
          ))
      )

      val response = WS.url(s"http://localhost:$port/teams").get.futureValue

      response.status shouldBe 200
      response.body   should include("""<a class="team-name" href="/teams/teamA">teamA</a>""")
      response.body   should include(firstactivityDate.asPattern("yyyy-MM-dd"))
      response.body   should include(lastactivityDate.asPattern("yyyy-MM-dd"))
    }
  }
}

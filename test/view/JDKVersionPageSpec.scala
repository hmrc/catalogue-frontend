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

package view
import org.jsoup.Jsoup
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.ViewMessages
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.connector.Team
import views.html.JdkVersionPage


class JDKVersionPageSpec extends AnyWordSpec with MockitoSugar with Matchers {
  private val msg: ViewMessages = mock[ViewMessages]

  "JDK Usage list" should {

    "show a list of slugs and what JDK they're using" in {
      implicit val request = FakeRequest()

      val versions = List(
        JDKVersion(name = "test-slug",     version = Version("1.181.0"), vendor = OpenJDK, kind = JDK)
      , JDKVersion(name = "thing-service", version = Version("1.171.0"), vendor = Oracle , kind = JRE)
      )

      val teams = List(Team(name = TeamName.apply("Team 1"), createdDate = None, lastActiveDate = None, repos = 2))

      val document = asDocument(new JdkVersionPage(msg)(versions, SlugInfoFlag.values, teams, SlugInfoFlag.Latest, None))

      val slug1 = document.select("#jdk-slug-test-slug")
      val slug2 = document.select("#jdk-slug-thing-service")

      slug1.select("#jdk-slug-test-slug").text() shouldBe "test-slug 1.181.0 JDK"
      slug1.select("#jdk-slug-test-slug img").attr("src") shouldBe "/assets/img/openjdk.png"
      slug2.select("#jdk-slug-thing-service").text() shouldBe "thing-service 1.171.0 JRE"
      slug2.select("#jdk-slug-thing-service img").attr("src") shouldBe "/assets/img/oracle2.gif"
    }


    "include a link to the repository" in {
      implicit val request = FakeRequest()

      val versions = List(JDKVersion(name= "thing-service", version = Version("1.171.0"), vendor = Oracle, kind = JDK))
      val teams    = List(Team(name = TeamName.apply("Team 1"), createdDate = None, lastActiveDate = None, repos = 1))
      val document = asDocument(new JdkVersionPage(msg)(versions, SlugInfoFlag.values, teams, SlugInfoFlag.Latest, None))

      val slug = document.select("#jdk-slug-thing-service")
      val link = slug.select("a[href*='/repositories/thing-service']")

      link.attr("href") shouldBe "/repositories/thing-service"
    }
  }

  private def asDocument(html: Html) = Jsoup.parse(html.toString())

}

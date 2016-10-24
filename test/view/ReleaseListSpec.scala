/*
 * Copyright 2016 HM Revenue & Customs
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

import java.time.LocalDateTime

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.{Matchers, WordSpec}
import play.api.data.Form
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.{TeamRelease, ReleasesFilter}
import uk.gov.hmrc.cataloguefrontend.DateHelper._

class ReleaseListSpec extends WordSpec with Matchers {


  def asDocument(html: Html): Document = Jsoup.parse(html.toString())

  "release_list" should {
    "display errors" in {

      val formWithErrors: Form[ReleasesFilter] = ReleasesFilter.form.bind(Map("from" -> "23?01/2016", "to" -> "23?01/2016"))

      val document = asDocument(views.html.release_list.render(Seq.empty, formWithErrors))

      document.select("li.alert-danger").get(0).text() shouldBe "from.error.date"
      document.select("li.alert-danger").get(1).text() shouldBe "to.error.date"

    }

    "display data" in {
      val now = LocalDateTime.now()

      val document = asDocument(views.html.release_list.render(Seq(TeamRelease("serv1", Seq("teamA", "teamB"), productionDate = now, creationDate = Some(now.plusDays(2)), interval = Some(2), leadTime = Some(10), version = "1.0")), ReleasesFilter.form))

      document.select("#row0_team").text() shouldBe "teamA teamB"
      document.select("#row0_name").text() shouldBe "serv1"
      document.select("#row0_version").text() shouldBe "1.0"
      document.select("#row0_production").text() shouldBe now.asString
      document.select("#row0_creation").text() shouldBe now.plusDays(2).asString
      document.select("#row0_leadtime").text() shouldBe "10 days"
      document.select("#row0_interval").text() shouldBe "2 days"

    }
  }

}

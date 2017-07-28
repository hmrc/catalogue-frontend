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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Matchers
import play.api.libs.ws.WSResponse

trait PlatformDependenciesSection extends Matchers {

  sealed case class DocumentSelectorsAndResult(selector: String, result: String, htmlClass: String)

  val dependencies = """{
                       |  "repositoryName": "service-name",
                       |  "libraryDependenciesState": [
                       |    {
                       |      "libraryName": "lib1-up-to-date",
                       |      "currentVersion": {
                       |        "major": 1,
                       |        "minor": 0,
                       |        "patch": 0
                       |      } ,
                       |      "latestVersion": {
                       |        "major": 1,
                       |        "minor": 0,
                       |        "patch": 0
                       |      }
                       |    },
                       |    {
                       |      "libraryName": "lib2-minor-behind",
                       |      "currentVersion": {
                       |        "major": 2,
                       |        "minor": 0,
                       |        "patch": 0
                       |      },
                       |      "latestVersion": {
                       |        "major": 2,
                       |        "minor": 1,
                       |        "patch": 0
                       |      }
                       |    },
                       |    {
                       |      "libraryName": "lib3-major-behind",
                       |      "currentVersion": {
                       |        "major": 3,
                       |        "minor": 0,
                       |        "patch": 0
                       |      },
                       |      "latestVersion": {
                       |        "major": 4,
                       |        "minor": 0,
                       |        "patch": 0
                       |      }
                       |    },
                       |    {
                       |      "libraryName": "lib4-patch-behind",
                       |      "currentVersion": {
                       |        "major": 3,
                       |        "minor": 0,
                       |        "patch": 0
                       |      },
                       |      "latestVersion": {
                       |        "major": 3,
                       |        "minor": 0,
                       |        "patch": 1
                       |      }
                       |    }
                       |  ]
                       |}""".stripMargin


  def response: WSResponse


  def runTests(): Unit = {

    response.status shouldBe 200

    val documentSelectorsAndResults: Seq[DocumentSelectorsAndResult] =
      Seq(
        DocumentSelectorsAndResult("#lib1-up-to-date", "lib1-up-to-date 1.0.0 1.0.0", "green"),
        DocumentSelectorsAndResult("#lib2-minor-behind", "lib2-minor-behind 2.0.0 2.1.0", "amber"),
        DocumentSelectorsAndResult("#lib3-major-behind", "lib3-major-behind 3.0.0 4.0.0", "red"),
        DocumentSelectorsAndResult("#lib4-patch-behind", "lib4-patch-behind 3.0.0 3.0.1", "amber")
      )

    def asDocument(html: String): Document = Jsoup.parse(html)


    val document = asDocument(response.body)

    documentSelectorsAndResults.foreach { sr =>
      document.select(sr.selector).get(0).text() shouldBe sr.result
      document.select(sr.selector).hasClass(sr.htmlClass) shouldBe true
    }
  }
}

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

package uk.gov.hmrc.cataloguefrontend.search

import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.cataloguefrontend.config.SearchConfig
import views.html.search.SearchResults


class SearchControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with FakeApplicationBuilder
     with OptionValues
     with ScalaFutures
     with IntegrationPatience {

  "Search controller" should {
    "limit results" in new Setup {
      when(mockSearchIndex.search(Seq("query1")))
        .thenReturn(Seq(
          SearchTerm(linkType = "a", name = "one"  , link = "/one"),
          SearchTerm(linkType = "b", name = "two"  , link = "/two"),
          SearchTerm(linkType = "c", name = "three", link = "/three")
        ))

      val res = controller.search(query = "query1", limit = 2)(FakeRequest())

      status(res) shouldBe 200
      contentAsString(res) should     include ("one")
      contentAsString(res) should     include ("two")
      contentAsString(res) should not include ("three")
    }

    "accept multiple search terms, capped at 5" in new Setup {
      when(mockSearchIndex.search(Seq("query1", "query2", "query3", "query4", "query5")))
        .thenReturn(Seq(
          SearchTerm(linkType = "a", name = "one"  , link = "/one")
        ))

      val res = controller.search(query = " query1 query2  query3  query4 query5 query6 ", limit = 2)(FakeRequest())

      status(res) shouldBe 200
      contentAsString(res) should include ("one")
    }
  }

  private trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mcc             = app.injector.instanceOf[MessagesControllerComponents]
    val view            = app.injector.instanceOf[SearchResults]
    val config          = app.injector.instanceOf[SearchConfig]
    val mockSearchIndex = mock[SearchIndex]
    val controller      = new SearchController(mockSearchIndex, view, config, mcc)
  }
}

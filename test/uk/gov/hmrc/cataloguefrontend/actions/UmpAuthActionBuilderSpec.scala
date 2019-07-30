/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.actions

import org.mockito.Matchers.{eq => is, _}
import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, ControllerComponents, MessagesControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, User}
import uk.gov.hmrc.cataloguefrontend.service.CatalogueErrorHandler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UmpAuthActionBuilderSpec extends WordSpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  "WhenAuthenticated Action" should {

    "allow the request to proceed if user is signed-in (checking UMP)" in new Setup {
      val expectedStatus = Ok
      val umpToken       = UmpToken("token")
      val request        = FakeRequest().withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(Some(User(List.empty))))

      whenAuthenticated
        .invokeBlock(request, (_: Request[AnyContent]) => Future(expectedStatus))
        .futureValue shouldBe expectedStatus
    }

    "return 303 REDIRECT if user is not signed-in (token exists but not valid)" in new Setup {
      val umpToken = UmpToken("token")
      val request  = FakeRequest(GET, "requestedPage").withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(None))

      val result = whenAuthenticated.invokeBlock(request, (_: Request[AnyContent]) => Future(Ok)).futureValue

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/sign-in?targetUrl=requestedPage")
    }

    "return 303 REDIRECT if user is not signed-in (no token in the session)" in new Setup {
      val result = whenAuthenticated.invokeBlock(FakeRequest(GET, "requestedPage"), (_: Request[AnyContent]) => Future(Ok)).futureValue

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/sign-in?targetUrl=requestedPage")
    }

    "return 303 REDIRECT with no targetUrl if request is not GET" in new Setup {
      val result = whenAuthenticated.invokeBlock(FakeRequest(POST, "requestedPage"), (_: Request[AnyContent]) => Future(Ok)).futureValue

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/sign-in")
    }
  }

  "WithGroup Action" should {

    "return 303 REDIRECT if user is not signed-in (token exists but not valid)" in new Setup {
      val umpToken = UmpToken("token")
      val group          = "dev-tools"
      val request  = FakeRequest(GET, "requestedPage").withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(None))

      val result = withGroup(group).invokeBlock(request, (_: Request[AnyContent]) => Future(Ok)).futureValue

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/sign-in?targetUrl=requestedPage")
    }

    "return 303 REDIRECT if user is not signed-in (no token in the session)" in new Setup {
      val group          = "dev-tools"
      val result = withGroup(group).invokeBlock(FakeRequest(GET, "requestedPage"), (_: Request[AnyContent]) => Future(Ok)).futureValue

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/sign-in?targetUrl=requestedPage")
    }

    "return 303 REDIRECT with no targetUrl if request is not GET" in new Setup {
      val group          = "dev-tools"
      val result = withGroup(group).invokeBlock(FakeRequest(POST, "requestedPage"), (_: Request[AnyContent]) => Future(Ok)).futureValue

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("/sign-in")
    }

    "allow the request to proceed if user has group (checking UMP)" in new Setup {
      val expectedStatus = Ok
      val umpToken       = UmpToken("token")
      val group          = "dev-tools"
      val request        = FakeRequest().withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(Some(User(groups = List(group)))))

      withGroup(group)
        .invokeBlock(request, (_: Request[AnyContent]) => Future(expectedStatus))
        .futureValue shouldBe expectedStatus
    }

    "return 403 FORBIDDEN if user does not have group" in new Setup {
      val umpToken = UmpToken("token")
      val group    = "dev-tools"
      val request  = FakeRequest().withSession("ump.token" -> umpToken.value)
      val expectedBody = "forbidden html"

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(Some(User(groups = List.empty))))
      when(catalogueErrorHandler.forbiddenTemplate(any())).thenReturn(Html(expectedBody))

      val result = withGroup(group).invokeBlock(request, (_: Request[AnyContent]) => Future(Ok))

      status(result) shouldBe 403
      contentAsString(result) shouldBe expectedBody
    }
  }

  private trait Setup {
    val userManagementAuthConnector = mock[UserManagementAuthConnector]
    val cc                          = app.injector.instanceOf[MessagesControllerComponents]
    val catalogueErrorHandler       = mock[CatalogueErrorHandler]
    val actionBuilder               = new UmpAuthActionBuilder(userManagementAuthConnector, cc, catalogueErrorHandler)
    val whenAuthenticated           = actionBuilder.whenAuthenticated
    def withGroup(group: String)    = actionBuilder.withGroup(group)
  }
}

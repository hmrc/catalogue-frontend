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

package uk.gov.hmrc.cataloguefrontend

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.repository.RepositoriesController
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.http.SessionKeys
import views.html._

import scala.concurrent.{ExecutionContext, Future}

class LibrariesSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  "/libraries" should {
    "redirect to the repositories page with the appropriate filters" in {
      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)

      val result = repositoriesController.allLibraries(
        FakeRequest().withSession(SessionKeys.authToken -> "Token token")
      )

      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/repositories?repoType=Library")
    }
  }

  private given mcc: MessagesControllerComponents = stubMessagesControllerComponents()

  private lazy val authStubBehaviour = mock[StubBehaviour]
  private lazy val repositoriesController = new RepositoriesController(
    teamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector],
    mcc                           = mcc,
    repositoriesListPage          = mock[RepositoriesListPage],
    auth                          = FrontendAuthComponentsStub(authStubBehaviour)
  )
}

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

package uk.gov.hmrc.cataloguefrontend

import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration

class UserManagementPortalLinkSpec extends WordSpec with Matchers with MockitoSugar {

  "UserManagementPortalLink" should {
    "should create a user management portal link for a team (append team- to lowercase name)" in {

      val config: Configuration = mock[Configuration]
      when(config.getString("usermanagement.portal.url")).thenReturn(Some("http://url"))
      UserManagementPortalLink(teamName = "My Team", config) shouldBe "http://url/My Team"


    }


    "should return a '#' as url if the link is missing in config" in {

      val config: Configuration = mock[Configuration]
      when(config.getString("usermanagement.portal.url")).thenReturn(None)
      UserManagementPortalLink(teamName = "MyTeam", config) shouldBe "#"


    }
  }

}

/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.editUser

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.validation.{Invalid, Valid}
import uk.gov.hmrc.cataloguefrontend.users.{EditUserAccessRequest, EditUserConstraints}

class EditUserAccessFormSpec extends AnyWordSpec with Matchers:

  val editUserAccessRequest: EditUserAccessRequest =
    EditUserAccessRequest(
      username = "joe.bloggs",
      organisation = "MDTP",
      vpn = false,
      jira = false,
      confluence = false,
      googleApps = false,
      environments = false,
      bitwarden = false
    )

  "EditUserConstraints" should:
    "return Valid when at least one new tooling access is requested" in:
      EditUserConstraints.accessHasChanged(editUserAccessRequest.copy(jira = true)) shouldBe Valid

    "return Invalid when no new tooling access is requested" in:
      EditUserConstraints.accessHasChanged(editUserAccessRequest) shouldBe Invalid("At least one new tooling access must be requested")

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

import org.scalatest.{Matchers, WordSpec, FunSuite}
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.RepoType.RepoType

class RepoTypeSpec extends WordSpec with Matchers {

  "RepoType" should {
    "be able to be read from a json string" in {
      Json.parse("""{"type":"Deployable"}""").as[Map[String, RepoType]] shouldBe Map("type" -> RepoType.Deployable)
      Json.parse("""{"type":"Library"}""").as[Map[String, RepoType]] shouldBe Map("type" -> RepoType.Library)
      Json.parse("""{"type":"Other"}""").as[Map[String, RepoType]] shouldBe Map("type" -> RepoType.Other)
    }
  }

}

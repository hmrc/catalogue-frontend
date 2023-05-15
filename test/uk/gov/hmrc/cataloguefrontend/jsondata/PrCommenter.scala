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

package uk.gov.hmrc.cataloguefrontend.jsondata

import uk.gov.hmrc.cataloguefrontend.JsonData

object PrCommenter {
  // name     : String,
  //  teamNames: List[String],
  //  version  : Version,
  //  comments : Seq[PrCommenterComment],
  //  created  : Instant

  val createdAt = JsonData.createdAt
  val reportResults: String =
    s"""[
        {
          "name":"11-seven-teams-repo",
          "version": "1.2.3",
          "teamNames": ["teamA", "teamB", "teamC", "teamD", "teamE", "teamF", "teamG", "teamH", "teamI"],
          "comments": [
              { "message": "hello", "params": { "id": "12345"} },
              { "message": "yo", "params": { "id": "123456"} },
              { "message": "wibble", "params": { "id": "1234567" } }
            ],
          "created": "$createdAt"
        }
       ]""""
}

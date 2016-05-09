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

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json

class CachedListSpec extends WordSpec with Matchers {

  "When deserialising from json, the cached list" should {

    "correctly interpret the cache timestamp" in {

      val timeStamp = new DateTime(2016, 4, 5, 12, 57).getMillis
      val expected = DateTimeFormat.forPattern("HH:mm dd/MM/yyyy").print(timeStamp)

      val json =
        s"""{
            | "cacheTimestamp": $timeStamp,
            | "data": [1,2,3]
            | }
         """.stripMargin

      val deserialised = Json.fromJson[CachedList[Int]](Json.parse(json))
      val actual = DateTimeFormat.forPattern("HH:mm dd/MM/yyyy").print(deserialised.get.time)

      actual should be(expected)

    }

  }

}

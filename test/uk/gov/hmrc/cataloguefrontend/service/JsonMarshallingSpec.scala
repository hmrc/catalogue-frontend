/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.service

import org.scalatest.WordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.{ConfigByEnvironment, ConfigByKey, ConfigByKeyEntry, ConfigEntry}
import org.scalatest.Matchers._

class JsonMarshallingSpec extends WordSpec with ConfigJson {

  "all-config response" should {
    "unmarshal to ConfigByEnvironment object" in {
      val jsonString =
        """
          |{
          |	"env1": {
          |		"key1": {
          |			"value": "value1"
          |		},
          |		"key2": {
          |			"value": "value2"
          |		}
          |  }
          |}
        """.stripMargin

      val expected = Map("env1" -> Map("key1" -> ConfigEntry("value1"), "key2" -> ConfigEntry("value2")))

      Json.parse(jsonString).as[ConfigByEnvironment] shouldBe expected

    }
  }
  "config-by-key response" should {
    "read into ConfigByKey object" in {
      val jsonString =
        """
          |{
          |    "key1": [
          |            {
          |                "environment": "env1",
          |                "configSource": "configSource1",
          |                "value": "value1"
          |            }
          |    ]
          |}
        """.stripMargin

      val expected = Map("key1" -> List(ConfigByKeyEntry("env1", "configSource1", "value1")))

      Json.parse(jsonString).as[ConfigByKey] shouldBe expected
    }
  }

}

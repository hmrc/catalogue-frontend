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

import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.mutable

class ConfigServiceSpec extends WordSpec with MockitoSugar {

  "config service" should {

    "flatten map" in new Setup {
      val input = mutable.Map[String, Object]("A" -> "1", "B" -> "2", "C" -> mutable.Map("D" -> "4", "E" -> "5"))
      val output = service.flattenYamlToDotNotation(mutable.Map[String, Object](), input)
      output.size shouldBe 4
      output("C.E") shouldBe ConfigEntry("5")
    }


    "Check single map for duplicates" should {
      "find duplicates" in new Setup {
        val input1 = mutable.Map[String, Object](  "A" -> ConfigEntry("1"), "B" -> ConfigEntry("2"), "C" -> ConfigEntry("3"))

        val input1Expected = mutable.Map[String, Object]("A" -> ConfigEntry("1"), "B" -> ConfigEntry("2", List("MyMap")), "C" -> ConfigEntry("3"))

        service.checkSingleMapForValue("B", "2", input1, "MyMap").toSeq shouldBe (input1Expected.toSeq)
      }


      "not find duplicates" in new Setup {
        val input1 = mutable.Map[String, Object](  "A" -> ConfigEntry("1"), "B" -> ConfigEntry("2"), "C" -> ConfigEntry("3"))

        val expectedResult = input1.toMap  // copy of original mutable map

        val result = service.checkSingleMapForValue("B", "NO_MATCH", input1, "myMap")
        result shouldBe (expectedResult)
      }
    }

    "Check duplicates" should {
      "find duplicates" in new Setup {
        import scala.collection.mutable.Map


        val input1 = scala.collection.mutable.Map[String, Object](  "A" -> ConfigEntry("1"), "B" -> ConfigEntry("2"), "C" ->  ConfigEntry("3"))
        val input2 = scala.collection.mutable.Map[String, Object](  "A" -> ConfigEntry("11"), "B" -> ConfigEntry("2"), "C" -> ConfigEntry("3"))

        val input1Expected = Map[String, Object]("A" -> ConfigEntry("1"), "B" -> ConfigEntry("2", List("map2")), "C" -> ConfigEntry("3", List("map2")))
        val input2Expected = Map[String, Object]("A" -> ConfigEntry("11"), "B" -> ConfigEntry("2", List("map1")), "C" -> ConfigEntry("3", List("map1")))

        val input = Map("map1" -> input1, "map2" -> input2)
        val expectedMultiResult = Map("map1" -> input1Expected, "map2" -> input2Expected)

        val multiResult = service.configByKey(input)
        multiResult shouldBe expectedMultiResult
      }
    }

  }
  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val configConnector = mock[ConfigConnector]
    val service = new ConfigService(configConnector)
  }
}

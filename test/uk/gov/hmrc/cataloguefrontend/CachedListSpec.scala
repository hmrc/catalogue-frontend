package uk.gov.hmrc.cataloguefrontend

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json

class CachedListSpec extends WordSpec with Matchers {

  "When deserialising from json, the cached list" should {

    "Correctly interpret the cache timestamp" in {

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

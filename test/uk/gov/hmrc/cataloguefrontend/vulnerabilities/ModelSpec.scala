/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.{JsError, JsResult, JsSuccess, JsValue, Json, Reads}
import play.api.libs.json.OFormat.oFormatFromReadsAndOWrites

class ModelSpec extends AnyWordSpec with Matchers with OptionValues{



  "Internal model for Vulnerabilties list" can {

    import DistinctVulnerability._
    val asJson = """{
                "vulnerableComponentName": "deb://ubuntu/xenial:test",
                "vulnerableComponentVersion": "1.2.5.4-1",
                "vulnerableComponents": [{"component": "deb://ubuntu/xenial:test", "version": "1.2.5.4-1"}],
                "id": "CVE-123",
                "score": 10,
                "summary": "summary",
                "description": "testing",
                "fixedVersions": ["2.5.2"],
                "references": ["http://test.com"],
                "publishedDate": "2019-08-20T10:53:37.00Z",
                "firstDetected": "2019-08-20T10:53:37.00Z",
                "assessment": "Serious",
                "curationStatus": "ACTION_REQUIRED",
                "ticket": "ticket1"
              }"""


    "deserialize from valid json to the entity" in {
      val entityOpt: Option[DistinctVulnerability] = jsonToDistinctVulnModel(asJson)


      entityOpt.value.id shouldBe "CVE-123"


    }

    val missingReferncesFields =
      """{
        |  "vulnerableComponentName": "deb://ubuntu/xenial:test",
        |  "vulnerableComponentVersion": "1.2.5.4-1",
        |  "vulnerableComponents": [{"component": "deb://ubuntu/xenial:test", "version": "1.2.5.4-1"}],
        |  "id": "CVE-123",
        |  "score": 10,
        |  "summary": "summary",
        |  "description": "testing",
        |  "fixedVersions": ["2.5.2"],
        |  "publishedDate": "2019-08-20T10:53:37.00Z",
        |  "firstDetected": "2019-08-20T10:53:37.00Z",
        |  "assessment": "Serious",
        |  "curationStatus": "ACTION_REQUIRED",
        |  "ticket": "ticket1"
        | }""".stripMargin

    "deser from valid json when 'references' field has been omitted" in {
      val entityOpt: Option[DistinctVulnerability] = jsonToDistinctVulnModel(missingReferncesFields)
      entityOpt.value.references should be (empty)
    }

    val referenceFieldAsEmptyList: String = """{
                "vulnerableComponentName": "deb://ubuntu/xenial:test",
                "vulnerableComponentVersion": "1.2.5.4-1",
                "vulnerableComponents": [{"component": "deb://ubuntu/xenial:test", "version": "1.2.5.4-1"}],
                "id": "CVE-123",
                "score": 10,
                "summary": "summary",
                "description": "testing",
                "fixedVersions": ["2.5.2"],
                "references": [],
                "publishedDate": "2019-08-20T10:53:37.00Z",
                "firstDetected": "2019-08-20T10:53:37.00Z",
                "assessment": "Serious",
                "curationStatus": "ACTION_REQUIRED",
                "ticket": "ticket1"
              }""".stripMargin

      "deser from valid json when 'references' field is empty list" in {
        val entityOpt: Option[DistinctVulnerability] = jsonToDistinctVulnModel(referenceFieldAsEmptyList)
        entityOpt.value.references should be(empty)
      }



  }

  private def jsonToDistinctVulnModel(asJson: String) = {
    val jsValue: JsValue = Json.parse(asJson)
    implicit val reads: Reads[DistinctVulnerability] = DistinctVulnerability.reads
    val entityResult: JsResult[DistinctVulnerability] = Json.fromJson(jsValue)

    val entityOpt = entityResult match {
      case JsError(errors) =>
        println(s"Json Errors: \n\t $errors")
        None
      case JsSuccess(value, path) =>
        Some(value)
    }
    entityOpt
  }
}

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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

enum CurationStatus(val asString: String, val display: String):
  case InvestigationOngoing extends CurationStatus(asString = "INVESTIGATION_ONGOING", display = "Investigation ongoing")
  case NoActionRequired     extends CurationStatus(asString = "NO_ACTION_REQUIRED"   , display = "No action required"   )
  case ActionRequired       extends CurationStatus(asString = "ACTION_REQUIRED"      , display = "Action required"      )
  case Uncurated            extends CurationStatus(asString = "UNCURATED"            , display = "Uncurated"            )

object CurationStatus {
  def parse(s: String): Either[String, CurationStatus] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid CurationStatus, should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[CurationStatus] = new Format[CurationStatus] {
    override def reads(json: JsValue): JsResult[CurationStatus] =
      json match {
        case JsString(s) =>
          parse(s).fold(msg => JsError(msg), cs => JsSuccess(cs))
        case _ => JsError("String value expected")
      }

    override def writes(cs: CurationStatus): JsValue =
      JsString(cs.asString)
  }

  import play.api.data.format.Formatter
  import play.api.data.FormError
  val formFormat: Formatter[CurationStatus] = new Formatter[CurationStatus] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], CurationStatus] =
      data
        .get(key)
        .flatMap(parse(_).toOption)
        .fold[Either[Seq[FormError], CurationStatus]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

    override def unbind(key: String, value: CurationStatus): Map[String, String] =
      Map(key -> value.asString)
  }
}

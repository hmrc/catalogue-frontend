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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import uk.gov.hmrc.cataloguefrontend.model.Environment
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError

sealed trait Check

object Check {
  case class Missing(addLink: String)
  case class Present(evidenceLink: String)

  type Result = Either[Missing, Present]

  sealed case class SimpleCheck(
    title       : String
  , checkResult : Result
  ) extends Check

  sealed case class EnvCheck(
    title        : String
  , checkResults : Map[Environment, Result]
  ) extends Check

  import play.api.libs.functional.syntax.toFunctionalBuilderOps
  import play.api.libs.json.{JsValue, Reads, __}
  val reads: Reads[Check] = new Reads[Check] {

    implicit val writesResult: Reads[Result] = new Reads[Result] {
      def reads(json: JsValue) =
        ( (json \ "evidence").asOpt[String], (json \ "add").asOpt[String] ) match {
          case (Some(str), _) => JsSuccess(Right(Present(str)): Result)
          case (_, Some(str)) => JsSuccess(Left( Missing(str)): Result)
          case _              => JsError("Could not find either field 'evidence' or 'add'")
        }
    }

    implicit val readsSimpleCheck: Reads[SimpleCheck] =
      ( (__ \ "title"      ).read[String]
      ~ (__ \ "simpleCheck").read[Result]
      ) (SimpleCheck.apply _)

    implicit val mapFormat: Reads[Map[Environment, Result]] =
      Reads
        .of[Map[String, Check.Result]]
        .map(
          _.map { case (k, v) => (Environment.parse(k).getOrElse(sys.error("Invalid Environment")), v) }
        )

    implicit val readsEnvCheck: Reads[EnvCheck] =
      ( (__ \ "title"           ).read[String]
      ~ (__ \ "environmentCheck").read[Map[Environment, Result]]
      ) (EnvCheck.apply _)

    def reads(json: JsValue) =
      json
        .validate[SimpleCheck]
        .orElse(json.validate[EnvCheck])
  }
}

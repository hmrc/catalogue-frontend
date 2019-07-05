/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.shuttering

import java.time.LocalDateTime
import play.api.libs.json.{Format, Json, JsError, JsValue, JsString, JsSuccess, Reads, __}
import play.api.libs.functional.syntax._


sealed trait Environment { def asString: String }
object Environment {
  case object Production      extends Environment { val asString = "production"    }
  case object ExternalTest    extends Environment { val asString = "external test" }
  case object QA              extends Environment { val asString = "qa"            }
  case object Staging         extends Environment { val asString = "staging"       }
  case object Dev             extends Environment { val asString = "development"   }

  val values = List(Production, ExternalTest, QA, Staging, Dev)

  def parse(s: String): Option[Environment] =
    values.find(_.asString == s)

  val format: Format[Environment] = new Format[Environment] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            Environment.parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid Environment '$s'")
            }
          }

    override def writes(e: Environment) =
      JsString(e.asString)
  }
}



case class ShutterState(
    name        : String
  , production  : Boolean
  , staging     : Boolean
  , qa          : Boolean
  , externalTest: Boolean
  , development : Boolean
  )

object ShutterState {

  val reads: Reads[ShutterState] =
    ( (__ \ "name"        ).read[String]
    ~ (__ \ "production"  ).read[Boolean]
    ~ (__ \ "staging"     ).read[Boolean]
    ~ (__ \ "qa"          ).read[Boolean]
    ~ (__ \ "externalTest").read[Boolean]
    ~ (__ \ "development" ).read[Boolean]
    )(ShutterState.apply _)
}


case class ShutterEvent(
    name       : String
  , env        : Environment
  , user       : String
  , date       : LocalDateTime
  , isShuttered: Boolean
  )

object ShutterEvent {

  val reads: Reads[ShutterEvent] = {
    implicit val ef = Environment.format
    ( (__ \ "name"       ).read[String]
    ~ (__ \ "env"        ).read[Environment]
    ~ (__ \ "user"       ).read[String]
    ~ (__ \ "date"       ).read[LocalDateTime]
    ~ (__ \ "isShuttered").read[Boolean]
    )(ShutterEvent.apply _)
  }
}
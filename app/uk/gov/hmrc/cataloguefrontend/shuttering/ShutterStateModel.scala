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
            parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid Environment '$s'")
            }
          }

    override def writes(e: Environment) =
      JsString(e.asString)
  }
}

sealed trait IsShuttered { def asString: String }
object IsShuttered {
  case object True  extends IsShuttered { val asString = "on" }
  case object False extends IsShuttered { val asString = "off" }

  val values = List(True, False)

  def parse(s: String): Option[IsShuttered] =
    values.find(_.asString == s)


  val format: Format[IsShuttered] = new Format[IsShuttered] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid IsShuttered '$s'")
            }
          }

    override def writes(e: IsShuttered) =
      JsString(e.asString)
  }
}

case class ShutterState( // Status
    name        : String
  , production  : IsShuttered // ShutterState Shuttter/Unshutter
  , staging     : IsShuttered
  , qa          : IsShuttered
  , externalTest: IsShuttered
  , development : IsShuttered
  ) {
    def stateFor(env: Environment): IsShuttered =
      env match {
        case Environment.Production   => production
        case Environment.ExternalTest => externalTest
        case Environment.QA           => qa
        case Environment.Staging      => staging
        case Environment.Dev          => development
      }
  }

object ShutterState {

  val reads: Reads[ShutterState] = {
    implicit val isf = IsShuttered.format
    ( (__ \ "name"        ).read[String]
    ~ (__ \ "production"  ).read[IsShuttered]
    ~ (__ \ "staging"     ).read[IsShuttered]
    ~ (__ \ "qa"          ).read[IsShuttered]
    ~ (__ \ "externalTest").read[IsShuttered]
    ~ (__ \ "development" ).read[IsShuttered]
    )(ShutterState.apply _)
  }
}


case class ShutterEvent(
    name       : String
  , env        : Environment
  , user       : String
  , date       : LocalDateTime
  , isShuttered: IsShuttered
  )

object ShutterEvent {

  val reads: Reads[ShutterEvent] = {
    implicit val ef  = Environment.format
    implicit val isf = IsShuttered.format
    ( (__ \ "name"       ).read[String]
    ~ (__ \ "env"        ).read[Environment]
    ~ (__ \ "user"       ).read[String]
    ~ (__ \ "date"       ).read[LocalDateTime]
    ~ (__ \ "isShuttered").read[IsShuttered]
    )(ShutterEvent.apply _)
  }
}
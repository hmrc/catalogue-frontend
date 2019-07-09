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

sealed trait ShutterStatus { def asString: String }
object ShutterStatus {
  case object Shuttered   extends ShutterStatus { val asString = "shuttered"   }
  case object Unshuttered extends ShutterStatus { val asString = "unshuttered" }

  val values = List(Shuttered, Unshuttered)

  def parse(s: String): Option[ShutterStatus] =
    values.find(_.asString == s)


  val format: Format[ShutterStatus] = new Format[ShutterStatus] {
    override def reads(json: JsValue) =
      json.validate[String]
        .flatMap { s =>
            parse(s) match {
              case Some(env) => JsSuccess(env)
              case None      => JsError(__, s"Invalid ShutterStatus '$s'")
            }
          }

    override def writes(e: ShutterStatus) =
      JsString(e.asString)
  }
}

case class ShutterState(
    name        : String
  , production  : ShutterStatus
  , staging     : ShutterStatus
  , qa          : ShutterStatus
  , externalTest: ShutterStatus
  , development : ShutterStatus
  ) {
    def statusFor(env: Environment): ShutterStatus =
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
    implicit val ssf = ShutterStatus.format
    ( (__ \ "name"        ).read[String]
    ~ (__ \ "production"  ).read[ShutterStatus]
    ~ (__ \ "staging"     ).read[ShutterStatus]
    ~ (__ \ "qa"          ).read[ShutterStatus]
    ~ (__ \ "externalTest").read[ShutterStatus]
    ~ (__ \ "development" ).read[ShutterStatus]
    )(ShutterState.apply _)
  }
}


case class ShutterEvent(
    name  : String
  , env   : Environment
  , user  : String
  , date  : LocalDateTime
  , status: ShutterStatus
  )

object ShutterEvent {

  val reads: Reads[ShutterEvent] = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format
    ( (__ \ "name"  ).read[String]
    ~ (__ \ "env"   ).read[Environment]
    ~ (__ \ "user"  ).read[String]
    ~ (__ \ "date"  ).read[LocalDateTime]
    ~ (__ \ "status").read[ShutterStatus]
    )(ShutterEvent.apply _)
  }
}
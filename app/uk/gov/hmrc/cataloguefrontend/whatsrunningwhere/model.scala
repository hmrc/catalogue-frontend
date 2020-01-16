/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class WhatsRunningWhere(
  applicationName: ApplicationName,
  versions       : List[WhatsRunningWhereVersion])

case class WhatsRunningWhereVersion(
  environment  : Environment,
  versionNumber: VersionNumber,
  lastSeen     : TimeSeen)


object JsonCodecs {
  def format[A, B](f: A => B, g: B => A)(implicit fa: Format[A]): Format[B] = fa.inmap(f, g)

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match {
      case Right(r) => JsSuccess(r)
      case Left(l)  => JsError(__, l)
    }

  val applicationNameFormat: Format[ApplicationName] = format(ApplicationName.apply, unlift(ApplicationName.unapply))
  val versionNumberFormat  : Format[VersionNumber]   = format(VersionNumber.apply  , unlift(VersionNumber.unapply  ))
  val environmentFormat    : Format[Environment]     = format(Environment.apply    , unlift(Environment.unapply    ))
  val timeSeenFormat       : Format[TimeSeen]        = format(TimeSeen.apply       , unlift(TimeSeen.unapply       ))

  val whatsRunningWhereVersionReads: Reads[WhatsRunningWhereVersion] = {
    implicit val wf  = environmentFormat
    implicit val vnf = versionNumberFormat
    implicit val tsf = timeSeenFormat
    ( (__ \ "environment"  ).read[Environment].map(env => Environment(env.asString.stripSuffix("-AWS-London")))
    ~ (__ \ "versionNumber").read[VersionNumber]
    ~ (__ \ "lastSeen"     ).read[TimeSeen]
    )(WhatsRunningWhereVersion.apply _)
  }

  val whatsRunningWhereReads: Reads[WhatsRunningWhere] = {
    implicit val wf    = applicationNameFormat
    implicit val wrwvf = whatsRunningWhereVersionReads
    ( (__ \ "applicationName").read[ApplicationName]
    ~ (__ \ "versions"       ).read[List[WhatsRunningWhereVersion]]
    )(WhatsRunningWhere.apply _)
  }

  val profileTypeFormat: Format[ProfileType] = new Format[ProfileType] {
    override def reads(js: JsValue): JsResult[ProfileType] =
      js.validate[String]
        .flatMap(s => toResult(ProfileType.parse(s)))

    override def writes(et: ProfileType): JsValue =
      JsString(et.asString)
  }

  val profileNameFormat: Format[ProfileName] =
    format(ProfileName.apply, unlift(ProfileName.unapply))

  val profileFormat: OFormat[Profile] = {
    implicit val ptf = profileTypeFormat
    implicit val pnf = profileNameFormat
    ( (__ \ "type").format[ProfileType]
    ~ (__ \ "name").format[ProfileName]
    )(Profile.apply, unlift(Profile.unapply))
  }
}

case class TimeSeen(time: LocalDateTime)

case class ApplicationName(asString: String) extends AnyVal

case class Environment(asString: String) extends AnyVal

object Environment {
  private def precedence(environment: Environment) = environment.asString match {
    // use `contains` so environments with prefixes/suffixes are sorted too
    case x if x.contains("production")   => 0
    case x if x.contains("externaltest") => 1
    case x if x.contains("staging")      => 2
    case x if x.contains("qa")           => 3
    case x if x.contains("integration")  => 4
    case x if x.contains("development")  => 5
    case _                               => 6
  }

  implicit val ordering: Ordering[Environment] = new Ordering[Environment] {
    override def compare(x: Environment, y: Environment): Int = {
      val envPrecedence = precedence(x).compare(precedence(y))
      if (envPrecedence == 0) x.asString.compareTo(y.asString) else envPrecedence
    }
  }
}

case class ProfileName(asString: String) extends AnyVal

object ProfileName {
  implicit val ordering = new Ordering[ProfileName] {
    def compare(x: ProfileName, y: ProfileName): Int =
      x.asString.compare(y.asString)
  }
}

sealed trait ProfileType {
  def asString: String
}
object ProfileType {
  def parse(s: String): Either[String, ProfileType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid profileType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val values: List[ProfileType] = List(
      Team
    , ServiceManager
    )

  case object Team extends ProfileType {
    override val asString: String = "team"
  }
  case object ServiceManager extends ProfileType {
    override val asString: String = "servicemanager"
  }
}

case class Profile(
  profileType: ProfileType
, profileName: ProfileName
)

case class VersionNumber(asString: String) extends AnyVal

/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import play.api.mvc.{PathBindable, QueryStringBindable}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsError, JsObject, JsString, JsSuccess, JsValue}
import play.api.data.FormError
import play.api.data.format.Formatter
import uk.gov.hmrc.cataloguefrontend.binders.Binders

package object model:

  case class Username(asString: String) extends AnyVal

  case class TeamName(asString: String) extends AnyVal

  object TeamName:
    val format: Format[TeamName] =
      Format.of[String].inmap(TeamName.apply, _.asString)

    given Ordering[TeamName] =
      Ordering.by(_.asString)

    given pathBindable: PathBindable[TeamName] =
      Binders.pathBindableFromString(
        s => Right(TeamName(s)),
        _.asString
      )

    implicit val queryStringBindable: QueryStringBindable[TeamName] =
      Binders.queryStringBindableFromString(
        s => Some(Right(TeamName(s))),
        _.asString
      )

    val formFormat: Formatter[TeamName] =
      new Formatter[TeamName]:
        override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], TeamName] =
          data
            .get(key)
            .map(_.trim) match
              case Some(s) if s.nonEmpty => Right(TeamName(s))
              case _                     => Left(Seq(FormError(key, s"$key is missing")))

        override def unbind(key: String, value: TeamName): Map[String, String] =
          Map(key -> value.asString)

  end TeamName

  case class ServiceName(asString: String) extends AnyVal

  object ServiceName:
    val format: Format[ServiceName] =
      Format.of[String].inmap(ServiceName.apply, _.asString)

    given Ordering[ServiceName] =
      Ordering.by(_.asString)

    val formFormat: Formatter[ServiceName] =
      new Formatter[ServiceName]:
        override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ServiceName] =
          data
            .get(key)
            .map(_.trim) match
              case Some(s) if s.nonEmpty => Right(ServiceName(s))
              case _                     => Left(Seq(FormError(key, s"$key is missing")))

        override def unbind(key: String, value: ServiceName): Map[String, String] =
          Map(key -> value.asString)

  end ServiceName

  case class Version(
    major   : Int,
    minor   : Int,
    patch   : Int,
    original: String
  ) extends Ordered[Version]: // TODO do we need Ordered as well as Ordering?

    def diff(other: Version): (Int, Int, Int) =
      (this.major - other.major, this.minor - other.minor, this.patch - other.patch)

    override def compare(other: Version): Int =
      import Ordered._
      (major, minor, patch, original).compare((other.major, other.minor, other.patch, other.original))

    override def toString: String =
      original

  object Version:
    given Ordering[Version] =
      new Ordering[Version]:
        def compare(x: Version, y: Version): Int =
          x.compare(y)

    def isNewVersionAvailable(currentVersion: Version, latestVersion: Version): Boolean =
      latestVersion.diff(currentVersion) match
        case (major, minor, patch) =>
          (major > 0)
            || (major == 0 && minor > 0)
            || (major == 0 && minor == 0 && patch > 0)

    def apply(s: String): Version =
      val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
      val regex2 = """(\d+)\.(\d+)(.*)""".r
      val regex1 = """(\d+)(.*)""".r
      s match
        case regex3(maj, min, patch, _) => Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s)
        case regex2(maj, min, _)        => Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , s)
        case regex1(patch, _)           => Version(0                    , 0                    , Integer.parseInt(patch), s)
        case _                          => Version(0                    , 0                    , 0                      , s)

    val format: Format[Version] =
      new Format[Version]:
        override def reads(json: JsValue) =
          json match
            case JsString(s) => JsSuccess(Version(s))
            case JsObject(m) => m.get("original") match
                                  case Some(JsString(s)) => JsSuccess(Version(s))
                                  case _                 => JsError("Not a string")
            case _           => JsError("Not a string")

        override def writes(v: Version) =
          JsString(v.original)

    val formFormat: Formatter[Version] =
      new Formatter[Version]:
        override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Version] =
          data
            .get(key)
            .flatMap(str => scala.util.Try(apply(str)).toOption )
            .fold[Either[Seq[FormError], Version]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

        override def unbind(key: String, value: Version): Map[String, String] =
          Map(key -> value.original)

end model

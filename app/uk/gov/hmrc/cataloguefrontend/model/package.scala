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

package object model {

  case class Username(asString: String) extends AnyVal

  case class TeamName(asString: String) extends AnyVal

  object TeamName {
    val format: Format[TeamName] =
      Format.of[String].inmap(TeamName.apply, _.asString)

    implicit val ordering: Ordering[TeamName] =
      Ordering.by(_.asString)

    implicit val pathBindable: PathBindable[TeamName] =
      new PathBindable[TeamName] {
        override def bind(key: String, value: String): Either[String, TeamName] =
          Right(TeamName(value))

        override def unbind(key: String, value: TeamName): String =
          value.asString
      }

    implicit def queryStringBindable(implicit strBinder: QueryStringBindable[String]): QueryStringBindable[TeamName] =
      new QueryStringBindable[TeamName] {
        override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, TeamName]] =
          strBinder.bind(key, params)
            .map(_.map(TeamName.apply))

        override def unbind(key: String, value: TeamName): String =
          strBinder.unbind(key, value.asString)
      }

    val formFormat: Formatter[TeamName] = new Formatter[TeamName] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], TeamName] =
        data
          .get(key)
          .toRight(Seq(FormError(key, s"$key is missing")))
          .map(TeamName.apply)

      override def unbind(key: String, value: TeamName): Map[String, String] =
        Map(key -> value.asString)
    }
  }

  case class ServiceName(asString: String) extends AnyVal

  object ServiceName {
    val format: Format[ServiceName] =
      Format.of[String].inmap(ServiceName.apply, _.asString)

    implicit val serviceNameOrdering: Ordering[ServiceName] =
      Ordering.by(_.asString)

    val formFormat: Formatter[ServiceName] = new Formatter[ServiceName] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ServiceName] =
        data
          .get(key)
          .toRight(Seq(FormError(key, s"$key is missing")))
          .map(ServiceName.apply)

      override def unbind(key: String, value: ServiceName): Map[String, String] =
        Map(key -> value.asString)
    }
  }

  case class Version(
    major   : Int,
    minor   : Int,
    patch   : Int,
    original: String
  ) extends Ordered[Version] {

    def diff(other: Version): (Int, Int, Int) =
      (this.major - other.major, this.minor - other.minor, this.patch - other.patch)

    override def compare(other: Version): Int = {
      import Ordered._
      (major, minor, patch, original).compare((other.major, other.minor, other.patch, other.original))
    }

    override def toString: String = original
  }

  object Version {

    implicit val ordering: Ordering[Version] = new Ordering[Version] {
      def compare(x: Version, y: Version): Int =
        x.compare(y)
    }

    def isNewVersionAvailable(currentVersion: Version, latestVersion: Version): Boolean =
      latestVersion.diff(currentVersion) match {
        case (major, minor, patch) =>
          (major > 0)
            || (major == 0 && minor > 0)
            || (major == 0 && minor == 0 && patch > 0)
      }

    def apply(s: String): Version = {
      val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
      val regex2 = """(\d+)\.(\d+)(.*)""".r
      val regex1 = """(\d+)(.*)""".r
      s match {
        case regex3(maj, min, patch, _) => Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s)
        case regex2(maj, min, _)        => Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , s)
        case regex1(patch, _)           => Version(0                    , 0                    , Integer.parseInt(patch), s)
        case _                          => Version(0                    , 0                    , 0                      , s)
      }
    }

    val format: Format[Version] = new Format[Version] {
      override def reads(json: JsValue) =
        json match {
          case JsString(s) => JsSuccess(Version(s))
          case JsObject(m) =>
            m.get("original") match {
              case Some(JsString(s)) => JsSuccess(Version(s))
              case _                 => JsError("Not a string")
            }
          case _ => JsError("Not a string")
        }

      override def writes(v: Version) =
        JsString(v.original)
    }

    val formFormat: Formatter[Version] = new Formatter[Version] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Version] =
        data
          .get(key)
          .flatMap(str => scala.util.Try(apply(str)).toOption )
          .fold[Either[Seq[FormError], Version]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

      override def unbind(key: String, value: Version): Map[String, String] =
        Map(key -> value.original)
    }
  }
}

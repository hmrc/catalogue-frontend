/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{__, Format, Reads}

import play.api.mvc.{PathBindable, QueryStringBindable}

case class Username(value: String) extends AnyVal

case class TeamName(asString: String) extends AnyVal

object TeamName {
  lazy val format: Format[TeamName] =
    Format.of[String].inmap(TeamName.apply, unlift(TeamName.unapply))

  implicit val ordering = new Ordering[TeamName] {
    def compare(x: TeamName, y: TeamName): Int =
      x.asString.compare(y.asString)
  }

  implicit val pathBindable: PathBindable[TeamName] =
    new PathBindable[TeamName] {
      override def bind(key: String, value: String): Either[String, TeamName] =
        Right(TeamName(value))

      override def unbind(key: String, value: TeamName): String =
        value.asString
    }

  implicit val queryStringBindable: QueryStringBindable[TeamName] =
    new QueryStringBindable[TeamName] {
      private val Name = "team"

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, TeamName]] =
        params.get(Name).map { values =>
          values.toList match {
            case Nil         => Left("missing team value")
            case head :: Nil => pathBindable.bind(key, head)
            case _           => Left("too many team values")
          }
        }

      override def unbind(key: String, value: TeamName): String =
        s"$Name=${value.asString}"
    }
}


// TODO can we return scala versions too?
case class RepositoryModules(
  name             : String,
  version          : Option[Version],
  dependenciesBuild: Seq[Dependency],
  modules          : Seq[RepositoryModule]
) {
  def allDependencies: Seq[Dependency] =
    modules.foldLeft(dependenciesBuild)((acc, module) =>
      acc ++ module.dependenciesCompile ++ module.dependenciesTest
    )
}

object RepositoryModules {
  val reads: Reads[RepositoryModules] = {
    implicit val dr  = Dependency.reads
    implicit val rmr = RepositoryModule.reads
    ( (__ \ "name"             ).read[String]
    ~ (__ \ "version"          ).readNullable[Version](Version.format)
    ~ (__ \ "dependenciesBuild").read[Seq[Dependency]]
    ~ (__ \ "modules"          ).read[Seq[RepositoryModule]]
    )(RepositoryModules.apply _)
  }
}

case class RepositoryModule(
  name               : String,
  group              : String,
  dependenciesCompile: Seq[Dependency],
  dependenciesTest   : Seq[Dependency],
  crossScalaVersions : Seq[Version]
)

object RepositoryModule {
  val reads: Reads[RepositoryModule] = {
    implicit val dr = Dependency.reads
    implicit val vf  = Version.format
    ( (__ \ "name"               ).read[String]
    ~ (__ \ "group"              ).read[String]
    ~ (__ \ "dependenciesCompile").read[Seq[Dependency]]
    ~ (__ \ "dependenciesTest"   ).read[Seq[Dependency]]
    ~ (__ \ "crossScalaVersions" ).read[Seq[Version]]
    )(RepositoryModule.apply _)
  }
}

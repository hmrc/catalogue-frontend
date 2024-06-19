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

package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{__, Format, Reads}
import play.api.mvc.{PathBindable, QueryStringBindable}

case class Username(value: String) extends AnyVal // TODO asString

case class TeamName(asString: String) extends AnyVal

object TeamName {
  lazy val format: Format[TeamName] =
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
}


case class RepositoryModules(
  name             : String,
  version          : Option[Version],
  dependenciesBuild: Seq[Dependency],
  modules          : Seq[RepositoryModule]
) {
  def allDependencies: Seq[Dependency] =
    modules.foldLeft(dependenciesBuild)((acc, module) =>
      acc ++ module.dependenciesCompile ++ module.dependenciesProvided ++ module.dependenciesTest ++ module.dependenciesIt
    )
}

object RepositoryModules {
  val reads: Reads[RepositoryModules] = {
    implicit val dr : Reads[Dependency]       = Dependency.reads
    implicit val rmr: Reads[RepositoryModule] = RepositoryModule.reads
    ( (__ \ "name"             ).read[String]
    ~ (__ \ "version"          ).readNullable[Version](Version.format)
    ~ (__ \ "dependenciesBuild").read[Seq[Dependency]]
    ~ (__ \ "modules"          ).read[Seq[RepositoryModule]]
    )(RepositoryModules.apply)
  }
}

case class RepositoryModule(
  name                : String,
  group               : String,
  dependenciesCompile : Seq[Dependency],
  dependenciesProvided: Seq[Dependency],
  dependenciesTest    : Seq[Dependency],
  dependenciesIt      : Seq[Dependency],
  crossScalaVersions  : Seq[Version],
  activeBobbyRules    : Seq[BobbyRuleViolation],
  pendingBobbyRules   : Seq[BobbyRuleViolation]
)

object RepositoryModule {
  val reads: Reads[RepositoryModule] = {
    implicit val bf: Reads[BobbyRuleViolation] = BobbyRuleViolation.format
    implicit val dr: Reads[Dependency]         = Dependency.reads
    implicit val vf: Reads[Version]            = Version.format
    ( (__ \ "name"                ).read[String]
    ~ (__ \ "group"               ).read[String]
    ~ (__ \ "dependenciesCompile" ).read[Seq[Dependency]]
    ~ (__ \ "dependenciesProvided").read[Seq[Dependency]]
    ~ (__ \ "dependenciesTest"    ).read[Seq[Dependency]]
    ~ (__ \ "dependenciesIt"      ).read[Seq[Dependency]]
    ~ (__ \ "crossScalaVersions"  ).read[Seq[Version]]
    ~ (__ \ "activeBobbyRules"    ).read[Seq[BobbyRuleViolation]]
    ~ (__ \ "pendingBobbyRules"   ).read[Seq[BobbyRuleViolation]]
    )(RepositoryModule.apply)
  }
}

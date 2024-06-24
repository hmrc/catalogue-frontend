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
import play.api.libs.json.{__, Reads}
import uk.gov.hmrc.cataloguefrontend.model.Version

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
    given Reads[Dependency]       = Dependency.reads
    given Reads[RepositoryModule] = RepositoryModule.reads
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
    given Reads[BobbyRuleViolation] = BobbyRuleViolation.format
    given Reads[Dependency]         = Dependency.reads
    given Reads[Version]            = Version.format
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

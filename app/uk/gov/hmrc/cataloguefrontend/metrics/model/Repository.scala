/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.metrics.model

case class Repository(name: RepositoryName, dependencies: Map[GroupName, Seq[DependencyName]]){
  val allDependencies: Seq[DependencyName] = dependencies.values.toSeq.flatten.distinct

}

object Repository{
  def apply(metrics: Seq[ServiceProgressMetrics]): Seq[Repository] = metrics
    .groupBy(m => RepositoryName(m.repository))
    .mapValues(values =>
      values
        .groupBy(m => GroupName(m.group))
        .mapValues(_.map(m => DependencyName(m.name)).distinct)
    )
    .map{ case (repoName, mapping) =>
      Repository(repoName, dependencies = mapping)
    }
    .toSeq
}

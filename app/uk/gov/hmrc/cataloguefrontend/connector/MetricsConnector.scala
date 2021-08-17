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

package uk.gov.hmrc.cataloguefrontend.connector

import cats.implicits.none
import com.google.inject.ImplementedBy
import uk.gov.hmrc.cataloguefrontend.connector.model.{DependencyName, Group, GroupName, MetricsResponse, Repository, RepositoryName, ServiceProgressMetrics}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MetricsConnector.Mocked])
trait MetricsConnector {
  def query(
             maybeGroup: Option[GroupName],
             maybeName: Option[DependencyName],
             maybeRepository: Option[RepositoryName]
           ): Future[MetricsResponse]

  // will return only the ones that have repos that have at least one dependency
  def getAllGroups: Future[Seq[Group]]

  // will return only the ones that have at least one dependency
  def getAllRepositories: Future[Seq[Repository]]

  def getAllDependencies: Future[Seq[DependencyName]]
}

object MetricsConnector{

  abstract class ViaQuery(implicit ec: ExecutionContext) extends MetricsConnector {
    override def getAllGroups: Future[Seq[Group]] = query(none, none, none)
      .map(_.metrics)
      .map(Group.apply)

    override def getAllRepositories: Future[Seq[Repository]] = query(none, none, none)
      .map(_.metrics)
      .map(Repository.apply)

    override def getAllDependencies: Future[Seq[DependencyName]] = query(none, none, none)
      .map(_.metrics)
      .map(DependencyName.apply)
  }

  class Mocked @Inject()(implicit val ec: ExecutionContext) extends ViaQuery {
    val allData = Seq(
      ServiceProgressMetrics(
        name = "frontend-bootstrap",
        group = "dwp.gov.uk",
        repository = "activity-logger",
        isHappy = true
      ),
      ServiceProgressMetrics(
        name = "frontend-bootstrap",
        group = "dwp.gov.uk",
        repository = "adobe-adaptive-forms",
        isHappy = false
      ),
      ServiceProgressMetrics(
        name = "bootstrap-play-25",
        group = "hmrc.gov.uk",
        repository = "adobe-adaptive-forms",
        isHappy = false
      ),
      ServiceProgressMetrics(
        name = "sbt-plugin",
        group = "hmrc.gov.uk",
        repository = "adobe-adaptive-forms",
        isHappy = true
      )
    )

    override def query(maybeGroup: Option[GroupName], maybeName: Option[DependencyName], maybeRepository: Option[RepositoryName]): Future[MetricsResponse] = Future.successful(
      MetricsResponse(
        metrics = allData.filter( metrics =>
          maybeGroup.forall(_.value == metrics.group) &&
            maybeName.forall(_.value == metrics.name) &&
            maybeRepository.forall(_.value == metrics.repository)
        )
      )
    )
  }


}
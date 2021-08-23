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

package uk.gov.hmrc.cataloguefrontend.metrics.connector

import cats.implicits.none
import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.metrics.model._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MetricsConnector.Impl])
trait MetricsConnector {
  def query(
             maybeGroup: Option[GroupName],
             maybeName: Option[DependencyName],
             maybeRepository: Option[RepositoryName]
           ): Future[MetricsResponse]

  def getAllGroups: Future[Seq[Group]]

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

  class Impl @Inject() (
    httpClient: HttpClient,
    servicesConfig: ServicesConfig
  )(implicit val ec: ExecutionContext) extends ViaQuery {
    import uk.gov.hmrc.http._

    private implicit val hc: HeaderCarrier = HeaderCarrier()
    private val platformProgressMetricsBaseURL: String = servicesConfig.baseUrl("platform-progress-metrics")
    implicit val rF: Reads[MetricsResponse] = MetricsResponse.reads
    val logger                           = Logger(this.getClass)

    import cats.syntax.option._

    override def query(maybeGroup: Option[GroupName], maybeName: Option[DependencyName], maybeRepository: Option[RepositoryName]): Future[MetricsResponse] = {
      val url = url"$platformProgressMetricsBaseURL/platform-progress-metrics/metrics" + List(
        maybeGroup.map(g => s"group=${g.value}").orEmpty,
        maybeName.map(n => s"name=${n.value}").orEmpty,
        maybeRepository.map(r => s"repository=${r.value}").orEmpty
      ).filter(_.nonEmpty)
        .mkString("?", "&", "")

      httpClient
        .GET[MetricsResponse](
          url
        )
        .recoverWith {
          case UpstreamErrorResponse.Upstream5xxResponse(x) =>
            logger.error(s"An error occurred when connecting to serviceDependencies. baseUrl: $platformProgressMetricsBaseURL", x)
            Future.successful(MetricsResponse(Seq.empty))
        }
    }
  }
}

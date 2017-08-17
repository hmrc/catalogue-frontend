/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.report

import java.util.Date

import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cataloguefrontend.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

case class DependencyReport(repository: String,               // teamsAndRepositoriesConnector.allRepositories
                            repoType: String,                 // teamsAndRepositoriesConnector.allRepositories
                            team: String,                     // findTeamNames(_,_)
                            digitalService: String,           // findDigitalServiceName
                            dependencyName: String,           //
                            dependencyType: String,           //
                            currentVersion: String,          //
                            latestVersion: String,
                            colour: String,
                            timestamp: Long = new Date().getTime)

object DependencyReport {
  implicit val format = Json.format[DependencyReport]
}


trait DependencyReportRepository {
  def add(DependencyReport: DependencyReport): Future[Boolean]

  def getAllDependencyReports: Future[Seq[DependencyReport]]
  def clearAllData: Future[Boolean]
}

class MongoDependencyReportRepository(mongo: () => DB)
  extends ReactiveRepository[DependencyReport, BSONObjectID](
    collectionName = "DependencyReport",
    mongo = mongo,
    domainFormat = DependencyReport.format) with DependencyReportRepository {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("repository" -> IndexType.Descending), name = Some("DependencyReportRepositoryIdx")))
      )
    )

  override def add(DependencyReport: DependencyReport): Future[Boolean] = {
    withTimerAndCounter("mongo.write") {
      insert(DependencyReport) map {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }



  override def getAllDependencyReports: Future[List[DependencyReport]] = findAll()

  override def clearAllData: Future[Boolean] = super.removeAll().map(!_.hasErrors)

}

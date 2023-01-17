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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.CurationStatus.{ActionRequired, InvestigationOngoing, NoActionRequired}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VulnerabilitiesService @Inject() (
  vulnerabilitiesConnector: VulnerabilitiesConnector
)(implicit
  ec: ExecutionContext
) {

  def getVulnerabilityCounts(service: Option[String], team: Option[String], environment: Option[Environment])(implicit hc: HeaderCarrier): Future[Seq[TotalVulnerabilityCount]] = {
    for {
      filteredCounts <- vulnerabilitiesConnector.vulnerabilityCounts(service, team, environment)

      countsByService = filteredCounts.foldLeft(Map.empty[String, TotalVulnerabilityCount])((acc, cur) => {
        val record = acc.getOrElse(cur.service, TotalVulnerabilityCount(cur.service, 0, 0, 0))
        val updatedRecord = cur.curationStatus match {
          case ActionRequired       => record.copy(actionRequired = record.actionRequired + cur.count)
          case NoActionRequired     => record.copy(noActionRequired = record.noActionRequired + cur.count)
          case InvestigationOngoing => record.copy(investigationOngoing = record.investigationOngoing + cur.count)
        }

        acc + (cur.service -> updatedRecord)

      }).values.toSeq.sortBy(_.service)
    } yield countsByService
  }


}

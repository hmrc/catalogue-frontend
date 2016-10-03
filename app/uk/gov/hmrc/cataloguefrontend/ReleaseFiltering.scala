/*
 * Copyright 2016 HM Revenue & Customs
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

import java.time.LocalDateTime

import uk.gov.hmrc.cataloguefrontend.DateHelper._

object ReleaseFiltering {

  implicit class ReleasesResult(releases: Seq[Release]) {

    def filter(query: ReleasesFilter): Seq[Release] = {

      val q = if (query.isEmpty) ReleasesFilter(from = Some(LocalDateTime.now().minusMonths(1))) else query

      releases.toStream
        .filter(x => q.team.isEmpty || q.team.get == x.team)
        .filter(x => q.serviceName.isEmpty || q.serviceName.get == x.name)
        .filter(x => q.from.isEmpty || x.productionDate.epochSeconds >= q.from.get.epochSeconds)
        .filter(x => q.to.isEmpty || x.productionDate.epochSeconds < q.to.get.plusDays(1).epochSeconds)

    }

  }

}

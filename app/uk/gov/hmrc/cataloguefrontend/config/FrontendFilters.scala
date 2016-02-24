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

package uk.gov.hmrc.cataloguefrontend.config

import com.kenshoo.play.metrics.MetricsFilter
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.filters.RecoveryFilter
import uk.gov.hmrc.play.filters.frontend.HeadersFilter
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

trait FrontendFilters {

  def loggingFilter: FrontendLoggingFilter

  def metricsFilter: MetricsFilter = MetricsFilter

  protected lazy val defaultFrontendFilters: Seq[EssentialFilter] = Seq(
    metricsFilter,
    HeadersFilter,
    loggingFilter,
    RecoveryFilter)

  def frontendFilters: Seq[EssentialFilter] = defaultFrontendFilters

}

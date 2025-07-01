/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.healthmetrics

import play.api.libs.json.Reads
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}
import FromStringEnum.*

given Parser[HealthMetric] = Parser.parser(HealthMetric.values)

enum HealthMetric(
  override val asString: String
, val displayString    : String
) extends FromString
  derives Ordering, Reads, FormFormat, QueryStringBindable:
  case LeakDetectionSummaries                  extends HealthMetric(asString = "LEAK_DETECTION_SUMMARIES"                    , displayString = "Leaks"                                                       )
  case ProductionBobbyErrors                   extends HealthMetric(asString = "PRODUCTION_BOBBY_ERRORS"                     , displayString = "Bobby Errors - Production"                                   )
  case LatestBobbyErrors                       extends HealthMetric(asString = "LATEST_BOBBY_ERRORS"                         , displayString = "Bobby Errors - Latest"                                       )
  case ProductionActionRequiredVulnerabilities extends HealthMetric(asString = "PRODUCTION_ACTION_REQUIRED_VULNERABILITIES"  , displayString = "Service Vulnerabilities - Production"                        )
  case LatestActionRequiredVulnerabilities     extends HealthMetric(asString = "LATEST_ACTION_REQUIRED_VULNERABILITIES"      , displayString = "Service Vulnerabilities - Latest"                            )
  case ProductionBobbyWarnings                 extends HealthMetric(asString = "PRODUCTION_BOBBY_WARNINGS"                   , displayString = "Bobby Warnings - Production"                                 )
  case LatestBobbyWarnings                     extends HealthMetric(asString = "LATEST_BOBBY_WARNINGS"                       , displayString = "Bobby Warnings - Latest"                                     )
  case ServiceCommissioningStateWarnings       extends HealthMetric(asString = "SERVICE_COMMISSIONING_STATE_WARNINGS"        , displayString = "Service Commissioning State Warnings"                        )
  case OutdatedOrHotFixedProductionDeployments extends HealthMetric(asString = "OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS", displayString = "Outdated or Hotfixed Production Deployments"                 )
  case PlatformInitiatives                     extends HealthMetric(asString = "PLATFORM_INITIATIVES"                        , displayString = "Platform Initiatives"                                        )
  case OpenPRRaisedByMembersOfTeam             extends HealthMetric(asString = "OPEN_PR_RAISED_BY_MEMBERS_OF_TEAM"           , displayString = "Open PRs raised by team members"                             )
  case OpenPRForOwnedRepos                     extends HealthMetric(asString = "OPEN_PR_FOR_OWNED_REPOS"                     , displayString = "Open PRs for own repositories"                               )
  case TestFailures                            extends HealthMetric(asString = "TEST_FAILURES"                               , displayString = "Test Failures"                                               )
  case AccessibilityAssessmentViolations       extends HealthMetric(asString = "ACCESSIBILITY_ASSESSMENT_VIOLATIONS"         , displayString = "Accessibility Assessment Violations"                         )
  case SecurityAssessmentAlerts                extends HealthMetric(asString = "SECURITY_ASSESSMENT_ALERTS"                  , displayString = "Security Assessment Alerts"                                  )
  case ContainerKills                          extends HealthMetric(asString = "CONTAINER_KILLS"                             , displayString = "Container kills"                                             )
  case NonIndexedQueries                       extends HealthMetric(asString = "NON_INDEXED_QUERIES"                         , displayString = "Non-indexed Queries"                                         )
  case SlowRunningQueries                      extends HealthMetric(asString = "SLOW_RUNNING_QUERIES"                        , displayString = "Slow-running Queries"                                        )
  case FrontendShutterStates                   extends HealthMetric(asString = "FRONTEND_SHUTTER_STATES"                     , displayString = "Shuttered Frontend - Production"                             )
  case ApiShutterStates                        extends HealthMetric(asString = "API_SHUTTER_STATES"                          , displayString = "Shuttered Api - Production"                                  )

object HealthMetric:
  def fromString(s: String): Option[HealthMetric] =
    HealthMetric.values.find(_.asString == s)

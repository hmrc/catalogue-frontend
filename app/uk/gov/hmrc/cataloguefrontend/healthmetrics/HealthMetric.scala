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
  derives Reads, FormFormat, QueryStringBindable:
  case OpenPRRaisedByMembersOfTeam             extends HealthMetric(asString = "OPEN_PR_RAISED_BY_MEMBERS_OF_TEAM"           , displayString = "Open pull requests raised by members of team"                )
  case OpenPRForReposOwnedByTeam               extends HealthMetric(asString = "OPEN_PR_FOR_REPOS_OWNED_BY_TEAM"             , displayString = "Open pull requests for repositories owned by team"           )
  case OpenPRForReposOwnedByDigitalService     extends HealthMetric(asString = "OPEN_PR_FOR_REPOS_OWNED_BY_DIGITAL_SERVICE"  , displayString = "Open pull requests for repositories owned by digital service")
  case LeakDetectionSummaries                  extends HealthMetric(asString = "LEAK_DETECTION_SUMMARIES"                    , displayString = "Leak detection summaries"                                    )
  case ProductionBobbyErrors                   extends HealthMetric(asString = "PRODUCTION_BOBBY_ERRORS"                     , displayString = "Production bobby rule errors"                                )
  case LatestBobbyErrors                       extends HealthMetric(asString = "LATEST_BOBBY_ERRORS"                         , displayString = "Latest bobby rule errors"                                    )
  case ProductionBobbyWarnings                 extends HealthMetric(asString = "PRODUCTION_BOBBY_WARNINGS"                   , displayString = "Production bobby rule warnings"                              )
  case LatestBobbyWarnings                     extends HealthMetric(asString = "LATEST_BOBBY_WARNINGS"                       , displayString = "Latest bobby rule warnings"                                  )
  case FrontendShutterStates                   extends HealthMetric(asString = "FRONTEND_SHUTTER_STATES"                     , displayString = "Frontend shutter states"                                     )
  case ApiShutterStates                        extends HealthMetric(asString = "API_SHUTTER_STATES"                          , displayString = "API shutter states"                                          )
  case PlatformInitiatives                     extends HealthMetric(asString = "PLATFORM_INITIATIVES"                        , displayString = "Platform initiatives"                                        )
  case ProductionActionRequiredVulnerabilities extends HealthMetric(asString = "PRODUCTION_ACTION_REQUIRED_VULNERABILITIES"  , displayString = "Production vulnerabilities requiring action"                 )
  case LatestActionRequiredVulnerabilities     extends HealthMetric(asString = "LATEST_ACTION_REQUIRED_VULNERABILITIES"      , displayString = "Latest vulnerabilities requiring action"                     )
  case ServiceCommissioningStateWarnings       extends HealthMetric(asString = "SERVICE_COMMISSIONING_STATE_WARNINGS"        , displayString = "Service commissioning state warnings"                        )
  case ContainerKills                          extends HealthMetric(asString = "CONTAINER_KILLS"                             , displayString = "Container kills"                                             )
  case NonIndexedQueries                       extends HealthMetric(asString = "NON_INDEXED_QUERIES"                         , displayString = "Non-indexed mongo queries"                                   )
  case SlowRunningQueries                      extends HealthMetric(asString = "SLOW_RUNNING_QUERIES"                        , displayString = "Slow-running mongo queries"                                  )
  case OutdatedOrHotFixedProductionDeployments extends HealthMetric(asString = "OUTDATED_OR_HOT_FIXED_PRODUCTION_DEPLOYMENTS", displayString = "Outdated or hot fixed production deployments"                )
  case TestFailures                            extends HealthMetric(asString = "TEST_FAILURES"                               , displayString = "Test failures"                                               )
  case AccessibilityAssessmentViolations       extends HealthMetric(asString = "ACCESSIBILITY_ASSESSMENT_VIOLATIONS"         , displayString = "Accessibility assessment violations"                         )
  case SecurityAssessmentAlerts                extends HealthMetric(asString = "SECURITY_ASSESSMENT_ALERTS"                  , displayString = "Security assessment alerts"                                  )


object HealthMetric:
  def fromString(s: String): Option[HealthMetric] =
    HealthMetric.values.find(_.asString == s)

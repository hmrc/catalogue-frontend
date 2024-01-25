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

package uk.gov.hmrc.cataloguefrontend.shuttering

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.ServiceName
import uk.gov.hmrc.internalauth.client.AuthenticatedRequest
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterService @Inject() (
  shutterConnector      : ShutterConnector,
  shutterGroupsConnector: ShutterGroupsConnector,
  routeRulesConnector   : RouteRulesConnector
)(implicit
  ec: ExecutionContext
) {

  def getShutterStates(
    st         : ShutterType,
    env        : Environment,
    serviceName: Option[ServiceName] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[ShutterState]] =
    shutterConnector.shutterStates(st, env, serviceName)

  def updateShutterStatus(
    serviceName: ServiceName,
    context    : Option[String],
    st         : ShutterType,
    env        : Environment,
    status     : ShutterStatus
  )(implicit
    hc : HeaderCarrier,
    req: AuthenticatedRequest[_, _]
  ): Future[Unit] =
    shutterConnector.updateShutterStatus(req.authorizationToken, serviceName, context, st, env, status)

  def outagePage(
    env        : Environment,
    serviceName: ServiceName
  )(implicit
    hc: HeaderCarrier
  ): Future[Option[OutagePage]] =
    shutterConnector.outagePage(env, serviceName)

  def frontendRouteWarnings(
    env        : Environment,
    serviceName: ServiceName
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[FrontendRouteWarning]] =
    shutterConnector.frontendRouteWarnings(env, serviceName)

  def findCurrentStates(
    st : ShutterType,
    env: Environment
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[(ShutterState, Option[ShutterStateChangeEvent])]] =
    for {
      states <- shutterConnector.shutterStates(st, env)
      events <- shutterConnector.latestShutterEvents(st, env)
      sorted =  states
                  .map(state => (state, events.find(_.serviceName == state.serviceName.asString)))
                  .sortWith { case ((l, _), (r, _)) =>
                    if (l.status.value == r.status.value)
                      l.serviceName.asString < r.serviceName.asString
                    else l.status.value == ShutterStatusValue.Shuttered
                  }
    } yield sorted

  /** Creates an [[OutagePageStatus]] for each service based on the contents of [[OutagePage]] */
  def toOutagePageStatus(serviceNames: Seq[ServiceName], outagePages: List[OutagePage]): Seq[OutagePageStatus] =
    serviceNames.map { serviceName =>
      outagePages.find(_.serviceName == serviceName) match {
        case Some(outagePage) if outagePage.warnings.nonEmpty =>
          OutagePageStatus(
            serviceName = serviceName,
            warning     = Some(
                            ( outagePage.warnings.map(_.message).mkString("<br/>"),
                              outagePage.warnings.head.name match {
                                case "UnableToRetrievePage"        => "Default outage page will be displayed."
                                case "MalformedHTML"               => "Outage page will be sent as is, without updating templates."
                                case "DuplicateTemplateElementIDs" => "All matching elements will be updated"
                              }
                            )
                          )
          )
        case Some(outagePage) if outagePage.templatedMessages.isEmpty =>
          OutagePageStatus(
            serviceName = serviceName,
            warning     = Some(("No templatedMessage Element in outage-page", "Outage page will be sent as is."))
          )
        case Some(outagePage) if outagePage.templatedMessages.length > 1 =>
          OutagePageStatus(
            serviceName = serviceName,
            warning     = Some(("Multiple templatedMessage Element in outage-page", "All matching elements will be updated."))
          )
        case Some(_) =>
          OutagePageStatus(
            serviceName = serviceName,
            warning     = None
          )
        case None =>
          OutagePageStatus(
            serviceName = serviceName,
            warning     = Some(("No templatedMessage Element no outage-page", "Default outage page will be displayed."))
          )
      }
    }

  def shutterGroups: Future[Seq[ShutterGroup]] =
    shutterGroupsConnector.shutterGroups

  def lookupShutterRoute(serviceName: ServiceName, env: Environment)(implicit hc: HeaderCarrier): Future[Option[String]] =
    for {
      baseRoutes      <- routeRulesConnector.frontendRoutes(serviceName.asString)
      optFrontendPath =  for {
                           envRoute      <- baseRoutes.find(_.environment == env.asString).map(_.routes)
                           frontendRoute <- envRoute.find(_.isRegex == false)
                         } yield ShutterLinkUtils.mkLink(env, frontendRoute.frontendPath)

    } yield optFrontendPath
}

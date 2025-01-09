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

import cats.data.OptionT
import cats.implicits.*

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubProxyConnector, RouteConfigurationConnector}
import uk.gov.hmrc.cataloguefrontend.connector.RouteConfigurationConnector.RouteType
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, ServiceName, TeamName}
import uk.gov.hmrc.internalauth.client.AuthenticatedRequest
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import org.jsoup.nodes.Document

@Singleton
class ShutterService @Inject() (
  shutterConnector      : ShutterConnector,
  shutterGroupsConnector: ShutterGroupsConnector,
  routeRulesConnector   : RouteConfigurationConnector,
  githubConnector       : GitHubProxyConnector
)(using
  ExecutionContext
):

  def getShutterStates(
    st         : ShutterType,
    env        : Environment,
    serviceName: Option[ServiceName] = None
  )(using
    HeaderCarrier
  ): Future[Seq[ShutterState]] =
    shutterConnector.shutterStates(st, env, teamName = None, digitalService = None, serviceName)

  def updateShutterStatus(
    serviceName: ServiceName,
    context    : Option[String],
    st         : ShutterType,
    env        : Environment,
    status     : ShutterStatus
  )(using
    hc : HeaderCarrier,
    req: AuthenticatedRequest[?, ?]
  ): Future[Unit] =
    shutterConnector.updateShutterStatus(req.authorizationToken, serviceName, context, st, env, status)

  def outagePage(
    serviceName: ServiceName
  )(using
    HeaderCarrier
  ): Future[Option[OutagePage]] =
    shutterConnector.outagePage(serviceName)

  def outagePagePreview(
    serviceName     : ServiceName,
    templatedMessage: Option[String]
  )(using
    HeaderCarrier
  ): Future[Option[Document]] =
    (for
       template   <- OptionT(githubConnector.getGitHubProxyRaw("/shutter-api/HEAD/conf/default-outage-page.html.tmpl"))
       outagePage <- OptionT(outagePage(serviceName))
     yield outagePage.renderTemplate(template, templatedMessage)
    ).value

  def frontendRouteWarnings(
    env        : Environment,
    serviceName: ServiceName
  )(using
    HeaderCarrier
  ): Future[Seq[FrontendRouteWarning]] =
    shutterConnector.frontendRouteWarnings(env, serviceName)

  def findCurrentStates(
    st            : ShutterType,
    env           : Environment,
    teamName      : Option[TeamName],
    digitalService: Option[DigitalService]
  )(using
    HeaderCarrier
  ): Future[Seq[(ShutterState, Option[ShutterStateChangeEvent])]] =
    for
      states <- shutterConnector.shutterStates(st, env, teamName, digitalService, serviceName = None)
      events <- shutterConnector.latestShutterEvents(st, env)
    yield
       states
         .map(state => (state, events.find(_.serviceName == state.serviceName)))
         .sortBy: (s, _) =>
           (s.status.value, s.serviceName)

  def shutterGroups()(using HeaderCarrier): Future[Seq[ShutterGroup]] =
    shutterGroupsConnector.shutterGroups().map(_.sortBy(_.name))

  def lookupShutterRoute(
    serviceName: ServiceName,
    env        : Environment
  )(using
    HeaderCarrier
  ): Future[Option[String]] =
    for
      frontendRoutes <- routeRulesConnector.routes(Some(serviceName), Some(RouteType.Frontend), Some(env))
      shutterRoute   =  frontendRoutes.find(_.isRegex == false)
    yield shutterRoute.map: r =>
      ShutterLinkUtils.mkLink(env, r.path)

end ShutterService

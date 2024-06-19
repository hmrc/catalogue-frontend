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

package test

import javax.inject.Inject

import play.api.routing.{HandlerDef, Router}
import play.api.routing.sird._
import play.core.routing.GeneratedRouter
import play.core.routing._
import play.core.routing.HandlerInvokerFactory._
import uk.gov.hmrc.cataloguefrontend.teams.TeamsController

class Routes @Inject()(
  val errorHandler: play.api.http.HttpErrorHandler,
  teamsController: TeamsController,
  prefix: String
) extends GeneratedRouter {

  override def documentation: Seq[(String, String, String)] =
    Seq.empty

  override def withPrefix(addPrefix: String): Router =
    new Routes(errorHandler, teamsController, prefix + addPrefix)

  private val defaultPrefix: String = {
    if (this.prefix.endsWith("/")) "" else "/"
  }

  override def routes: Router.Routes = {
    case uk_gov_hmrc_cataloguefrontend_teams_TeamsController_outOfDateTeamDependencies2_route(params@_) =>
      call(params.fromPath[String]("teamName", None)) { (teamName) =>
        createInvoker(
          fakeCall   = teamsController.outOfDateTeamDependencies(fakeValue[String]),
          handlerDef = HandlerDef(
            classLoader    = this.getClass.getClassLoader,
            routerPackage  = "app",
            controller     = "uk.gov.hmrc.cataloguefrontend.teams.TeamsController",
            method         = "outOfDateTeamDependencies",
            parameterTypes = Seq(classOf[String]),
            verb           = "GET",
            path           = this.prefix + """teams/""" + "$" + """teamName<[^/]+>/out-of-date-dependencies""",
            comments       = "",
            modifiers      = Seq.empty
          )
        ).call(teamsController.outOfDateTeamDependencies(teamName))
      }
  }

  private lazy val uk_gov_hmrc_cataloguefrontend_teams_TeamsController_outOfDateTeamDependencies2_route = Route("GET",
    PathPattern(List(StaticPart(this.prefix), StaticPart(this.defaultPrefix), StaticPart("teams/"), DynamicPart("teamName", """[^/]+""", encodeable=true), StaticPart("/out-of-date-dependencies")))
  )
}

/*
private lazy val uk_gov_hmrc_cataloguefrontend_teams_TeamsController_outOfDateTeamDependencies2_route = Route("GET",
    PathPattern(List(StaticPart(this.prefix), StaticPart(this.defaultPrefix), StaticPart("teams/"), DynamicPart("teamName", """[^/]+""", encodeable=true), StaticPart("/out-of-date-dependencies")))
  )
  private lazy val uk_gov_hmrc_cataloguefrontend_teams_TeamsController_outOfDateTeamDependencies2_invoker = createInvoker(
    TeamsController_23.outOfDateTeamDependencies(fakeValue[String]),
    play.api.routing.HandlerDef(this.getClass.getClassLoader,
      "app",
      "uk.gov.hmrc.cataloguefrontend.teams.TeamsController",
      "outOfDateTeamDependencies",
      Seq(classOf[String]),
      "GET",
      this.prefix + """teams/""" + "$" + """teamName<[^/]+>/out-of-date-dependencies""",
      """""",
      Seq()
    )
  )*/

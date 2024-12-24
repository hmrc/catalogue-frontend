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

package uk.gov.hmrc.cataloguefrontend.test

import cats.implicits._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest}
import play.api.data.{Form, Forms}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.cataloguefrontend.test.view.html.TestJobListPage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestJobController @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def allTests(teamName: Option[TeamName], digitalService: Option[DigitalService]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      for
        teams           <- teamsAndRepositoriesConnector.allTeams()
        digitalServices <- teamsAndRepositoriesConnector.allDigitalServices()
        testJobs        <- teamsAndRepositoriesConnector.findTestJobs(
                             teamName.filter(_.asString.nonEmpty),
                             digitalService.filter(_.asString.nonEmpty)
                           )
      yield
        Ok(TestJobListPage(
          form.bindFromRequest(),
          testJobs.sortBy(_.jobName),
          teams,
          digitalServices
        ))

  case class Filter(
    team          : Option[TeamName],
    digitalService: Option[DigitalService]
  )

  lazy val form: Form[Filter] =
    Form(
      Forms.mapping(
        "teamName"       -> Forms.optional(Forms.of[TeamName]),
        "digitalService" -> Forms.optional(Forms.of[DigitalService])
      )(Filter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )


end TestJobController

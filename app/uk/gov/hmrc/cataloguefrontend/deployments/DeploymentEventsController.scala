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

package uk.gov.hmrc.cataloguefrontend.deployments

import cats.implicits._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.data.{Form, Forms}
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.deployments.view.html.DeploymentEventsPage
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.model.Environment.Production
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{DeploymentHistory, Pagination, ReleasesConnector}
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentEventsController @Inject()(
  releasesConnector            : ReleasesConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  deploymentEventsPage         : DeploymentEventsPage,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  import DeploymentEventsController._

  def deploymentEvents(env: Environment = Production): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(deploymentEventsPage(env, Seq.empty, Seq.empty, Pagination(0, pageSize, 0), formWithErrors))),
          validForm =>
            for
              deployments <- releasesConnector.deploymentHistory(
                               env,
                               from    = Some(validForm.from),
                               to      = Some(validForm.to),
                               team    = validForm.team,
                               service = validForm.service,
                               skip    = validForm.page.map(i => i * pageSize),
                               limit   = Some(pageSize)
                             )
              teams       <- teamsAndRepositoriesConnector.allTeams()
              pagination  =  Pagination(page = validForm.page.getOrElse(0), pageSize = pageSize, total = deployments.total)
            yield Ok(
              deploymentEventsPage(
                env,
                deployments.history,
                teams.sortBy(_.name.asString),
                pagination,
                form.fill(validForm)
              )
            )
        )

  def csv(env: Environment): Action[AnyContent] =
    BasicAuthAction.apply: request =>
      given MessagesRequest[AnyContent] = request
      form
        .bindFromRequest()
        .fold(
          formErrors => BadRequest(deploymentEventsPage(env, Seq.empty, Seq.empty, Pagination(0, pageSize, 0), formErrors))
        , formObject =>
            val stream =
              Source
                .unfoldAsync(0): skip =>
                  releasesConnector.deploymentHistory(
                    environment = env
                  , from        = Some(formObject.from)
                  , to          = Some(formObject.to)
                  , team        = formObject.team
                  , service     = formObject.service
                  , skip        = Some(skip)
                  , limit       = Some(500) // max backend allows
                  ).map:
                    case page if page.history.isEmpty => None
                    case page                         => Some((skip + page.history.size, CsvUtils.toCsv(toRows(page.history), includeTitle = skip == 0)))
                .flatMapConcat(xs => Source.single(ByteString(xs,  "UTF-8")))

            Result(
              header = ResponseHeader(200, Map("Content-Disposition" -> s"attachment; filename=\"mdtp-deployment-events-${env.asString}-${formObject.from}-${formObject.to}.csv\""))
            , body   = HttpEntity.Streamed(stream, None, Some("text/csv"))
            )
        )

end DeploymentEventsController

object DeploymentEventsController:


  case class SearchForm(
    from   : LocalDate,
    to     : LocalDate,
    team   : Option[String],
    service: Option[String],
    page   : Option[Int]
  )

  val pageSize: Int = 50

  def defaultFromTime(referenceDate: LocalDate = LocalDate.now()): LocalDate =
    referenceDate.minusDays(7)

  def defaultToTime(referenceDate: LocalDate = LocalDate.now()): LocalDate =
    referenceDate

  lazy val form: Form[SearchForm] =
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms
        .mapping(
          "from"    -> Forms.default(Forms.localDate(dateFormat), defaultFromTime()),
          "to"      -> Forms.default(Forms.localDate(dateFormat), defaultToTime()),
          "team"    -> Forms.optional(Forms.text),
          "service" -> Forms.optional(Forms.text),
          "page"    -> Forms.optional(Forms.number(min = 0))
        )(SearchForm.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying("To Date must be greater than or equal to From Date", f => !f.to.isBefore(f.from))
    )

  def toRows(seq: Seq[DeploymentHistory]): Seq[Seq[(String, String)]] =
    seq.map: x =>
      Seq(
        "Service"     -> x.name.asString
      , "Teams"       -> x.teams.map(_.asString).mkString("\"", ", ", "\"")
      , "Version"     -> x.version.original
      , "Deploy time" -> x.time.time.toString
      , "Deployer"    -> x.username.asString
      )

end DeploymentEventsController

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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.healthindicators.HealthIndicatorsFilter.form
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{HealthIndicatorsLeaderBoard, HealthIndicatorsPage, error_404_template}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HealthIndicatorsController @Inject() (
  healthIndicatorsConnector    : HealthIndicatorsConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  override val mcc             : MessagesControllerComponents,
  healthIndicatorsService      : HealthIndicatorsService,
  override val auth            : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def breakdownForRepo(name: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        indicator <- healthIndicatorsConnector.getIndicator(name)
        history   <- healthIndicatorsConnector.getHistoricIndicators(name)
        average   <- healthIndicatorsConnector.getAveragePlatformScore
        result = indicator match {
          case Some(indicator: Indicator) => Ok(HealthIndicatorsPage(indicator, history, average.map(_.averageScore)))
          case None                       =>  NotFound(error_404_template())
        }
      } yield result
    }

  def indicatorsForRepoType(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(HealthIndicatorsLeaderBoard(Seq.empty, Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for {
              indicatorsWithTeams <- healthIndicatorsService.findIndicatorsWithTeams(
                                       repoType       = validForm.repoType.getOrElse(RepoType.Service)
                                     , repoNameFilter = None // Use listjs filtering
                                     )
              teams               <- teamsAndRepositoriesConnector.allTeams
              indicators          =  indicatorsFilteredByTeam(indicatorsWithTeams, validForm.team)
            } yield Ok(HealthIndicatorsLeaderBoard(indicators, RepoType.values, teams.sortBy(_.name), form.fill(validForm)))
        )
    }

  private def indicatorsFilteredByTeam(indicatorsWithTeams: Seq[IndicatorsWithTeams], team: Option[String]): Seq[IndicatorsWithTeams] = team match {
    case Some(t) => indicatorsWithTeams.filter(i => i.owningTeams.map(v => v.asString).contains(t))
    case None => indicatorsWithTeams
  }

}

object HealthIndicatorsController {
  def getScoreColour(score: Int): String =
    score match {
      case x if x > 0    => "repo-score-green"
      case x if x > -100 => "repo-score-amber"
      case _             => "repo-score-red"
    }
}

case class HealthIndicatorsFilter(
  repoName: Option[String],
  team: Option[String] = None,
  repoType: Option[RepoType] = None
)

object HealthIndicatorsFilter {
  lazy val form: Form[HealthIndicatorsFilter] = Form(
    mapping(
      "repoName" -> optional(text),
      "team"     -> optional(text),
      "repoType" -> optional(text)
        .transform[Option[RepoType]](
          _.flatMap(s => RepoType.parse(s).toOption),
          _.map(_.asString)
        )
    )(HealthIndicatorsFilter.apply)(HealthIndicatorsFilter.unapply)
  )
}

sealed trait RepoType {
  def asString: String
}

object RepoType {
  val format: Format[RepoType] = new Format[RepoType] {
    override def reads(json: JsValue): JsResult[RepoType] =
      json.validate[String].flatMap {
        case "Service"   => JsSuccess(Service)
        case "Library"   => JsSuccess(Library)
        case "Prototype" => JsSuccess(Prototype)
        case "Test"      => JsSuccess(Test)
        case "Other"     => JsSuccess(Other)
        case "All Types" => JsSuccess(AllTypes)
        case s           => JsError(s"Invalid RepoType: $s")
      }

    override def writes(o: RepoType): JsValue =
      o match {
        case Service   => JsString("Service")
        case Library   => JsString("Library")
        case Prototype => JsString("Prototype")
        case Test      => JsString("Test")
        case Other     => JsString("Other")
        case AllTypes  => JsString("All Types")
        case s         => JsString(s"$s")
      }
  }
  def parse(s: String): Either[String, RepoType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid repoType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val values: List[RepoType] = List(
    Service,
    Library,
    Prototype,
    Test,
    Other,
    AllTypes
  )

  case object Service extends RepoType {
    override def asString: String = "Service"
  }
  case object Library extends RepoType {
    override def asString: String = "Library"
  }
  case object Prototype extends RepoType {
    override def asString: String = "Prototype"
  }
  case object Test extends RepoType {
    override def asString: String = "Test"
  }
  case object Other extends RepoType {
    override def asString: String = "Other"
  }
  case object AllTypes extends RepoType {
    override def asString: String = "All Types"
  }
}

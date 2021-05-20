/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{HealthIndicatorsLeaderBoard, HealthIndicatorsPage, error_404_template}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HealthIndicatorsController @Inject()(
  healthIndicatorsConnector: HealthIndicatorsConnector,
  mcc: MessagesControllerComponents,
  healthIndicatorsService: HealthIndicatorsService
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  def indicatorsForRepo(name: String): Action[AnyContent] =
    Action.async { implicit request =>
      healthIndicatorsConnector.getRepositoryRating(name).map {
        case Some(repositoryRating: RepositoryRating) => Ok(HealthIndicatorsPage(repositoryRating))
        case None                                     => NotFound(error_404_template())
      }
    }

  def indicatorsForRepoType(repoType: String, repositoryName: String): Action[AnyContent] =
    Action.async { implicit request =>
      RepoType.parse(repoType).fold(_ => Future.successful(Redirect(routes.HealthIndicatorsController.indicatorsForRepoType(RepoType.Service.asString, repositoryName))),
        r => for {
          repoRatingsWithTeams <- healthIndicatorsService.findRepoRatingsWithTeams(r)
        } yield Ok(HealthIndicatorsLeaderBoard(repoRatingsWithTeams, r, repositoryName, RepoType.values))
      )
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
  repoType: Option[RepoType] = None
)

object HealthIndicatorsFilter {
  lazy val form: Form[HealthIndicatorsFilter] = Form(
    mapping(
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
        case "Other"     => JsSuccess(Other)
        case "All Types" => JsSuccess(AllTypes)
        case s           => JsError(s"Invalid RepoType: $s")
      }

    override def writes(o: RepoType): JsValue =
      o match {
        case Service   => JsString("Service")
        case Library   => JsString("Library")
        case Prototype => JsString("Prototype")
        case Other     => JsString("Other")
        case AllTypes => JsString("All Types")
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
  case object Other extends RepoType {
    override def asString: String = "Other"
  }
  case object AllTypes extends RepoType {
    override def asString: String = "All Types"
  }
}

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

package uk.gov.hmrc.cataloguefrontend.createawebhook

import cats.data.EitherT

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.I18nSupport
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, ResourceType, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.CreateAWebhookPage

import javax.inject.{Inject, Singleton}
import java.net.URL
import scala.concurrent.ExecutionContext
import scala.util.Try

@Singleton
class CreateAWebhookController @Inject()(
   override val auth            : FrontendAuthComponents,
   override val mcc             : MessagesControllerComponents,
   createAWebhookPage           : CreateAWebhookPage,
   buildDeployApiConnector      : BuildDeployApiConnector,
   teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport {

  private val logger = Logger(getClass)

  def createWebhookPermission(serviceName: String): Predicate =
    Predicate.Permission(Resource.from("catalogue-frontend", s"services/$serviceName"), IAAction("CREATE_WEBHOOK"))

  private def retrieveRepositories()(implicit hc: HeaderCarrier) =
    EitherT.right[Result](teamsAndRepositoriesConnector.allRepositories())

  def createAWebhookLanding(repositoryName: Option[String]): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateAWebhookController.createAWebhookLanding(repositoryName),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_WEBHOOK")))
    ).async { implicit request =>
        (for {
          repositories  <- retrieveRepositories()
        } yield {
          val form = CreateWebhookForm.form.fill(
            CreateWebhookForm(repositoryName.getOrElse(""), Seq.empty, "")
          )
          Ok(createAWebhookPage(form, repositories))
        }).merge
      }

  def createAWebhook(): Action[AnyContent] =
    auth.authenticatedAction(
      continueUrl = routes.CreateAWebhookController.createAWebhookLanding(CreateWebhookForm.form.data.get("repositoryName")),
      retrieval   = Retrieval.locations(resourceType = Some(ResourceType("catalogue-frontend")), action = Some(IAAction("CREATE_WEBHOOK")))
    ) .async { implicit request =>
    CreateWebhookForm.form.bindFromRequest().fold(
      formWithErrors => retrieveRepositories().map(repositories => BadRequest(createAWebhookPage(formWithErrors, repositories))).merge,
      validForm      => {
        import uk.gov.hmrc.cataloguefrontend.routes
        (for {
          _   <- auth.authorised(Some(createWebhookPermission(validForm.repositoryName)))
          res <- scala.concurrent.Future.successful(Right("1234").withLeft[Result]) // buildDeployApiConnector.createAWebhook(validForm)
        } yield {
          res match {
            case Left(errMsg)    => logger.info(s"createAWebhook failed with: $errMsg")
            case Right(id)       => logger.info(s"Build and deploy api request id: ${id}:")
          }
          Redirect(routes.CatalogueController.repository(validForm.repositoryName)).flashing("infoMessage" -> "A new webhook has been created.")
        }).recoverWith {
          case ex: UpstreamErrorResponse if Seq(401, 403).contains(ex.statusCode) => retrieveRepositories()
            .map{repositories =>
              logger.info(s"User attempted to create a webhook on the unauthorised repo '${validForm.repositoryName}'")
              Forbidden(
                createAWebhookPage(CreateWebhookForm.form.fill(validForm).withGlobalError("Unauthorised. Please, choose a repository on which you have permissions."), repositories)
              )
            }.merge
        }
      }
    )
  }
}

sealed trait WebhookEventType { 
  val asString: String 
  def value(): String = asString.replace(" ", "_").toUpperCase()
}
object WebhookEventType {
  case object EventIssues                   extends WebhookEventType { override val asString = "Event Issues"   }
  case object EventIssueComment             extends WebhookEventType { override val asString = "Event Issue Comment"   }
  case object EventPullRequest              extends WebhookEventType { override val asString = "Event Pull Request" }
  case object EventPullRequestReviewComment extends WebhookEventType { override val asString = "Event Pull Request Review Comment"      }
  case object EventPush                     extends WebhookEventType { override val asString = "Event Push"     }

  val values: List[WebhookEventType] = List(EventIssues, EventIssueComment, EventPullRequest, EventPullRequestReviewComment, EventPush)

  def parse(s: String): Either[String, WebhookEventType] =
    values
      .find(_.value() == s)
      .toRight(s"Invalid webhookEvenType - should be one of: ${values.map(_.value()).mkString(", ")}")

  val format: Format[WebhookEventType] =
    new Format[WebhookEventType] {
      override def reads(json: JsValue): JsResult[WebhookEventType] =
        json match {
          case JsString(s) => parse(s).fold(msg => JsError(msg), rt => JsSuccess(rt))
          case _           => JsError("String value expected")
        }

      override def writes(rt: WebhookEventType): JsValue =
        JsString(rt.asString)
    }
}

case class CreateWebhookForm(
  repositoryName  : String,
  events          : Seq[String],
  webhookUrl      : String,
)

object CreateWebhookForm {

  val logger = Logger(getClass)

  def mkConstraint[T](constraintName: String)(constraint: T => Boolean, error: String): Constraint[T] =
    Constraint(constraintName)({ toBeValidated => if(constraint(toBeValidated)) Valid else Invalid(error) })

  val webhookUrlIsValid: (String => Boolean) = webhookUrl => Try(new URL(webhookUrl)).isSuccess
  val eventsAreValid: (Seq[String] => Boolean) = events => 
    events.nonEmpty && events.exists(e => WebhookEventType.values.exists(_.value() == e))

  implicit val writes: Writes[CreateWebhookForm] =
    ( (__ \ "repositoryName").write[String]
    ~ (__ \ "events"        ).write[Seq[String]]
    ~ (__ \ "webhookUrl"    ).write[String]
    )(unlift(CreateWebhookForm.unapply))

   val form: Form[CreateWebhookForm] = Form(
    mapping(
      "repositoryName"   -> nonEmptyText,
      "events"           -> seq(text).verifying(
        mkConstraint("constraints.eventsAreValid")(constraint = eventsAreValid, error = "Please, select a supported event type.")
      ),
      "webhookUrl"       -> nonEmptyText.verifying(
        mkConstraint("constraints.webhookUrlIsValid")(constraint = webhookUrlIsValid, error = "Webhook url is not valid.")
      ),
    )(CreateWebhookForm.apply)(CreateWebhookForm.unapply)
  )
}

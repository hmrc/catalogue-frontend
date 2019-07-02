/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.data.EitherT
import cats.instances.all._
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.i18n.MessagesProvider
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, MessagesControllerComponents, Request, Result, Session}
import play.twirl.api.Html
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.cataloguefrontend.connector.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.shuttering.{ routes => appRoutes }
import views.html.shuttering.shutterService.{Page1, Page2, Page3}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterServiceController @Inject()(
     mcc           : MessagesControllerComponents
   , shutterService: ShutterService
   , page1         : Page1
   , page2         : Page2
   , page3         : Page3
   )(implicit val ec: ExecutionContext) extends FrontendController(mcc) {

  import ShutterServiceController._

  private def start(form: Form[ShutterForm])(implicit request: Request[Any], messagesProvider: MessagesProvider): Future[Html] =
    for {
      shutterStates <- shutterService.getShutterStates
      envs          =  Seq(SlugInfoFlag.Production, SlugInfoFlag.ExternalTest, SlugInfoFlag.QA, SlugInfoFlag.Staging, SlugInfoFlag.Dev)
    } yield page1(form, shutterStates, envs)

  def step1Get(env: Option[String], serviceName: Option[String]) =
    Action.async { implicit request =>
      start(form.fill(ShutterForm(serviceName = "", env = ""))).map(Ok(_))
    }

  def step1Post =
    Action.async { implicit request =>
      form
        .bindFromRequest
        .fold(
            hasErrors = formWithErrors => start(formWithErrors).map(BadRequest(_))
          , success   = data           => Future(
                                            Redirect(appRoutes.ShutterServiceController.step2Get)
                                              .withSession(toSession(data))
                                          )
          )
    }

  def step2Get =
    Action { implicit request =>
      fromSession(request.session)
        .map(sf => Ok(page2(sf)))
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }

  def step2Post =
    Action.async { implicit request =>
      (for {
         sf <- EitherT.fromOption[Future](
                  fromSession(request.session)
                , Redirect(appRoutes.ShutterServiceController.step1Post)
                )
         _  <- EitherT.right[Result] {
                 shutterService
                   .shutterService(sf.serviceName, sf.env)
               }
       } yield Redirect(appRoutes.ShutterServiceController.step3Get)
      ).merge
    }

  def step3Get =
    Action { implicit request =>
      fromSession(request.session)
        .map(sf => Ok(page3(sf)).withNewSession)
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }


  def form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "serviceName" -> Forms.text.verifying(notEmpty)
        , "env"         -> Forms.text.verifying(notEmpty)
        )(ShutterForm.apply)(ShutterForm.unapply)
    )

  // Forms.nonEmpty, but has no constraint info label
    def notEmpty = {
    import play.api.data.validation._
    Constraint[String]("") { o =>
      if (o == null || o.trim.isEmpty) Invalid(ValidationError("error.required")) else Valid
    }
  }
}

object ShutterServiceController {
  case class ShutterForm(
      serviceName: String
    , env        : String
    )

  private val SessionKey = "ShutterServiceController"

  private implicit val shutterFormFormats = Json.format[ShutterForm]

  def toSession(sf: ShutterForm): (String, String) =
    (SessionKey -> Json.stringify(Json.toJson(sf)(shutterFormFormats)))

  def fromSession(session: Session): Option[ShutterForm] =
    for {
      js <- session.get(SessionKey)
      sf <- Json.parse(js).asOpt[ShutterForm]
    } yield sf
}

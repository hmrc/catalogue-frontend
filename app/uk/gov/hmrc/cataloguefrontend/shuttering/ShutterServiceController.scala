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
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthenticated
import uk.gov.hmrc.cataloguefrontend.connector.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.shuttering.{ routes => appRoutes }
import views.html.shuttering.shutterService.{Page1, Page2, Page3}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterServiceController @Inject()(
     mcc             : MessagesControllerComponents
   , shutterService  : ShutterService
   , page1           : Page1
   , page2           : Page2
   , page3           : Page3
   , umpAuthenticated: UmpAuthenticated
   )(implicit val ec : ExecutionContext
   ) extends FrontendController(mcc)
        with play.api.i18n.I18nSupport {

  import ShutterServiceController._

  private def start(form: Form[ShutterForm])(implicit request: Request[Any]): Future[Html] =
    for {
      shutterStates <- shutterService.getShutterStates
      envs          =  Environment.values
      states        =  Seq("shutter", "unshutter")
    } yield page1(form, shutterStates, envs, states)

  def step1Get(env: Option[String], serviceName: Option[String]) =
    umpAuthenticated.async { implicit request =>
      start(form.fill(ShutterForm(
          serviceName = serviceName.getOrElse("")
        , env         = env.getOrElse("")
        , state       = "" // TODO from shutterStates, set to toggle current value
        ))).map(Ok(_))
    }

  def step1Post =
    umpAuthenticated.async { implicit request =>
      (for {
         sf      <- form
                      .bindFromRequest
                      .fold(
                          hasErrors = formWithErrors => EitherT.left(start(formWithErrors).map(BadRequest(_)))
                        , success   = data           => EitherT.pure[Future, Result](data)
                        )
         env     <- Environment.parse(sf.env) match {
                      case Some(env) => EitherT.pure[Future, Result](env)
                      case None      => EitherT.left(start(form.bindFromRequest).map(BadRequest(_)))
                    }
         shutter =  Shutter(sf.serviceName, env, sf.state)
       } yield Redirect(appRoutes.ShutterServiceController.step2Get)
                .withSession(request.session + toSession(shutter))
      ).merge
    }

  def step2Get =
    umpAuthenticated { implicit request =>
      fromSession(request.session)
        .map(sf => Ok(page2(sf)))
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }

  def step2Post =
    umpAuthenticated.async { implicit request =>
      (for {
         shutter <- EitherT.fromOption[Future](
                        fromSession(request.session)
                      , Redirect(appRoutes.ShutterServiceController.step1Post)
                      )
         _       <- EitherT.right[Result] {
                        shutterService
                          .shutterService(shutter.serviceName, shutter.env)
                      }
       } yield Redirect(appRoutes.ShutterServiceController.step3Get)
      ).merge
    }

  def step3Get =
    umpAuthenticated { implicit request =>
      fromSession(request.session)
        .map(shutter => Ok(page3(shutter)).withSession(request.session - SessionKey))
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }


  def form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "serviceName" -> Forms.text.verifying(notEmpty)
        , "env"         -> Forms.text.verifying(notEmpty)
        , "state"       -> Forms.text.verifying(notEmpty)
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
    , state      : String
    )

  case class Shutter(
      serviceName: String
    , env        : Environment
    , state      : String
    )

  val SessionKey = "ShutterServiceController"

  private implicit val shutterFormats = {
    implicit val environmentFormats = Environment.format
    Json.format[Shutter]
  }

  def toSession(sf: Shutter): (String, String) =
    (SessionKey -> Json.stringify(Json.toJson(sf)(shutterFormats)))

  def fromSession(session: Session): Option[Shutter] =
    for {
      js <- session.get(SessionKey)
      sf <- Json.parse(js).asOpt[Shutter]
    } yield sf
}

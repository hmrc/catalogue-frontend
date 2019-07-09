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
import cats.syntax.all._
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
      statusValues  =  ShutterStatus.values
    } yield page1(form, shutterStates, envs, statusValues)

  def step1Get(env: Option[String], serviceName: Option[String]) =
    umpAuthenticated.async { implicit request =>
    for {
      shutterStates <- shutterService.getShutterStates
      shutter       =  if (serviceName.isDefined || env.isDefined) {
                          ShutterForm(
                              serviceNames = serviceName.toSeq
                            , env          = env.getOrElse("")
                            , status       = statusFor(shutterStates)(serviceName, env).fold("")(_.asString)
                            )
                        } else fromSession(request.session) match {
                          case Some(shutter) => ShutterForm(
                                                    serviceNames = shutter.serviceNames
                                                  , env          = shutter.env.asString
                                                  , status       = shutter.status.asString
                                                  )
                          case None          => ShutterForm(serviceNames = Seq.empty, env = "", status = "")
                        }
      html          <- start(form.fill(shutter)).map(Ok(_))
    } yield html
  }

  def statusFor(shutterStates: Seq[ShutterState])(optServiceName: Option[String], optEnv: Option[String]): Option[ShutterStatus] =
    for {
      serviceName <- optServiceName
      envStr      <- optEnv
      env         <- Environment.parse(envStr)
      status      <- shutterStates.find(_.name == serviceName).map(_.statusFor(env))
    } yield status match {
      case ShutterStatus.Shuttered   => ShutterStatus.Unshuttered
      case ShutterStatus.Unshuttered => ShutterStatus.Shuttered
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
         status  <- ShutterStatus.parse(sf.status) match {
                      case Some(status) => EitherT.pure[Future, Result](status)
                      case None         => EitherT.left(start(form.bindFromRequest).map(BadRequest(_)))
                    }
         shutter =  Shutter(sf.serviceNames, env, status)
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
         _       <- shutter.serviceNames.toList.traverse_[EitherT[Future, Result, ?], Unit] { serviceName =>
                      EitherT.right[Result] {
                        shutterService
                          .updateShutterStatus(serviceName, shutter.env, shutter.status)
                      }
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
          "serviceName" -> Forms.seq(Forms.text).verifying(notEmptySeq)
        , "env"         -> Forms.text.verifying(notEmpty)
        , "status"      -> Forms.text.verifying(notEmpty)
        )(ShutterForm.apply)(ShutterForm.unapply)
    )

  // Forms.nonEmpty, but has no constraint info label
  def notEmpty = {
    import play.api.data.validation._
    Constraint[String]("") { o =>
      if (o == null || o.trim.isEmpty) Invalid(ValidationError("error.required")) else Valid
    }
  }

  def notEmptySeq = {
    import play.api.data.validation._
    Constraint[Seq[String]]("") { o =>
      if (o == null || o.isEmpty) Invalid(ValidationError("error.required")) else Valid
    }
  }
}

object ShutterServiceController {
  case class ShutterForm(
      serviceNames: Seq[String]
    , env         : String
    , status      : String
    )

  case class Shutter(
      serviceNames: Seq[String]
    , env         : Environment
    , status      : ShutterStatus
    )

  val SessionKey = "ShutterServiceController"

  private implicit val shutterFormats = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format
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

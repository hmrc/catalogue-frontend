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
import views.html.shuttering.shutterService.{Page1, Page2, Page3, Page4}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterServiceController @Inject()(
     mcc             : MessagesControllerComponents
   , shutterService  : ShutterService
   , page1           : Page1
   , page2           : Page2
   , page3           : Page3
   , page4           : Page4
   , umpAuthenticated: UmpAuthenticated
   )(implicit val ec : ExecutionContext
   ) extends FrontendController(mcc)
        with play.api.i18n.I18nSupport {

  import ShutterServiceController._

  private def start(form: Form[Page1Form])(implicit request: Request[Any]): Future[Html] =
    for {
      shutterStates <- shutterService.getShutterStates
      envs          =  Environment.values
      statusValues  =  ShutterStatus.values
    } yield page1(form, shutterStates, envs, statusValues)

  def step1Get(env: Option[String], serviceName: Option[String]) =
    umpAuthenticated.async { implicit request =>
    for {
      shutterStates <- shutterService.getShutterStates
      page1f     =  if (serviceName.isDefined || env.isDefined) {
                          Page1Form(
                              serviceNames = serviceName.toSeq
                            , env          = env.getOrElse("")
                            , status       = statusFor(shutterStates)(serviceName, env).fold("")(_.asString)
                            )
                        } else page1OutFromSession(request.session) match {
                          case Some(page1Out) => Page1Form(
                                                     serviceNames = page1Out.serviceNames
                                                   , env          = page1Out.env.asString
                                                   , status       = page1Out.status.asString
                                                   )
                          case None          => Page1Form(serviceNames = Seq.empty, env = "", status = "")
                        }
      html          <- start(page1Form.fill(page1f)).map(Ok(_))
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
         sf      <- page1Form
                      .bindFromRequest
                      .fold(
                          hasErrors = formWithErrors => EitherT.left(start(formWithErrors).map(BadRequest(_)))
                        , success   = data           => EitherT.pure[Future, Result](data)
                        )
         env     <- Environment.parse(sf.env) match {
                      case Some(env) => EitherT.pure[Future, Result](env)
                      case None      => EitherT.left(start(page1Form.bindFromRequest).map(BadRequest(_)))
                    }
         status  <- ShutterStatus.parse(sf.status) match {
                      case Some(status) => EitherT.pure[Future, Result](status)
                      case None         => EitherT.left(start(page1Form.bindFromRequest).map(BadRequest(_)))
                    }
         page1Out =  Page1Out(sf.serviceNames, env, status)
       } yield Redirect(appRoutes.ShutterServiceController.step2Get)
                .withSession(request.session + page1OutToSession(page1Out))
      ).merge
    }

  def step2Get =
    umpAuthenticated.async { implicit request =>
    for {
      shutterStates <- shutterService.getShutterStates
      envs          =  Environment.values
      statusValues  =  ShutterStatus.values
      page2f        =  page2OutFromSession(request.session) match {
                          case Some(page2Result) => Page2Form(
                                                        env          = page2Result.env.asString
                                                      , status       = page2Result.status.asString
                                                      )
                          case None              => Page2Form(env = "", status = "")
                        }
      html          =  Ok(page2(page2Form.fill(page2f), envs, statusValues))
    } yield html
  }

  def step2Post =
    umpAuthenticated.async { implicit request =>
      val envs          =  Environment.values
      val statusValues  =  ShutterStatus.values
      (for {
         sf      <- page2Form
                      .bindFromRequest
                      .fold(
                          hasErrors = formWithErrors => EitherT.left(Future(BadRequest(page2(formWithErrors, envs, statusValues))))
                        , success   = data           => EitherT.pure[Future, Result](data)
                        )
         env     <- Environment.parse(sf.env) match {
                      case Some(env) => EitherT.pure[Future, Result](env)
                      case None      => EitherT.left(Future(BadRequest(page2(page2Form.bindFromRequest, envs, statusValues))))
                    }
         status  <- ShutterStatus.parse(sf.status) match {
                      case Some(status) => EitherT.pure[Future, Result](status)
                      case None         => EitherT.left(Future(BadRequest(page2(page2Form.bindFromRequest, envs, statusValues))))
                    }
         page2Out =  Page2Out(env, status)
       } yield Redirect(appRoutes.ShutterServiceController.step3Get)
                .withSession(request.session + page2OutToSession(page2Out))
      ).merge
    }

  def step3Get =
    umpAuthenticated { implicit request =>
      page1OutFromSession(request.session)
        .map(sf => Ok(page3(sf)))
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }

  def step3Post =
    umpAuthenticated.async { implicit request =>
      (for {
         shutter <- EitherT.fromOption[Future](
                        page1OutFromSession(request.session)
                      , Redirect(appRoutes.ShutterServiceController.step1Post)
                      )
         _       <- shutter.serviceNames.toList.traverse_[EitherT[Future, Result, ?], Unit] { serviceName =>
                      EitherT.right[Result] {
                        shutterService
                          .updateShutterStatus(serviceName, shutter.env, shutter.status)
                      }
                    }
       } yield Redirect(appRoutes.ShutterServiceController.step4Get)
      ).merge
    }

  def step4Get =
    umpAuthenticated { implicit request =>
      page1OutFromSession(request.session)
        .map(page1Out => Ok(page4(page1Out)).withSession(request.session - Page1SessionKey - Page2SessionKey))
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }
}

object ShutterServiceController {

  import uk.gov.hmrc.cataloguefrontend.util.FormUtils.{notEmpty, notEmptySeq}

  // -- Page 1 -------------------------

  def page1Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "serviceName" -> Forms.seq(Forms.text).verifying(notEmptySeq)
        , "env"         -> Forms.text.verifying(notEmpty)
        , "status"      -> Forms.text.verifying(notEmpty)
        )(Page1Form.apply)(Page1Form.unapply)
    )

  case class Page1Form(
      serviceNames: Seq[String]
    , env         : String
    , status      : String
    )

  case class Page1Out(
      serviceNames: Seq[String]
    , env         : Environment
    , status      : ShutterStatus
    )

  val Page1SessionKey = "ShutterServiceController.Page1"

  private implicit val page1OutFormats = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format
    Json.format[Page1Out]
  }

  def page1OutToSession(p1o: Page1Out): (String, String) =
    (Page1SessionKey -> Json.stringify(Json.toJson(p1o)(page1OutFormats)))

  def page1OutFromSession(session: Session): Option[Page1Out] =
    for {
      js <- session.get(Page1SessionKey)
      sf <- Json.parse(js).asOpt[Page1Out]
    } yield sf

  // -- Page 2 -------------------------

  def page2Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "env"         -> Forms.text.verifying(notEmpty)
        , "status"      -> Forms.text.verifying(notEmpty)
        )(Page2Form.apply)(Page2Form.unapply)
    )

  case class Page2Form(
      env         : String
    , status      : String
    )

  case class Page2Out(
      env         : Environment
    , status      : ShutterStatus
    )

  val Page2SessionKey = "ShutterServiceController.Page2"

  private implicit val page2OutFormats = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format
    Json.format[Page2Out]
  }

  def page2OutToSession(p2o: Page2Out): (String, String) =
    (Page2SessionKey -> Json.stringify(Json.toJson(p2o)(page2OutFormats)))

  def page2OutFromSession(session: Session): Option[Page2Out] =
    for {
      js <- session.get(Page2SessionKey)
      sf <- Json.parse(js).asOpt[Page2Out]
    } yield sf
}

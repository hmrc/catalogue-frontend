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

  // --------------------------------------------------------------------------
  // Step1
  //
  // Select Services, Environment, and shutter action
  // --------------------------------------------------------------------------

  private def showPage1(form: Form[Step1Form])(implicit request: Request[Any]): Future[Html] =
    for {
      shutterStates <- shutterService.getShutterStates
      envs          =  Environment.values
      statusValues  =  ShutterStatus.values
    } yield page1(form, shutterStates, envs, statusValues)

  def step1Get(env: Option[String], serviceName: Option[String]) =
    umpAuthenticated.async { implicit request =>
    for {
      shutterStates <- shutterService.getShutterStates
      step1f        =  if (serviceName.isDefined || env.isDefined) {
                         Step1Form(
                             serviceNames = serviceName.toSeq
                           , env          = env.getOrElse("")
                           , status       = statusFor(shutterStates)(serviceName, env).fold("")(_.asString)
                           )
                       } else fromSession(request.session).flatMap(_.step1) match {
                         case Some(step1Out) => Step1Form(
                                                    serviceNames = step1Out.serviceNames
                                                  , env          = step1Out.env.asString
                                                  , status       = step1Out.status.asString
                                                  )
                         case None          => Step1Form(serviceNames = Seq.empty, env = "", status = "")
                       }
      html          <- showPage1(step1Form.fill(step1f)).map(Ok(_))
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
         sf       <- step1Form
                       .bindFromRequest
                       .fold(
                           hasErrors = formWithErrors => EitherT.left(showPage1(formWithErrors).map(BadRequest(_)))
                         , success   = data           => EitherT.pure[Future, Result](data)
                         )
         env      <- Environment.parse(sf.env) match {
                       case Some(env) => EitherT.pure[Future, Result](env)
                       case None      => EitherT.left(showPage1(step1Form.bindFromRequest).map(BadRequest(_)))
                     }
         status   <- ShutterStatus.parse(sf.status) match {
                       case Some(status) => EitherT.pure[Future, Result](status)
                       case None         => EitherT.left(showPage1(step1Form.bindFromRequest).map(BadRequest(_)))
                     }
         step1Out =  Step1Out(sf.serviceNames, env, status)
       } yield status match {
         case ShutterStatus.Shuttered   => Redirect(appRoutes.ShutterServiceController.step2Get)
                                             .withSession(request.session + updateFlowState(request.session)(_.copy(step1 = Some(step1Out))))
         case ShutterStatus.Unshuttered => Redirect(appRoutes.ShutterServiceController.step3Get)
                                             .withSession(request.session + updateFlowState(request.session)(_.copy(step1 = Some(step1Out) , step2 = Some(Step2Out(reason = "", outageMessage = "")))))
       }
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step2
  //
  // Review outage pages (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------

  private def showPage2(form: Form[Step2Form], step1Out: Step1Out)(implicit request: Request[Any]): Future[Html] =
    for {
      outagePages           <- step1Out.serviceNames.toList.traverse[Future, Option[OutagePage]](serviceName =>
                                 shutterService
                                   .outagePageByAppAndEnv(serviceName, step1Out.env)
                               ).map(_.collect { case Some(op) => op })
      outageMessageTemplate = outagePages
                                .flatMap(_.templatedMessages)
                                .headOption
      requiresOutageMessage = outageMessageTemplate.isDefined || outagePages.flatMap(_.warnings).nonEmpty
      form2                 = step2Form.fill {
                                val s2f = form.get
                                lazy val defaultOutageMessage = outageMessageTemplate.fold("")(_.innerHtml)
                                if (s2f.outageMessage.isEmpty) s2f.copy(outageMessage = defaultOutageMessage) else s2f
                              }
    } yield page2(form2, step1Out, requiresOutageMessage, outagePages)


  def step2Get =
    umpAuthenticated.async { implicit request =>
      (for {
         step1Out      <- EitherT.fromOption[Future](
                              fromSession(request.session)
                                .flatMap(_.step1)
                            , Redirect(appRoutes.ShutterServiceController.step1Post)
                            )
         step2f        =  fromSession(request.session).flatMap(_.step2) match {
                            case Some(step2Out) => Step2Form(
                                                       reason        = step2Out.reason
                                                     , outageMessage = step2Out.outageMessage
                                                     )
                            case None           => Step2Form(reason = "", outageMessage = "")
                          }
         html          <- EitherT.right[Result](showPage2(step2Form.fill(step2f), step1Out).map(Ok(_)))
       } yield html
      ).merge
  }

  def step2Post =
    umpAuthenticated.async { implicit request =>
      val envs          =  Environment.values
      val statusValues  =  ShutterStatus.values
      (for {
         step1Out <- EitherT.fromOption[Future](
                         fromSession(request.session)
                           .flatMap(_.step1)
                       , Redirect(appRoutes.ShutterServiceController.step1Post)
                       )
         sf       <- step2Form
                       .bindFromRequest
                       .fold(
                           hasErrors = formWithErrors => EitherT.left(showPage2(formWithErrors, step1Out).map(BadRequest(_)))
                         , success   = data           => EitherT.pure[Future, Result](data)
                         )
         step2Out =  Step2Out(sf.reason, sf.outageMessage)
       } yield Redirect(appRoutes.ShutterServiceController.step3Get)
                .withSession(request.session + updateFlowState(request.session)(fs => fs.copy(step2 = Some(step2Out))))
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step3
  //
  // Confirm
  //
  // --------------------------------------------------------------------------

  private def showPage3(form3: Form[Step3Form], step1Out: Step1Out, step2Out: Step2Out)(implicit request: Request[Any]): Html = {
    val back      =  if (step1Out.status == ShutterStatus.Shuttered)
                       appRoutes.ShutterServiceController.step2Get
                     else appRoutes.ShutterServiceController.step1Post
    page3(form3, step1Out, step2Out, back)
  }

  def step3Get =
    umpAuthenticated { implicit request =>
      (for {
         flowState <- fromSession(request.session)
         step1Out  <- flowState.step1
         step2Out  <- flowState.step2
         html      =  showPage3(step3Form.fill(Step3Form(confirm = false)), step1Out, step2Out)
       } yield Ok(html)
      ).getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }

  def step3Post =
    umpAuthenticated.async { implicit request =>
      (for {
         step1Out <- EitherT.fromOption[Future](
                         fromSession(request.session).flatMap(_.step1)
                       , Redirect(appRoutes.ShutterServiceController.step1Post)
                       )
         step2Out <- EitherT.fromOption[Future](
                         fromSession(request.session).flatMap(_.step2)
                       , Redirect(appRoutes.ShutterServiceController.step2Get)
                       )
         _        <- step3Form
                       .bindFromRequest
                       .fold(
                           hasErrors = formWithErrors => EitherT.left(Future(showPage3(formWithErrors, step1Out, step2Out)).map(BadRequest(_)))
                         , success   = data           => EitherT.pure[Future, Result](())
                         )
         _        <- step1Out.serviceNames.toList.traverse_[EitherT[Future, Result, ?], Unit] { serviceName =>
                       EitherT.right[Result] {
                         shutterService
                           .updateShutterStatus(serviceName, step1Out.env, step1Out.status, step2Out.reason, step2Out.outageMessage)
                       }
                    }
       } yield Redirect(appRoutes.ShutterServiceController.step4Get)
      ).merge
    }


  // --------------------------------------------------------------------------
  // Step4
  //
  // Feedback from submission
  //
  // --------------------------------------------------------------------------

  def step4Get =
    umpAuthenticated { implicit request =>
      fromSession(request.session)
        .flatMap(_.step1)
        .map(step1Out => Ok(page4(step1Out)).withSession(request.session - SessionKey))
        .getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }
}

object ShutterServiceController {

  import uk.gov.hmrc.cataloguefrontend.util.FormUtils.{notEmpty, notEmptySeq}

  // -- Step 1 -------------------------

  def step1Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "serviceName" -> Forms.seq(Forms.text).verifying(notEmptySeq)
        , "env"         -> Forms.text.verifying(notEmpty)
        , "status"      -> Forms.text.verifying(notEmpty)
        )(Step1Form.apply)(Step1Form.unapply)
    )

  case class Step1Form(
      serviceNames: Seq[String]
    , env         : String
    , status      : String
    )

  case class Step1Out(
      serviceNames: Seq[String]
    , env         : Environment
    , status      : ShutterStatus
    )

  private implicit val step1OutFormats = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatus.format
    Json.format[Step1Out]
  }

  // -- Step 2 -------------------------

  def step2Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "reason"        -> Forms.text
        , "outageMessage" -> Forms.text
        )(Step2Form.apply)(Step2Form.unapply)
    )

  case class Step2Form(
      reason       : String
    , outageMessage: String
    )

  case class Step2Out(
      reason       : String
    , outageMessage: String
    )

  private implicit val step2OutFormats =
    Json.format[Step2Out]

  // -- Step 3 -------------------------

  def step3Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "confirm"        -> Forms.boolean
        )(Step3Form.apply)(Step3Form.unapply).verifying("You must check confirmation for Production", _.confirm == true)
    )

  case class Step3Form(
      confirm      : Boolean
    )


  // -- Flow State -------------------------


  val SessionKey = "ShutterServiceController"

  case class FlowState(
      step1: Option[Step1Out]
    , step2: Option[Step2Out]
    )

  private implicit val flowStateFormats = Json.format[FlowState]


  def updateFlowState(session: Session)(f: FlowState => FlowState): (String, String) = {
    val updatedState = f(fromSession(session).getOrElse(FlowState(None, None)))
    (SessionKey -> Json.stringify(Json.toJson(updatedState)))
  }

  def fromSession(session: Session): Option[FlowState] =
    for {
      js <- session.get(SessionKey)
      sf <- Json.parse(js).asOpt[FlowState]
    } yield sf
}

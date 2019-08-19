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
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result, Session}
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthActionBuilder
import uk.gov.hmrc.cataloguefrontend.shuttering.{routes => appRoutes}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.shuttering.shutterService._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterServiceController @Inject()(
  mcc: MessagesControllerComponents,
  shutterService: ShutterService,
  page1: Page1,
  page2a: Page2a,
  page2b: Page2b,
  page3: Page3,
  page4: Page4,
  umpAuthActionBuilder: UmpAuthActionBuilder
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc)
    with play.api.i18n.I18nSupport {

  import ShutterServiceController._

  val withGroup = umpAuthActionBuilder.withGroup("dev-tools")

  // --------------------------------------------------------------------------
  // Step1
  //
  // Select Services, Environment, and shutter action
  // --------------------------------------------------------------------------

  private def showPage1(form: Form[Step1Form])(implicit request: Request[Any]): Future[Html] =
    for {
      shutterStates <- shutterService.getShutterStates
      envs         = Environment.values
      statusValues = ShutterStatusValue.values
      shutterGroups <- shutterService.shutterGroups
    } yield page1(form, shutterStates, envs, statusValues, shutterGroups)

  def step1Get(env: Option[String], serviceName: Option[String]) =
    withGroup.async { implicit request =>
      for {
        shutterStates <- shutterService.getShutterStates
        step1f = if (serviceName.isDefined || env.isDefined) {
          Step1Form(
            serviceNames = serviceName.toSeq,
            env          = env.getOrElse(""),
            status       = statusFor(shutterStates)(serviceName, env).fold("")(_.asString)
          )
        } else
          fromSession(request.session).flatMap(_.step1) match {
            case Some(step1Out) =>
              Step1Form(
                serviceNames = step1Out.serviceNames,
                env          = step1Out.env.asString,
                status       = step1Out.status.asString
              )
            case None => Step1Form(serviceNames = Seq.empty, env = "", status = "")
          }
        html <- showPage1(step1Form.fill(step1f)).map(Ok(_))
      } yield html
    }

  def statusFor(shutterStates: Seq[ShutterState])(
    optServiceName: Option[String],
    optEnv: Option[String]): Option[ShutterStatusValue] =
    for {
      serviceName <- optServiceName
      envStr      <- optEnv
      env         <- Environment.parse(envStr)
      status      <- shutterStates.find(_.name == serviceName).map(_.statusFor(env).value)
    } yield
      status match {
        case ShutterStatusValue.Shuttered   => ShutterStatusValue.Unshuttered
        case ShutterStatusValue.Unshuttered => ShutterStatusValue.Shuttered
      }

  def step1Post =
    withGroup.async { implicit request =>
      (for {
        sf <- step1Form.bindFromRequest
               .fold(
                 hasErrors = formWithErrors => EitherT.left(showPage1(formWithErrors).map(BadRequest(_))),
                 success   = data => EitherT.pure[Future, Result](data)
               )
        env <- Environment.parse(sf.env) match {
                case Some(env) => EitherT.pure[Future, Result](env)
                case None      => EitherT.left(showPage1(step1Form.bindFromRequest).map(BadRequest(_)))
              }
        status <- ShutterStatusValue.parse(sf.status) match {
                   case Some(status) => EitherT.pure[Future, Result](status)
                   case None         => EitherT.left(showPage1(step1Form.bindFromRequest).map(BadRequest(_)))
                 }
        step1Out = Step1Out(sf.serviceNames, env, status)
      } yield
        status match {
          case ShutterStatusValue.Shuttered =>
            Redirect(appRoutes.ShutterServiceController.step2aGet)
              .withSession(request.session + updateFlowState(request.session)(_.copy(step1 = Some(step1Out))))
          case ShutterStatusValue.Unshuttered =>
            Redirect(appRoutes.ShutterServiceController.step3Get)
              .withSession(
                request.session + updateFlowState(request.session)(
                  _.copy(
                    step1  = Some(step1Out),
                    step2b = Some(Step2bOut(reason = "", outageMessage = "", requiresOutageMessage = false)))))
        }).merge
    }

  // --------------------------------------------------------------------------
  // Step2a
  //
  // Review frontend-route warnings (if any) (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------
  private def showPage2a(form2a: Form[Step2aForm], step1Out: Step1Out)(implicit request: Request[Any]): Future[Html] =
    for {
      frontendRouteWarnings <- step1Out.serviceNames.toList
                                .map(
                                  serviceName =>
                                    shutterService
                                      .frontendRouteWarningsByAppAndEnv(serviceName, step1Out.env)
                                      .map(w => ServiceAndRouteWarnings(serviceName, w)))
                                .sequence
    } yield page2a(form2a, step1Out, frontendRouteWarnings)

  def step2aGet =
    withGroup.async { implicit request =>
      (for {
        step1Out <- EitherT.fromOption[Future](
                     fromSession(request.session)
                       .flatMap(_.step1),
                     Redirect(appRoutes.ShutterServiceController.step1Post)
                   )
        step2af = fromSession(request.session).flatMap(_.step2a) match {
          case Some(step2aOut) =>
            Step2aForm(
              confirm = step2aOut.confirmed
            )
          case None => Step2aForm(confirm = false)
        }
        html <- EitherT.right[Result](showPage2a(step2aForm.fill(step2af), step1Out).map(Ok(_)))
      } yield html).merge
    }

  def step2aPost =
    withGroup.async { implicit request =>
      (for {
        step1Out <- EitherT.fromOption[Future](
                     fromSession(request.session)
                       .flatMap(_.step1),
                     Redirect(appRoutes.ShutterServiceController.step1Post)
                   )
        sf <- step2aForm.bindFromRequest
               .fold(
                 hasErrors = formWithErrors => EitherT.left(showPage2a(formWithErrors, step1Out).map(BadRequest(_))),
                 success   = data => EitherT.pure[Future, Result](data)
               )
        step2aOut = Step2aOut(sf.confirm)
      } yield
        Redirect(appRoutes.ShutterServiceController.step2bGet)
          .withSession(request.session + updateFlowState(request.session)(fs => fs.copy(step2a = Some(step2aOut))))).merge
    }

  // --------------------------------------------------------------------------
  // Step2b
  //
  // Review outage pages (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------

  private def showPage2b(form: Form[Step2bForm], step1Out: Step1Out)(implicit request: Request[Any]): Future[Html] =
    for {
      outagePages <- step1Out.serviceNames.toList
                      .traverse[Future, Option[OutagePage]](serviceName =>
                        shutterService
                          .outagePageByAppAndEnv(serviceName, step1Out.env))
                      .map(_.collect { case Some(op) => op })
      outageMessageTemplate = outagePages
        .flatMap(op => op.templatedMessages.map((op, _)))
        .headOption
      requiresOutageMessage = outageMessageTemplate.isDefined || outagePages.flatMap(_.warnings).nonEmpty
      outageMessageSrc      = outageMessageTemplate.map(_._1)
      defaultOutageMessage  = outageMessageTemplate.fold("")(_._2.innerHtml)
      outagePageStatus      = shutterService.toOutagePageStatus(step1Out.serviceNames, outagePages)
      back = if (step1Out.status == ShutterStatusValue.Shuttered)
        appRoutes.ShutterServiceController.step2aGet
      else appRoutes.ShutterServiceController.step1Post
      form2b = step2bForm.fill {
        val s2f = form.get
        if (s2f.outageMessage.isEmpty) s2f.copy(outageMessage = defaultOutageMessage) else s2f
      }
    } yield
      page2b(form2b, step1Out, requiresOutageMessage, outageMessageSrc, defaultOutageMessage, outagePageStatus, back)

  def step2bGet =
    withGroup.async { implicit request =>
      (for {
        step1Out <- EitherT.fromOption[Future](
                     fromSession(request.session)
                       .flatMap(_.step1),
                     Redirect(appRoutes.ShutterServiceController.step1Post)
                   )
        step2bf = fromSession(request.session).flatMap(_.step2b) match {
          case Some(step2bOut) =>
            Step2bForm(
              reason                = step2bOut.reason,
              outageMessage         = step2bOut.outageMessage,
              requiresOutageMessage = step2bOut.requiresOutageMessage
            )
          case None => Step2bForm(reason = "", outageMessage = "", requiresOutageMessage = true)
        }
        html <- EitherT.right[Result](showPage2b(step2bForm.fill(step2bf), step1Out).map(Ok(_)))
      } yield html).merge
    }

  def step2bPost =
    withGroup.async { implicit request =>
      (for {
        step1Out <- EitherT.fromOption[Future](
                     fromSession(request.session)
                       .flatMap(_.step1),
                     Redirect(appRoutes.ShutterServiceController.step1Post)
                   )
        sf <- step2bForm.bindFromRequest
               .fold(
                 hasErrors = formWithErrors => EitherT.left(showPage2b(formWithErrors, step1Out).map(BadRequest(_))),
                 success   = data => EitherT.pure[Future, Result](data)
               )
        step2bOut = Step2bOut(sf.reason, sf.outageMessage, sf.requiresOutageMessage)
      } yield
        Redirect(appRoutes.ShutterServiceController.step3Get)
          .withSession(request.session + updateFlowState(request.session)(fs => fs.copy(step2b = Some(step2bOut))))).merge
    }

  // --------------------------------------------------------------------------
  // Step3
  //
  // Confirm
  //
  // --------------------------------------------------------------------------

  private def showPage3(form3: Form[Step3Form], step1Out: Step1Out, step2Out: Step2bOut)(
    implicit request: Request[Any]): Html = {
    val back =
      if (step1Out.status == ShutterStatusValue.Shuttered)
        appRoutes.ShutterServiceController.step2bGet
      else appRoutes.ShutterServiceController.step1Post
    page3(form3, step1Out, step2Out, back)
  }

  def step3Get =
    withGroup { implicit request =>
      (for {
        flowState <- fromSession(request.session)
        step1Out  <- flowState.step1
        step2bOut <- flowState.step2b
        html = showPage3(step3Form.fill(Step3Form(confirm = false)), step1Out, step2bOut)
      } yield Ok(html)).getOrElse(Redirect(appRoutes.ShutterServiceController.step1Post))
    }

  def step3Post =
    withGroup.async { implicit request =>
      (for {
        step1Out <- EitherT.fromOption[Future](
                     fromSession(request.session).flatMap(_.step1),
                     Redirect(appRoutes.ShutterServiceController.step1Post)
                   )
        step2bOut <- EitherT.fromOption[Future](
                      fromSession(request.session).flatMap(_.step2b),
                      Redirect(appRoutes.ShutterServiceController.step2bGet)
                    )
        _ <- step3Form.bindFromRequest
              .fold(
                hasErrors = formWithErrors =>
                  EitherT.left(Future(showPage3(formWithErrors, step1Out, step2bOut)).map(BadRequest(_))),
                success = data => EitherT.pure[Future, Result](())
              )
        status = step1Out.status match {
          case ShutterStatusValue.Shuttered =>
            ShutterStatus.Shuttered(
              reason        = Some(step2bOut.reason).filter(_.nonEmpty),
              outageMessage = Some(step2bOut.outageMessage).filter(_.nonEmpty)
            )
          case ShutterStatusValue.Unshuttered => ShutterStatus.Unshuttered
        }
        _ <- step1Out.serviceNames.toList.traverse_[EitherT[Future, Result, ?], Unit] { serviceName =>
              EitherT.right[Result] {
                shutterService
                  .updateShutterStatus(request.token, serviceName, step1Out.env, status)
              }
            }
      } yield Redirect(appRoutes.ShutterServiceController.step4Get)).merge
    }

  // --------------------------------------------------------------------------
  // Step4
  //
  // Feedback from submission
  //
  // --------------------------------------------------------------------------

  def step4Get =
    withGroup { implicit request =>
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
        "serviceName" -> Forms.seq(Forms.text).verifying(notEmptySeq),
        "env"         -> Forms.text.verifying(notEmpty),
        "status"      -> Forms.text.verifying(notEmpty)
      )(Step1Form.apply)(Step1Form.unapply)
    )

  case class Step1Form(
    serviceNames: Seq[String],
    env: String,
    status: String
  )

  case class Step1Out(
    serviceNames: Seq[String],
    env: Environment,
    status: ShutterStatusValue
  )

  private implicit val step1OutFormats = {
    implicit val ef  = Environment.format
    implicit val ssf = ShutterStatusValue.format
    Json.format[Step1Out]
  }

  // -- Step 2a Review frontend-route warnings (if any) -------------------------
  def step2aForm(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms
        .mapping(
          "confirm" -> Forms.boolean
        )(Step2aForm.apply)(Step2aForm.unapply)
        .verifying("You must tick to confirm you have acknowledged the frontend-route warnings", _.confirm == true)
    )
  case class Step2aForm(confirm: Boolean)

  case class Step2aOut(
    confirmed: Boolean
  )

  private implicit val step2aOutFormats = {
    Json.format[Step2aOut]
  }

  // -- Step 2b Review outage-page message and warnings -------------------------

  def step2bForm(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
        "reason"                -> Forms.text,
        "outageMessage"         -> Forms.text,
        "requiresOutageMessage" -> Forms.boolean
      )(Step2bForm.apply)(Step2bForm.unapply)
    )

  case class Step2bForm(
    reason: String,
    outageMessage: String,
    requiresOutageMessage: Boolean
  )

  case class Step2bOut(
    reason: String,
    outageMessage: String,
    requiresOutageMessage: Boolean
  )

  private implicit val step2bOutFormats =
    Json.format[Step2bOut]

  // -- Step 3 -------------------------

  def step3Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms
        .mapping(
          "confirm" -> Forms.boolean
        )(Step3Form.apply)(Step3Form.unapply)
        .verifying(
          "You must tick to confirm you have acknowledged you are changing production services!",
          _.confirm == true)
    )

  case class Step3Form(
    confirm: Boolean
  )

  // -- Flow State -------------------------

  val SessionKey = "ShutterServiceController"

  case class FlowState(
    step1: Option[Step1Out],
    step2a: Option[Step2aOut],
    step2b: Option[Step2bOut]
  )

  case class ServiceAndRouteWarnings(serviceName: String, warnings: Seq[FrontendRouteWarning])

  private implicit val flowStateFormats = Json.format[FlowState]

  def updateFlowState(session: Session)(f: FlowState => FlowState): (String, String) = {
    val updatedState = f(fromSession(session).getOrElse(FlowState(None, None, None)))
    (SessionKey -> Json.stringify(Json.toJson(updatedState)))
  }

  def fromSession(session: Session): Option[FlowState] =
    for {
      js <- session.get(SessionKey)
      sf <- Json.parse(js).asOpt[FlowState]
    } yield sf
}

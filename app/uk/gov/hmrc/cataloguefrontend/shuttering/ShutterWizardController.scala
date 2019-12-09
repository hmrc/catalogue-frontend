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

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.instances.all._
import cats.syntax.all._
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.i18n.{Messages, MessagesProvider}
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthActionBuilder
import uk.gov.hmrc.cataloguefrontend.config.CatalogueConfig
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.cataloguefrontend.service.AuthService
import uk.gov.hmrc.cataloguefrontend.shuttering.{routes => appRoutes}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.shuttering.shutterService._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterWizardController @Inject()(
    mcc                          : MessagesControllerComponents
  , shutterService               : ShutterService
  , page1                        : Page1
  , page2a                       : Page2a
  , page2b                       : Page2b
  , page3                        : Page3
  , page4                        : Page4
  , umpAuthActionBuilder         : UmpAuthActionBuilder
  , authService                  : AuthService
  , catalogueConfig              : CatalogueConfig
  , routeRulesConnector          : RouteRulesConnector
  , mongoComponent               : MongoComponent
  , servicesConfig               : ServicesConfig
  , timestampSupport             : TimestampSupport
  )(implicit val ec: ExecutionContext)
    extends FrontendController(mcc)
       with play.api.i18n.I18nSupport {

  import ShutterWizardController._

  val cacheRepo = new SessionCacheRepository(
        mongoComponent   = mongoComponent
      , collectionName   = "sessions"
      , ttl              = servicesConfig.getDuration("mongodb.session.expireAfter")
      , timestampSupport = timestampSupport
      , sessionIdKey     = "ShutterWizardController"
      )

  implicit val s0f  = step0OutFormats
  implicit val s1f  = step1OutFormats
  implicit val s2af = step2aOutFormats
  implicit val s2bf = step2bOutFormats


  val withGroup = umpAuthActionBuilder.withGroup(catalogueConfig.shutterGroup)

  // --------------------------------------------------------------------------
  // Start
  //
  // --------------------------------------------------------------------------

  def start(shutterType: ShutterType, env: Environment, serviceName: Option[String]) =
    withGroup.async { implicit request =>
      for {
        updatedFlowState <- cacheRepo.put(step0Key, Step0Out(
                                shutterType = shutterType
                              , env         = env
                              ))
      } yield
        Redirect(appRoutes.ShutterWizardController.step1Get(serviceName))
          .withSession(request.session + updatedFlowState)
    }

  // --------------------------------------------------------------------------
  // Step1
  //
  // Select Services, Environment, and shutter action
  // --------------------------------------------------------------------------

  private def showPage1(shutterType: ShutterType, env: Environment, form: Form[Step1Form])(implicit request: Request[Any]): Future[Html] =
    for {
      shutterStates <- shutterService.getShutterStates(shutterType, env)
      envs          =  Environment.values
      statusValues  =  ShutterStatusValue.values
      shutterGroups <- shutterService.shutterGroups
      back = appRoutes.ShutterOverviewController.allStates(shutterType)
    } yield page1(form, shutterType, env, shutterStates, statusValues, shutterGroups, back)

  def step1Get(serviceName: Option[String]) =
    withGroup.async { implicit request =>
      (for {
         step0Out      <- getStep0Out
         shutterStates <- EitherT.liftF(shutterService.getShutterStates(step0Out.shutterType, step0Out.env))
         step1f        <- if (serviceName.isDefined) {
                            EitherT.pure[Future, Result](Step1Form(
                              serviceNames = serviceName.toSeq,
                              status       = statusFor(shutterStates)(serviceName).fold("")(_.asString)
                            ))
                          } else
                            EitherT.liftF[Future, Result, Step1Form] {
                              cacheRepo.get(step1Key)
                                .map {
                                  case Some(step1Out) =>
                                    Step1Form(
                                      serviceNames = step1Out.serviceNames,
                                      status       = step1Out.status.asString
                                    )
                                  case None => Step1Form(serviceNames = Seq.empty, status = "")
                                }
                            }
         html          <- EitherT.liftF[Future, Result, Result] {
                            showPage1(step0Out.shutterType, step0Out.env, step1Form.fill(step1f)).map(Ok(_))
                          }
       } yield html
      ).merge
    }

  def statusFor(shutterStates: Seq[ShutterState])(optServiceName: Option[String]): Option[ShutterStatusValue] =
    for {
      serviceName <- optServiceName
      status      <- shutterStates.find(_.name == serviceName).map(_.status.value)
    } yield
      status match {
        case ShutterStatusValue.Shuttered   => ShutterStatusValue.Unshuttered
        case ShutterStatusValue.Unshuttered => ShutterStatusValue.Shuttered
      }

  def step1Post =
    withGroup.async { implicit request =>
      (for {
         step0Out      <- getStep0Out
         boundForm     =  step1Form.bindFromRequest
         sf            <- boundForm
                            .fold(
                              hasErrors = formWithErrors => EitherT.left(showPage1(step0Out.shutterType, step0Out.env, formWithErrors).map(BadRequest(_)))
                            , success   = data           => EitherT.pure[Future, Result](data)
                            )

         status        <- ShutterStatusValue.parse(sf.status) match {
                            case Some(status) => EitherT.pure[Future, Result](status)
                            case None         => EitherT.left(showPage1(step0Out.shutterType, step0Out.env, boundForm).map(BadRequest(_)))
                          }

         // check has `shutter-platform` group or is authorized for selected services
         serviceNames  <- EitherT.fromOption[Future](NonEmptyList.fromList(sf.serviceNames.toList), ())
                           .leftFlatMap(_=> EitherT.left[NonEmptyList[String]](showPage1(step0Out.shutterType, step0Out.env, boundForm.withGlobalError(Messages("No services selected"))).map(BadRequest(_))))
         hasGlobalPerm =  request.user.groups.contains(catalogueConfig.shutterPlatformGroup)
         _             <- if (step0Out.shutterType != ShutterType.Frontend || hasGlobalPerm)
                            EitherT.pure[Future, Result](())
                          else
                            EitherT(authService.authorizeServices(serviceNames))
                              .leftFlatMap { case AuthService.ServiceForbidden(s) =>
                                val errorMessage = s"You do not have permission to shutter service(s): ${s.toList.mkString(", ")}"
                                EitherT.left[Unit](showPage1(step0Out.shutterType, step0Out.env, boundForm.withGlobalError(Messages(errorMessage))).map(Forbidden(_)))
                              }

         step1Out      =  Step1Out(sf.serviceNames, status)

         _             <- EitherT.liftF[Future, Result, (String, String)] {
                            status match {
                              case ShutterStatusValue.Shuttered if step0Out.shutterType == ShutterType.Frontend =>
                                cacheRepo.put(step1Key, step1Out)
                              case _ =>
                                for {
                                  _   <- cacheRepo.put(step1Key, step1Out)
                                  res <- cacheRepo.put(step2bKey, Step2bOut(
                                             reason                = ""
                                           , outageMessage         = ""
                                           , requiresOutageMessage = false
                                           , useDefaultOutagePage  = false
                                           ))
                                } yield res
                            }
                          }
       } yield
         status match {
           case ShutterStatusValue.Shuttered if step0Out.shutterType == ShutterType.Frontend =>
             Redirect(appRoutes.ShutterWizardController.step2aGet)
           case _ =>
             Redirect(appRoutes.ShutterWizardController.step3Get)
         }
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step2a
  //
  // Review frontend-route warnings (if any) (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------
  private def frontendRouteWarnings(
      env     : Environment
    , step1Out: Step1Out
    )(implicit request: Request[Any]): Future[List[ServiceAndRouteWarnings]] =
    step1Out.serviceNames.toList
      .traverse(
        serviceName =>
          shutterService
            .frontendRouteWarnings(env, serviceName)
            .map(w => ServiceAndRouteWarnings(serviceName, w)))

  private def showPage2a(
      form2a  : Form[Step2aForm]
    , env     : Environment
    , step1Out: Step1Out
    )(implicit request: Request[Any]): Future[Html] =
    frontendRouteWarnings(env, step1Out)
      .map(w => page2a(form2a, env, step1Out, w, appRoutes.ShutterWizardController.step1Get(None)))

  def step2aGet =
    withGroup.async { implicit request =>
      (for {
         step0Out <- getStep0Out
         step1Out <- getStep1Out
         step2af  <- EitherT.liftF {
                       cacheRepo.get(step2aKey).map {
                         case Some(step2aOut) => Step2aForm(confirm = step2aOut.confirmed)
                         case None            => Step2aForm(confirm = false)
                       }
                     }
        serviceAndRouteWarnings <- EitherT.right(frontendRouteWarnings(step0Out.env, step1Out))
        html      <- EitherT.right[Result](
                       if (serviceAndRouteWarnings.flatMap(_.warnings).nonEmpty)
                         showPage2a(step2aForm.fill(step2af), step0Out.env, step1Out).map(Ok(_))
                       else
                         //Skip straight to outage-page screen if there are no route warnings
                         cacheRepo.put(step2aKey, Step2aOut(confirmed = false, skipped = true))
                           .map(_ => Redirect(appRoutes.ShutterWizardController.step2bGet()))
                     )
       } yield html
      ).merge
    }

  def step2aPost =
    withGroup.async { implicit request =>
      (for {
         step0Out  <- getStep0Out
         step1Out  <- getStep1Out
         sf        <- step2aForm.bindFromRequest
                        .fold(
                          hasErrors = formWithErrors => EitherT.left(showPage2a(formWithErrors, step0Out.env, step1Out).map(BadRequest(_))),
                          success   = data           => EitherT.pure[Future, Result](data)
                        )
         step2aOut =  Step2aOut(sf.confirm, skipped = false)
         _         <- EitherT.liftF[Future, Result, (String, String)] {
                        cacheRepo.put(step2aKey, step2aOut)
                      }
       } yield
         Redirect(appRoutes.ShutterWizardController.step2bGet)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step2b
  //
  // Review outage pages (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------

  private def showPage2b(
      form     : Form[Step2bForm]
    , step1Out : Step1Out
    , step2aOut: Option[Step2aOut
    ])(implicit request: Request[Any]): EitherT[Future, Result, Html] =
    for {
      step0Out              <- getStep0Out
      outagePages           <- EitherT.liftF[Future, Result, List[OutagePage]] {
                                 step1Out.serviceNames.toList
                                   .traverse[Future, Option[OutagePage]](serviceName =>
                                     shutterService
                                       .outagePage(step0Out.env, serviceName))
                                   .map(_.collect { case Some(op) => op })
                               }
      outageMessageTemplate =  outagePages
                                 .flatMap(op => op.templatedMessages.map((op, _)))
                                 .headOption
      requiresOutageMessage =  outageMessageTemplate.isDefined || outagePages.flatMap(_.warnings).nonEmpty
      outageMessageSrc      =  outageMessageTemplate.map(_._1)
      defaultOutageMessage  =  outageMessageTemplate.fold("")(_._2.innerHtml)
      outagePageStatus      =  shutterService.toOutagePageStatus(step1Out.serviceNames, outagePages)
      back                  =  if (step1Out.status == ShutterStatusValue.Shuttered && !step2aOut.map(_.skipped).getOrElse(true))
                                 appRoutes.ShutterWizardController.step2aGet
                               else appRoutes.ShutterWizardController.step1Post
      form2b                =  step2bForm.fill {
                                 val s2f = form.get
                                 if (s2f.outageMessage.isEmpty) s2f.copy(outageMessage = defaultOutageMessage) else s2f
                               }
    } yield
      page2b(form2b, step0Out.env, step1Out, requiresOutageMessage, outageMessageSrc, defaultOutageMessage, outagePageStatus, back)

  def step2bGet =
    withGroup.async { implicit request =>
      (for {
         step1Out  <- getStep1Out
         step2aOut <- EitherT.liftF(cacheRepo.get(step2aKey))
         step2bf   <- EitherT.liftF {
                        cacheRepo.get(step2bKey)
                          .map {
                            case Some(step2bOut) => Step2bForm(
                                                        reason                = step2bOut.reason
                                                      , outageMessage         = step2bOut.outageMessage
                                                      , requiresOutageMessage = step2bOut.requiresOutageMessage
                                                      , useDefaultOutagePage  = step2bOut.useDefaultOutagePage
                                                      )
                            case None            => Step2bForm(
                                                        reason                = ""
                                                      , outageMessage         = ""
                                                      , requiresOutageMessage = true
                                                      , useDefaultOutagePage  = false
                                                      )
                          }
                      }
         html      <- showPage2b(step2bForm.fill(step2bf), step1Out, step2aOut)
       } yield Ok(html)
      ).merge
    }

  def step2bPost =
    withGroup.async { implicit request =>
      (for {
         step1Out  <- getStep1Out
         step2aOut <- EitherT.liftF(cacheRepo.get(step2aKey))
         sf        <- step2bForm.bindFromRequest
                        .fold(
                            hasErrors = formWithErrors => EitherT.left(showPage2b(formWithErrors, step1Out, step2aOut).map(BadRequest(_)).merge)
                          , success   = data           => EitherT.pure[Future, Result](data)
                          )
         step2bOut =  Step2bOut(sf.reason, sf.outageMessage, sf.requiresOutageMessage, sf.useDefaultOutagePage)
         _         <- EitherT.liftF[Future, Result, (String, String)] {
                        cacheRepo.put(step2bKey, step2bOut)
                      }
       } yield
         Redirect(appRoutes.ShutterWizardController.step3Get)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step3
  //
  // Confirm
  //
  // --------------------------------------------------------------------------

  private def showPage3(form3: Form[Step3Form], shutterType: ShutterType, env: Environment, step1Out: Step1Out, step2Out: Step2bOut)(
    implicit request: Request[Any]): Html = {
    val back =
      if (step1Out.status == ShutterStatusValue.Shuttered && shutterType == ShutterType.Frontend)
        appRoutes.ShutterWizardController.step2bGet
      else appRoutes.ShutterWizardController.step1Post
    page3(form3, shutterType, env, step1Out, step2Out, back)
  }

  def step3Get =
    withGroup.async { implicit request =>
      (for {
         step0Out  <- OptionT(cacheRepo.get(step0Key))
         step1Out  <- OptionT(cacheRepo.get(step1Key))
         step2bOut <- OptionT(cacheRepo.get(step2bKey))
         html      =  showPage3(step3Form.fill(Step3Form(confirm = false)), step0Out.shutterType, step0Out.env, step1Out, step2bOut)
       } yield Ok(html)
      ).getOrElse(Redirect(appRoutes.ShutterWizardController.step1Post))
    }

  def step3Post =
    withGroup.async { implicit request =>
      (for {
         step0Out  <- getStep0Out
         step1Out  <- getStep1Out
         step2bOut <- EitherT.fromOptionF[Future, Result, Step2bOut](
                        cacheRepo.get(step2bKey)
                      , Redirect(appRoutes.ShutterWizardController.step2bGet)
                      )
         _         <- step3Form.bindFromRequest
                       .fold(
                         hasErrors = formWithErrors =>
                           EitherT.left(Future(showPage3(formWithErrors, step0Out.shutterType, step0Out.env, step1Out, step2bOut)).map(BadRequest(_))),
                         success = data => EitherT.pure[Future, Result](())
                       )
         status    =  step1Out.status match {
                        case ShutterStatusValue.Shuttered =>
                          ShutterStatus.Shuttered(
                              reason               = Some(step2bOut.reason).filter(_.nonEmpty)
                            , outageMessage        = Some(step2bOut.outageMessage).filter(_.nonEmpty)
                            , useDefaultOutagePage = step2bOut.useDefaultOutagePage
                            )
                        case ShutterStatusValue.Unshuttered => ShutterStatus.Unshuttered
                      }
         _         <- step1Out.serviceNames.toList.traverse_[EitherT[Future, Result, ?], Unit] { serviceName =>
                       EitherT.right[Result] {
                         shutterService
                           .updateShutterStatus(request.token, serviceName, step0Out.shutterType, step0Out.env, status)
                       }
                     }
       } yield Redirect(appRoutes.ShutterWizardController.step4Get)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step4
  //
  // Feedback from submission
  //
  // --------------------------------------------------------------------------

  def step4Get =
    withGroup.async { implicit request =>
      (for {
         step0Out <- getStep0Out
         step1Out <- getStep1Out

         serviceLinks <- EitherT.liftF[Future, Result, Map[String, Option[String]]](
             step1Out.serviceNames.toList
               .traverse(s => shutterService.lookupShutterRoute(s, step0Out.env).map((s, _)))
               .map(_.toMap)
         )

         html     =  page4(step0Out.shutterType, step0Out.env, step1Out, serviceLinks)
       } yield Ok(html).withSession(request.session - cacheRepo.sessionIdKey)
      ).merge
    }

  // -- Session -------------------------

  def getStep0Out(implicit request: Request[_], ec: ExecutionContext) =
    EitherT.fromOptionF[Future, Result, Step0Out](
        cacheRepo.get(step0Key)
      , Redirect(appRoutes.ShutterOverviewController.allStates(ShutterType.Frontend))
      )

  def getStep1Out(implicit request: Request[_], ec: ExecutionContext) =
    EitherT.fromOptionF[Future, Result, Step1Out](
        cacheRepo.get(step1Key)
      , Redirect(appRoutes.ShutterWizardController.step1Post)
      )
}

object ShutterWizardController {

  import uk.gov.hmrc.cataloguefrontend.util.FormUtils.{notEmpty, notEmptySeq}

  // -- Step 0 -------------------------

  val step0Key  = DataKey[Step0Out]("step0")

  case class Step0Out(
    shutterType: ShutterType
  , env        : Environment
  )

  val step0OutFormats = {
    implicit val stf = ShutterType.format
    implicit val ef  = Environment.format
    Json.format[Step0Out]
  }

  // -- Step 1 -------------------------

  val step1Key  = DataKey[Step1Out]("step1")

  def step1Form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
        "serviceName" -> Forms.seq(Forms.text).verifying(notEmptySeq),
        "status"      -> Forms.text.verifying(notEmpty)
      )(Step1Form.apply)(Step1Form.unapply)
    )

  case class Step1Form(
      serviceNames: Seq[String]
    , status      : String
    )

  case class Step1Out(
      serviceNames: Seq[String]
    , status      : ShutterStatusValue
    )

  val step1OutFormats = {
    implicit val ssf = ShutterStatusValue.format
    Json.format[Step1Out]
  }

  // -- Step 2a Review frontend-route warnings (if any) -------------------------

  val step2aKey = DataKey[Step2aOut]("step2a")

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
    , skipped  : Boolean
    )

  val step2aOutFormats =
    Json.format[Step2aOut]

  // -- Step 2b Review outage-page message and warnings -------------------------

  val step2bKey = DataKey[Step2bOut]("step2b")

  def step2bForm(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
          "reason"                -> Forms.text
        , "outageMessage"         -> Forms.text
        , "requiresOutageMessage" -> Forms.boolean
        , "useDefaultOutagePage"  -> Forms.boolean
        )(Step2bForm.apply)(Step2bForm.unapply)
    )

  case class Step2bForm(
      reason               : String
    , outageMessage        : String
    , requiresOutageMessage: Boolean
    , useDefaultOutagePage : Boolean
    )

  case class Step2bOut(
      reason               : String
    , outageMessage        : String
    , requiresOutageMessage: Boolean
    , useDefaultOutagePage : Boolean
    )

  val step2bOutFormats =
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

  case class ServiceAndRouteWarnings(serviceName: String, warnings: Seq[FrontendRouteWarning])
}

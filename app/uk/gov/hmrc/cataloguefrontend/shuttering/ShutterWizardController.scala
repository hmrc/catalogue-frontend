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

package uk.gov.hmrc.cataloguefrontend.shuttering

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.instances.all._
import cats.syntax.all._
import play.api.data.{Form, Forms}
import play.api.i18n.Messages
import play.api.libs.json.{Format, Json, Reads, Writes}
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.auth.AuthController
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.shuttering.{routes => appRoutes}
import uk.gov.hmrc.cataloguefrontend.shuttering.view.shutterService.html.{Page1, Page2a, Page2b, Page3, Page4}
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger

@Singleton
class ShutterWizardController @Inject() (
  override val  mcc   : MessagesControllerComponents,
  shutterService      : ShutterService,
  page1               : Page1,
  page2a              : Page2a,
  page2b              : Page2b,
  page3               : Page3,
  page4               : Page4,
  override val auth   : FrontendAuthComponents,
  mongoComponent      : MongoComponent,
  servicesConfig      : ServicesConfig,
  timestampSupport    : TimestampSupport
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport:

  import ShutterWizardController._

  private val logger: Logger = Logger(getClass)

  val sessionIdKey = "ShutterWizardController"

  val cacheRepo = SessionCacheRepository(
    mongoComponent   = mongoComponent,
    collectionName   = "sessions",
    ttl              = servicesConfig.getDuration("mongodb.session.expireAfter"),
    timestampSupport = timestampSupport,
    sessionIdKey     = sessionIdKey
  )

  given Format[Step0Out]  = step0OutFormats
  given Format[Step1Out]  = step1OutFormats
  given Format[Step2aOut] = step2aOutFormats
  given Format[Step2bOut] = step2bOutFormats

  def shutterPermission(shutterType: ShutterType)(serviceName: ServiceName): Predicate =
    Predicate.Permission(Resource.from("shutter-api", s"${shutterType.asString}/${serviceName.asString}"), IAAction("SHUTTER"))

  // --------------------------------------------------------------------------
  // Start
  //
  // --------------------------------------------------------------------------

  def start(shutterType: ShutterType, env: Environment, serviceName: Option[ServiceName], context: Option[String]) =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.start(shutterType, env, serviceName, context))
    ).async { implicit request =>
      for
        updatedFlowState <- putSession(step0Key, Step0Out(shutterType = shutterType, env = env))
      yield
        Redirect(appRoutes.ShutterWizardController.step1Get(serviceName, context))
          .withSession(request.session + updatedFlowState)
    }

  // --------------------------------------------------------------------------
  // Step1
  //
  // Select Services, Environment, and shutter action
  // --------------------------------------------------------------------------

  private def showPage1(
    shutterType: ShutterType,
    env        : Environment,
    form       : Form[Step1Form]
  )(using
    request    : Request[Any]
  ): Future[Html] =
    for
      shutterStates <- shutterService.getShutterStates(shutterType, env)
      envs          =  Environment.values
      statusValues  =  ShutterStatusValue.values
      shutterGroups <- shutterService.shutterGroups()
      back          =  appRoutes.ShutterOverviewController.allStatesForEnv(shutterType, env)
    yield page1(form, shutterType, env, shutterStates, shutterGroups, back)

  def step1Get(
    serviceName: Option[ServiceName]
  , context    : Option[String]
  ) =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step1Get(serviceName, context))
    ).async { implicit request =>
      (for
         step0Out      <- getStep0Out()
         shutterStates <- EitherT.liftF(shutterService.getShutterStates(step0Out.shutterType, step0Out.env))
         step1f        <- serviceName match
                            case Some(sn) => EitherT.pure[Future, Result](Step1Form(
                                               serviceNameAndContexts = ServiceNameAndContext(sn, context) :: Nil
                                             , status                 = inverseShutterStatusValue(shutterStates)(sn, context)
                                             ))
                            case None     => EitherT.liftF[Future, Result, Step1Form](
                                               getFromSession(step1Key)
                                                 .map(_.fold(Step1Form(serviceNameAndContexts = Seq.empty, status = None))(step1Out => Step1Form(
                                                   serviceNameAndContexts = step1Out.serviceNameAndContexts
                                                 , status                 = Some(step1Out.status)
                                                 )))
                                            )
         html          <- EitherT.liftF[Future, Result, Result]:
                            showPage1(step0Out.shutterType, step0Out.env, step1Form.fill(step1f))
                              .map(Ok(_))
       yield html
      ).merge
    }

  private def inverseShutterStatusValue(shutterStates: Seq[ShutterState])(serviceName: ServiceName, context: Option[String]): Option[ShutterStatusValue] =
    shutterStates
      .find(x => x.serviceName == serviceName && x.context == context)
      .map:
        _.status.value match
          case ShutterStatusValue.Shuttered   => ShutterStatusValue.Unshuttered
          case ShutterStatusValue.Unshuttered => ShutterStatusValue.Shuttered

  def step1Post =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step1Get(None, None))
    ).async { implicit request =>
      (for
         step0Out      <- getStep0Out()
         boundForm     =  step1Form.bindFromRequest()
         sf            <- boundForm.fold(
                            hasErrors = formWithErrors => EitherT.left(showPage1(step0Out.shutterType, step0Out.env, formWithErrors).map(BadRequest(_)))
                          , success   = data           => EitherT.pure[Future, Result](data)
                          )
         status        <- sf.status.fold(
                            EitherT.left(showPage1(step0Out.shutterType, step0Out.env, boundForm).map(BadRequest(_)))
                          )(status => EitherT.pure[Future, Result](status)
                          )
         // check has permission to shutter selected services (TODO only display locations that can be shuttered)
         serviceNameAndContexts
                       <- EitherT
                            .fromOption[Future](NonEmptyList.fromList(sf.serviceNameAndContexts.toList), ())
                            .leftFlatMap: _ =>
                              EitherT.left[NonEmptyList[ServiceNameAndContext]](
                                showPage1(step0Out.shutterType, step0Out.env, boundForm.withGlobalError(Messages("No services selected"))).map(BadRequest(_))
                              )
         resources     <- EitherT.liftF(auth.authorised(predicate = None, retrieval = Retrieval.locations(Some(ResourceType("shutter-api")))))
         permsFor      =
                          if resources.exists(_.resourceLocation.value == s"${step0Out.shutterType.asString}/*")
                          then
                            serviceNameAndContexts.map(_.serviceName.asString).toList
                          else
                            val locationPrefix = step0Out.shutterType.asString + "/"
                            resources
                              .collect { case Resource(_, ResourceLocation(l)) if l.startsWith(locationPrefix) => l.stripPrefix(locationPrefix) }
                              .toList
         denied        =  serviceNameAndContexts.map(_.serviceName.asString).toList.diff(permsFor)
         _             <-
                          if denied.isEmpty
                          then
                            EitherT.pure[Future, Result](())
                          else
                            EitherT.left[Unit]:
                              val errorMessage = s"You do not have permission to shutter service(s): ${denied.mkString(", ")}"
                              showPage1(step0Out.shutterType, step0Out.env, boundForm.withGlobalError(Messages(errorMessage))).map(Forbidden(_))
         step1Out      =  Step1Out(sf.serviceNameAndContexts, status)
         _             <- EitherT.liftF[Future, Result, (String, String)]:
                            status match
                              case ShutterStatusValue.Shuttered if step0Out.shutterType == ShutterType.Frontend =>
                                putSession(step1Key, step1Out)
                              case _ =>
                                for
                                  _   <- putSession(step1Key, step1Out)
                                  res <- putSession(
                                           step2bKey,
                                           Step2bOut(
                                             reason                = "",
                                             outageMessage         = "",
                                             outageMessageWelsh    = "",
                                             useDefaultOutagePage  = false
                                           )
                                         )
                                yield res
       yield
         status match
           case ShutterStatusValue.Shuttered if step0Out.shutterType == ShutterType.Frontend =>
             Redirect(appRoutes.ShutterWizardController.step2aGet)
           case _ =>
             Redirect(appRoutes.ShutterWizardController.step3Get)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step2a
  //
  // Review frontend-route warnings (if any) (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------
  private def frontendRouteWarnings(
    env     : Environment,
    step1Out: Step1Out
  )(using
    request : Request[Any]
  ): Future[Seq[ServiceAndRouteWarnings]] =
    step1Out
      .serviceNameAndContexts
      .traverse(service =>
        shutterService
          .frontendRouteWarnings(env, service.serviceName)
          .map(w => ServiceAndRouteWarnings(service.serviceName, w))
      )

  private def showPage2a(
    form2a  : Form[Step2aForm],
    step0Out: Step0Out,
    step1Out: Step1Out
  )(using
    request : Request[Any]
  ): Future[Html] =
    frontendRouteWarnings(step0Out.env, step1Out)
      .map(w => page2a(form2a, step0Out, step1Out, w, appRoutes.ShutterWizardController.step1Get(None, None)))

  def step2aGet =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step2aGet)
    ).async { implicit request =>
      (for
         step0Out <- getStep0Out()
         step1Out <- getStep1Out()
         step2af  <- EitherT.liftF:
                       getFromSession(step2aKey)
                         .map:
                           case Some(step2aOut) => Step2aForm(confirm = step2aOut.confirmed)
                           case None            => Step2aForm(confirm = false)
         serviceAndRouteWarnings <- EitherT.right(frontendRouteWarnings(step0Out.env, step1Out))
         html     <- EitherT.right[Result]:
                       if serviceAndRouteWarnings.flatMap(_.warnings).nonEmpty
                       then
                         showPage2a(step2aForm.fill(step2af), step0Out, step1Out).map(Ok(_))
                       else
                         //Skip straight to outage-page screen if there are no route warnings
                         putSession(step2aKey, Step2aOut(confirmed = false, skipped = true))
                           .map(_ => Redirect(appRoutes.ShutterWizardController.step2bGet))
       yield html
      ).merge
    }

  def step2aPost =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step2aGet)
    ).async { implicit request =>
      (for
         step0Out <- getStep0Out()
         step1Out <- getStep1Out()
         sf       <- step2aForm.bindFromRequest()
                       .fold(
                         hasErrors = formWithErrors => EitherT.left(
                                       showPage2a(formWithErrors, step0Out, step1Out).map(BadRequest(_))
                                     ),
                         success   = data => EitherT.pure[Future, Result](data)
                       )
         step2aOut =  Step2aOut(sf.confirm, skipped = false)
         _         <- EitherT.liftF[Future, Result, (String, String)](
                        putSession(step2aKey, step2aOut)
                      )
       yield Redirect(appRoutes.ShutterWizardController.step2bGet)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step2b
  //
  // Review outage pages (only applies to Shutter - not Unshutter)
  //
  // --------------------------------------------------------------------------

  private def showPage2b(
    form     : Form[Step2bForm],
    step1Out : Step1Out,
    step2aOut: Option[Step2aOut]
  )(using
    request  : Request[Any]
  ): EitherT[Future, Result, Html] =
    for
      step0Out              <- getStep0Out()
      outagePages           <- EitherT.liftF[Future, Result, List[OutagePage]]:
                                 step1Out
                                   .serviceNameAndContexts
                                   .traverse[Future, Option[OutagePage]] { service => shutterService.outagePage(service.serviceName) }
                                   .map(_.toList.collect { case Some(op) => op })
      outageMessageTemplate =  outagePages
                                 .flatMap(op => op.templatedMessages.map((op, _)))
                                 .headOption
      outageMessageSrc      =  outageMessageTemplate.map(_._1)
      defaultOutageMessage  =  outageMessageTemplate.fold("")(_._2.innerHtml)
      outagePageStatus      =  shutterService.toOutagePageStatus(step1Out.serviceNameAndContexts.map(_.serviceName), outagePages)
      back                  =
                               if step1Out.status == ShutterStatusValue.Shuttered && !step2aOut.map(_.skipped).getOrElse(true)
                               then
                                 appRoutes.ShutterWizardController.step2aGet
                               else
                                 appRoutes.ShutterWizardController.step1Post
      form2b                =  step2bForm.fill:
                                 val s2f = form.get
                                 if s2f.outageMessage.isEmpty
                                 then
                                   s2f.copy(outageMessage = defaultOutageMessage)
                                 else
                                   s2f
    yield
      page2b(
        form2b,
        step0Out,
        step1Out,
        outageMessageSrc,
        defaultOutageMessage,
        outagePages,
        back
      )

  val step2bGet =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step2bGet)
    ).async { implicit request =>
      (for
         step1Out  <- getStep1Out()
         step2aOut <- EitherT.liftF(getFromSession(step2aKey))
         step2bf   <- EitherT.liftF:
                        getFromSession(step2bKey)
                          .map:
                            case Some(step2bOut) =>
                              Step2bForm(
                                reason                = step2bOut.reason,
                                outageMessage         = step2bOut.outageMessage,
                                outageMessageWelsh    = step2bOut.outageMessageWelsh,
                                useDefaultOutagePage  = step2bOut.useDefaultOutagePage
                              )
                            case None =>
                              Step2bForm(
                                reason                = "",
                                outageMessage         = "",
                                outageMessageWelsh    = "",
                                useDefaultOutagePage  = false
                              )
         html      <- showPage2b(step2bForm.fill(step2bf), step1Out, step2aOut)
       yield Ok(html)
      ).merge
    }
    
  def step2bPreview(
      serviceName: ServiceName,
      templatedMessage: Option[String]
  ) =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step2bGet)
    ).async { implicit request =>
      shutterService
        .outagePagePreview(serviceName, templatedMessage)
        .map {
          case None       =>
            logger.warn(s"Unable to retrieve outage page preview for service: ${serviceName.asString}")
            Redirect(appRoutes.ShutterWizardController.step2bGet)
          case Some(page) => Ok(Html(page.outerHtml))
        }
    }

  val step2bPost =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step2bGet)
    ).async { implicit request =>
      (for
         step1Out  <- getStep1Out()
         step2aOut <- EitherT.liftF(getFromSession(step2aKey))
         sf        <- step2bForm.bindFromRequest()
                        .fold(
                          hasErrors = formWithErrors => EitherT.left(
                                        showPage2b(formWithErrors, step1Out, step2aOut).map(BadRequest(_)).merge
                                      ),
                          success   = data => EitherT.pure[Future, Result](data)
                        )
         step2bOut =  Step2bOut(sf.reason, sf.outageMessage, sf.outageMessageWelsh, sf.useDefaultOutagePage)
         _         <- EitherT.liftF[Future, Result, (String, String)]:
                        putSession(step2bKey, step2bOut)
       yield Redirect(appRoutes.ShutterWizardController.step3Get)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step3
  //
  // Confirm
  //
  // --------------------------------------------------------------------------

  private def showPage3(
    form3      : Form[Step3Form],
    shutterType: ShutterType,
    env        : Environment,
    step1Out   : Step1Out,
    step2Out   : Step2bOut
  )(using
    request    : Request[Any]
  ): Html =
    val back =
      if step1Out.status == ShutterStatusValue.Shuttered && shutterType == ShutterType.Frontend
      then
        appRoutes.ShutterWizardController.step2bGet
      else
        appRoutes.ShutterWizardController.step1Post
    page3(form3, shutterType, env, step1Out, step2Out, back)

  val step3Get =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step3Get)
    ).async { implicit request =>
      (for
         step0Out  <- OptionT(getFromSession(step0Key))
         step1Out  <- OptionT(getFromSession(step1Key))
         step2bOut <- OptionT(getFromSession(step2bKey))
         html      =  showPage3(
                        step3Form.fill(Step3Form(confirm = false)),
                        step0Out.shutterType,
                        step0Out.env,
                        step1Out,
                        step2bOut
                      )
       yield Ok(html)
      ).getOrElse(Redirect(appRoutes.ShutterWizardController.step1Post))
    }

  val step3Post =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step3Get)
    ).async { implicit request =>
      (for
         step0Out  <- getStep0Out()
         step1Out  <- getStep1Out()
         step2bOut <- EitherT.fromOptionF[Future, Result, Step2bOut](
                        getFromSession(step2bKey),
                        Redirect(appRoutes.ShutterWizardController.step2bGet)
                      )
         _         <- step3Form.bindFromRequest()
                        .fold(
                          hasErrors = formWithErrors => EitherT.leftT[Future, Unit](
                                        BadRequest(
                                          showPage3(
                                            formWithErrors,
                                            step0Out.shutterType,
                                            step0Out.env,
                                            step1Out,
                                            step2bOut
                                          )
                                        )
                                      ),
                          success   = data => EitherT.pure[Future, Result](())
                        )
         status    =  step1Out.status match
                        case ShutterStatusValue.Shuttered   => ShutterStatus.Shuttered(
                                                                 reason               = Some(step2bOut.reason).filter(_.nonEmpty)
                                                               , outageMessage        = Some(step2bOut.outageMessage).filter(_.nonEmpty)
                                                               , outageMessageWelsh   = Some(step2bOut.outageMessageWelsh).filter(_.nonEmpty)
                                                               , useDefaultOutagePage = step2bOut.useDefaultOutagePage
                                                               )
                        case ShutterStatusValue.Unshuttered => ShutterStatus.Unshuttered
         _         <- EitherT.right[Result]:
                        step1Out
                          .serviceNameAndContexts
                          .traverse(service => shutterService.updateShutterStatus(service.serviceName, service.context, step0Out.shutterType, step0Out.env, status))
       yield Redirect(appRoutes.ShutterWizardController.step4Get)
      ).merge
    }

  // --------------------------------------------------------------------------
  // Step4
  //
  // Feedback from submission
  //
  // --------------------------------------------------------------------------

  val step4Get =
    auth.authenticatedAction(
      continueUrl = AuthController.continueUrl(appRoutes.ShutterWizardController.step4Get)
    ).async { implicit request =>
      (for
         step0Out     <- getStep0Out()
         step1Out     <- getStep1Out()
         serviceLinks <- EitherT.liftF[Future, Result, Map[String, Option[String]]](
                           step1Out.serviceNameAndContexts
                             .traverse(service => shutterService.lookupShutterRoute(service.serviceName, step0Out.env).map((service.serviceName.asString, _)))
                             .map(_.toMap)
                         )
         html         =  page4(step0Out.shutterType, step0Out.env, step1Out, serviceLinks)
       yield
         Ok(html).withSession(request.session - sessionIdKey)
      ).merge
    }

  // -- Session -------------------------

  private def getStep0Out()(using Request[?]) =
    EitherT.fromOptionF[Future, Result, Step0Out](
      getFromSession(step0Key),
      Redirect(appRoutes.ShutterOverviewController.allStates(ShutterType.Frontend))
    )

  private def getStep1Out()(using Request[?]) =
    EitherT.fromOptionF[Future, Result, Step1Out](
      getFromSession(step1Key),
      Redirect(appRoutes.ShutterWizardController.step1Post)
    )

  private def getFromSession[A : Reads](dataKey: DataKey[A])(using Request[?]): Future[Option[A]] =
    cacheRepo.getFromSession(dataKey)

  private def putSession[A : Writes](dataKey: DataKey[A], data: A)(using Request[?]): Future[(String, String)] =
    cacheRepo.putSession(dataKey, data)
end ShutterWizardController

object ShutterWizardController:

  import play.api.libs.functional.syntax._
  import uk.gov.hmrc.cataloguefrontend.util.FormUtils.{notEmptyOption, notEmptySeq}

  // -- Step 0 -------------------------

  val step0Key = DataKey[Step0Out]("step0")

  case class Step0Out(
    shutterType: ShutterType,
    env        : Environment
  )

  val step0OutFormats =
    Json.format[Step0Out]

  // -- Step 1 -------------------------

  val step1Key = DataKey[Step1Out]("step1")

  case class ServiceNameAndContext(serviceName: ServiceName, context: Option[String]):
    def asString: String =
      s"${serviceName.asString},${context.getOrElse("")}"

  object ServiceNameAndContext:
    def apply(asString: String): ServiceNameAndContext =
      ServiceNameAndContext(
        ServiceName(asString.take(asString.indexOf(",")))
      , asString.split(",").drop(1).headOption
      )

  val step1Form =
    Form(
      Forms.mapping(
        "selectedServices" -> Forms.seq(Forms.text.transform[ServiceNameAndContext](ServiceNameAndContext.apply(_), _.asString)).verifying(notEmptySeq)
      , "status"           -> Forms.optional(Forms.of[ShutterStatusValue]).verifying(notEmptyOption)
      )(Step1Form.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

  case class Step1Form(
    serviceNameAndContexts: Seq[ServiceNameAndContext],
    status                : Option[ShutterStatusValue]
  )

  case class Step1Out(
    serviceNameAndContexts: Seq[ServiceNameAndContext],
    status                : ShutterStatusValue
  )

  val step1OutFormats: Format[Step1Out] =
    given Format[ServiceNameAndContext] =
      summon[Format[String]].inmap[ServiceNameAndContext](ServiceNameAndContext.apply, _.asString)

    Json.format[Step1Out]

  // -- Step 2a Review frontend-route warnings (if any) -------------------------

  val step2aKey = DataKey[Step2aOut]("step2a")

  val step2aForm =
    Form(
      Forms
        .mapping(
          "confirm" -> Forms.boolean
        )(Step2aForm.apply)(r => Some(r.confirm))
        .verifying("You must tick to confirm you have acknowledged the frontend-route warnings", _.confirm == true)
    )
  case class Step2aForm(confirm: Boolean)

  case class Step2aOut(
    confirmed: Boolean,
    skipped  : Boolean
  )

  val step2aOutFormats =
    Json.format[Step2aOut]

  // -- Step 2b Review outage-page message and warnings -------------------------

  val step2bKey = DataKey[Step2bOut]("step2b")

  val step2bForm =
    Form(
      Forms.mapping(
        "reason"                -> Forms.text,
        "outageMessage"         -> Forms.text,
        "outageMessageWelsh"    -> Forms.text,
        "useDefaultOutagePage"  -> Forms.boolean
      )(Step2bForm.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

  case class Step2bForm(
    reason               : String,
    outageMessage        : String,
    outageMessageWelsh   : String,
    useDefaultOutagePage : Boolean
  )

  case class Step2bOut(
    reason               : String,
    outageMessage        : String,
    outageMessageWelsh   : String,
    useDefaultOutagePage : Boolean
  )

  val step2bOutFormats =
    Json.format[Step2bOut]

  // -- Step 3 -------------------------

  val step3Form =
    Form(
      Forms
        .mapping(
          "confirm" -> Forms.boolean
        )(Step3Form.apply)(r => Some(r.confirm))
        .verifying("You must tick to confirm you have acknowledged you are changing production services!", _.confirm == true)
    )

  case class Step3Form(
    confirm: Boolean
  )

  case class ServiceAndRouteWarnings(
    serviceName: ServiceName,
    warnings   : Seq[FrontendRouteWarning]
  )

end ShutterWizardController

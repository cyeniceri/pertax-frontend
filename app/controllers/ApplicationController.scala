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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth._
import org.joda.time.DateTime
import play.api.{Environment, Logger, Mode, Play}
import play.api.mvc._
import services.IdentityVerificationSuccessResponse._
import services._
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrlPolicy.Id
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, PermitAllOnDev, RedirectUrl, RedirectUrlPolicy, SafeRedirectUrl}
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import views.html.iv.success.SuccessView
import views.html.iv.failure._

import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject()(
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  successView: SuccessView,
  cannotConfirmIdentityView: CannotConfirmIdentityView,
  failedIvIncompleteView: FailedIvIncompleteView,
  lockedOutView: LockedOutView,
  timeOutView: TimeOutView,
  technicalIssuesView: TechnicalIssuesView,
  environment: Environment)(
  implicit configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) with CurrentTaxYear {

  private val logger = Logger(this.getClass)

  override def now: () => DateTime = () => DateTime.now()

  def uplift(redirectUrl: Option[SafeRedirectUrl]): Action[AnyContent] = Action.async {
    Future.successful(Redirect(redirectUrl.map(_.url).getOrElse(routes.HomeController.index().url)))
  }

  def showUpliftJourneyOutcome(continueUrl: Option[SafeRedirectUrl]): Action[AnyContent] =
    Action.async { implicit request =>
      val journeyId =
        List(request.getQueryString("token"), request.getQueryString("journeyId")).flatten.headOption

      val retryUrl = routes.ApplicationController.uplift(continueUrl).url

      journeyId match {
        case Some(jid) =>
          identityVerificationFrontendService.getIVJourneyStatus(jid).map {

            case IdentityVerificationSuccessResponse(Success) =>
              Ok(successView(continueUrl.map(_.url).getOrElse(routes.HomeController.index().url)))

            case IdentityVerificationSuccessResponse(InsufficientEvidence) =>
              Redirect(routes.SelfAssessmentController.ivExemptLandingPage(continueUrl))

            case IdentityVerificationSuccessResponse(UserAborted) =>
              logErrorMessage(UserAborted)
              Unauthorized(cannotConfirmIdentityView(retryUrl))

            case IdentityVerificationSuccessResponse(FailedMatching) =>
              logErrorMessage(FailedMatching)
              Unauthorized(cannotConfirmIdentityView(retryUrl))

            case IdentityVerificationSuccessResponse(Incomplete) =>
              logErrorMessage(Incomplete)
              Unauthorized(failedIvIncompleteView(retryUrl))

            case IdentityVerificationSuccessResponse(PrecondFailed) =>
              logErrorMessage(PrecondFailed)
              Unauthorized(cannotConfirmIdentityView(retryUrl))

            case IdentityVerificationSuccessResponse(LockedOut) =>
              logErrorMessage(LockedOut)
              Unauthorized(lockedOutView(allowContinue = false))

            case IdentityVerificationSuccessResponse(Timeout) =>
              logErrorMessage(Timeout)
              InternalServerError(timeOutView(retryUrl))

            case IdentityVerificationSuccessResponse(TechnicalIssue) =>
              logger.warn(s"TechnicalIssue response from identityVerificationFrontendService")
              InternalServerError(technicalIssuesView(retryUrl))

            case r =>
              logger.error(s"Unhandled response from identityVerificationFrontendService: $r")
              InternalServerError(technicalIssuesView(retryUrl))
          }
        case None =>
          logger.error(s"No journeyId present when displaying IV uplift journey outcome")
          Future.successful(BadRequest(technicalIssuesView(retryUrl)))
      }
    }

  private def logErrorMessage(reason: String): Unit =
    logger.warn(s"Unable to confirm user identity: $reason")

  def signout(continueUrl: Option[RedirectUrl], origin: Option[Origin]): Action[AnyContent] =
    authJourney.minimumAuthWithSelfAssessment { implicit request =>
      val safeUrl = continueUrl.flatMap { x =>
        x.getEither(getPolicy) match {
          case Right(safeRedirectUrl) => Some(safeRedirectUrl.url)
          case _                      => Some(configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin))
        }
      }
      safeUrl
        .orElse(origin.map(configDecorator.getFeedbackSurveyUrl))
        .fold(BadRequest("Missing origin")) { url: String =>
          if (request.isGovernmentGateway) {
            Redirect(configDecorator.getBasGatewayFrontendSignOutUrl(url))
          } else {
            Redirect(configDecorator.citizenAuthFrontendSignOut).withSession("postLogoutPage" -> url)
          }
        }
    }

  private def getPolicy: RedirectUrlPolicy[Id] = {
    val runningMode: Mode =
      if (configDecorator.serviceManagerRunModeFlag & environment.mode == Mode.Prod) Mode.Dev else environment.mode

    OnlyRelative | PermitAllOnDev(Environment.simple(mode = runningMode))
  }
}

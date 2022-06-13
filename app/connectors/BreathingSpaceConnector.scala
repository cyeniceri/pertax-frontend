/*
 * Copyright 2022 HM Revenue & Customs
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

package connectors

import cats.data.EitherT
import cats.implicits.catsStdInstancesForFuture
import com.codahale.metrics.Timer
import com.google.inject.Inject
import config.ConfigDecorator
import metrics.{Metrics, MetricsEnumeration}
import models.BreathingSpaceIndicator
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http._
import util.Timeout

import java.util.UUID.randomUUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceConnector @Inject() (
  val httpClient: HttpClient,
  val metrics: Metrics,
  override val configDecorator: ConfigDecorator
) extends Timeout with Logging with ServicesCircuitBreaker {

  lazy val baseUrl = configDecorator.breathingSpcaeBaseUrl
  lazy val timeoutInSec =
    configDecorator.breathingSpcaeTimeoutInSec
  val metricName = MetricsEnumeration.GET_BREATHING_SPACE_INDICATOR

  override val externalServiceName = configDecorator.breathingSpaceAppName

  def getBreathingSpaceIndicator(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Boolean] = {

    val timerContext: Timer.Context = metrics.startTimer(metricName)
    val url = s"$baseUrl/$nino/memorandum"
    implicit val bsHeaderCarrier: HeaderCarrier = hc
      .withExtraHeaders(
        "Correlation-Id" -> randomUUID.toString
      )

    val result =
      withTimeout(timeoutInSec seconds) {
        withCircuitBreaker(httpClient
          .GET[Either[UpstreamErrorResponse, HttpResponse]](url)(implicitly, bsHeaderCarrier, implicitly))(bsHeaderCarrier)
          .map { response =>
            timerContext.stop()
            response
          }
      }

    val eitherResponse = EitherT(
      result
        .map {
          case Right(response) =>
            metrics.incrementSuccessCounter(metricName)
            Right(response)
          case Left(error) if error.statusCode == NOT_FOUND =>
            metrics.incrementFailedCounter(metricName)
            logger.info(error.message)
            Left(error)
          case Left(error) if error.statusCode >= 400 && error.statusCode <= 499 =>
            metrics.incrementFailedCounter(metricName)
            logger.error(error.message)
            throw new HttpException(error.message, error.statusCode)
          case Left(error) =>
            metrics.incrementFailedCounter(metricName)
            logger.error(error.message, error)
            Left(error)
        }
    )

    eitherResponse.map { value =>
      value.json.as[BreathingSpaceIndicator].breathingSpaceIndicator
    }


  }
}

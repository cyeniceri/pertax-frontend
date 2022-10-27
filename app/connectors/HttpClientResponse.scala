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
import com.google.inject.Inject
import metrics.{Metrics, MetricsEnumeration}
import play.api.Logging
import play.api.http.Status.{BAD_GATEWAY, LOCKED, NOT_FOUND, TOO_MANY_REQUESTS}
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class HttpClientResponse @Inject() (metrics: Metrics)(implicit ec: ExecutionContext) extends Logging {

  def read(
    response: Future[Either[UpstreamErrorResponse, HttpResponse]],
    metricName: Option[MetricsEnumeration.Value] = None
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    EitherT(response.map {
      case Right(response)                                                                 =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        Right(response)
      case Left(error) if error.statusCode == NOT_FOUND                                    =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        logger.info(error.message)
        Left(error)
      case Left(error) if error.statusCode == LOCKED                                       =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        logger.warn(error.message)
        Left(error)
      case Left(error) if error.statusCode >= 499 || error.statusCode == TOO_MANY_REQUESTS =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        logger.error(error.message)
        Left(error)
      case Left(error)                                                                     =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        logger.error(error.message, error)
        Left(error)
    } recover {
      case exception: HttpException =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        logger.error(exception.message)
        Left(UpstreamErrorResponse(exception.message, BAD_GATEWAY, BAD_GATEWAY))
      case exception: Exception     =>
        metricName.foreach(metric => metrics.incrementFailedCounter(metric))
        throw exception
    })
}

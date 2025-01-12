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

package connectors

import cats.data.EitherT
import com.google.inject.Inject
import config.ConfigDecorator
import models.BreathingSpaceIndicator
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import play.api.libs.json.OFormat.oFormatFromReadsAndOWrites
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import util.Timeout

import java.util.UUID.randomUUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceConnector @Inject() (
  val httpClient: HttpClient,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator
) extends Timeout
    with Logging {

  lazy val baseUrl      = configDecorator.breathingSpcaeBaseUrl
  lazy val timeoutInSec =
    configDecorator.breathingSpaceTimeoutInSec

  def getBreathingSpaceIndicator(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Boolean] = {
    val url                                     = s"$baseUrl/$nino/memorandum"
    implicit val bsHeaderCarrier: HeaderCarrier = hc
      .withExtraHeaders(
        "Correlation-Id" -> randomUUID.toString
      )
    val result                                  = withTimeout(timeoutInSec.seconds) {
      httpClient
        .GET[Either[UpstreamErrorResponse, HttpResponse]](url)(readEitherOf(readRaw), bsHeaderCarrier, ec)
    }
    httpClientResponse
      .read(result)
      .map(response => response.json.as[BreathingSpaceIndicator].breathingSpaceIndicator)
  }

}

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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{BAD_GATEWAY, BAD_REQUEST, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HttpException, UpstreamErrorResponse}
import util.{BaseSpec, Fixtures, WireMockHelper}

class BreathingSpaceConnectorSpec extends BaseSpec with WireMockHelper {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.breathing-space-if-proxy.port" -> server.port()
    )
    .build()

  def sut: BreathingSpaceConnector = injected[BreathingSpaceConnector]
  val nino: Nino = Fixtures.fakeNino

  val url = s"/$nino/memorandum"

  val breathingSpaceTrueResponse =
    s"""
       |{
       |    "breathingSpaceIndicator": true
       |}
       |""".stripMargin

  val breathingSpaceFalseResponse =
    s"""
       |{
       |    "breathingSpaceIndicator": false
       |}
       |""".stripMargin

  "getBreathingSpaceIndicator is called" must {

    "return a true right response" in {

      server.stubFor(
        get(urlPathEqualTo(url))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      sut
        .getBreathingSpaceIndicator(nino)
        .value
        .futureValue
        .right
        .get mustBe true
    }

    "return a false right response" in {

      server.stubFor(
        get(urlPathEqualTo(url))
          .willReturn(ok(breathingSpaceFalseResponse))
      )

      sut
        .getBreathingSpaceIndicator(nino)
        .value
        .futureValue
        .right
        .get mustBe false
    }

    List(
      NOT_FOUND,
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE
    ).foreach { httpResponse =>
      s"return a $httpResponse when $httpResponse status is received" in {

        server.stubFor(
          get(urlPathEqualTo(url))
            .willReturn(aResponse.withStatus(httpResponse))
        )

        val result = sut
          .getBreathingSpaceIndicator(nino)
          .value
          .futureValue
          .left
          .get

        result mustBe an[UpstreamErrorResponse]
        result.statusCode mustBe httpResponse

      }
    }

    List(
      BAD_REQUEST,
      IM_A_TEAPOT,
      UNPROCESSABLE_ENTITY
    ).foreach { httpResponse =>
      s"throws a HttpException when $httpResponse status is received" in {

        server.stubFor(
          get(urlPathEqualTo(url))
            .willReturn(aResponse.withStatus(httpResponse))
        )

        val result = sut
          .getBreathingSpaceIndicator(nino)
          .value

        whenReady(result.failed) { e =>
          e mustBe a[HttpException]
        }
      }
    }
  }

}

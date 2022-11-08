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

package services

import com.codahale.metrics.Timer.Context
import com.codahale.metrics.{Counter, MetricRegistry, Timer}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.kenshoo.play.metrics.Metrics
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.CONTENT_TYPE
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.UpstreamErrorResponse

class PreferencesFrontendConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  val mockMetrics        = mock[Metrics]
  val mockMetricRegistry = mock[MetricRegistry]
  val mockTimer          = mock[Timer]
  val mockContext        = mock[Context]
  val mockCounter        = mock[Counter]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[Metrics].toInstance(mockMetrics))
    .configure("microservice.services.preferences-frontend.port" -> server.port)
    .build()

  when(mockMetrics.defaultRegistry).thenReturn(mockMetricRegistry)

  when(mockMetricRegistry.timer(anyString())).thenReturn(mockTimer)

  when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter)

  when(mockTimer.time()).thenReturn(mockContext)

  when(mockContext.stop()).thenReturn(1L)

  //TODO: Find a way to mock metrics in a testable way
  "PreferencesFrontend" must {

    "return None if an OK status is retrieved, and user is Government GateWay" in {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendConnector]

      val url = "/paperless/activate"

      val jsonBody =
        """{
          | "redirectUserTo": "/foo"
          |}
          |""".stripMargin

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(jsonBody)
          )
      )

      val result = service.getPaperlessPreference().value.futureValue.getOrElse(Some("testUrl"))

      result mustBe None

    }

    "return a redirectUrl if Precondition failed with 412 response" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(
          saUser = NonFilerSelfAssessmentUser,
          request = FakeRequest()
        )

      implicit val service = app.injector.instanceOf[PreferencesFrontendConnector]

      val url = "/paperless/activate"

      val jsonBody =
        """{
          | "redirectUserTo": "http://www.testurl.com"
          |}
          |""".stripMargin

      server.stubFor(
        put(urlMatching(s"$url.*"))
          .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(PRECONDITION_FAILED)
              .withBody(jsonBody)
          )
      )

      val result = service.getPaperlessPreference().value.futureValue.getOrElse(None)

      result mustBe Some("http://www.testurl.com")
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      TOO_MANY_REQUESTS,
      REQUEST_TIMEOUT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE,
      BAD_GATEWAY
    ).foreach { errorResponse =>
      s"return UpstreamErrorResponse when the connector retrieves a $errorResponse status" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            credentials = Credentials("", "GovernmentGateway"),
            confidenceLevel = ConfidenceLevel.L200,
            request = FakeRequest()
          )

        implicit val service = app.injector.instanceOf[PreferencesFrontendConnector]

        val url = "/paperless/activate"

        val jsonBody =
          """{}""".stripMargin

        server.stubFor(
          put(urlMatching(s"$url.*"))
            .withHeader(CONTENT_TYPE, matching(ContentTypes.JSON))
            .willReturn(
              aResponse()
                .withStatus(errorResponse)
                .withBody(jsonBody)
            )
        )

        val result = service.getPaperlessPreference().value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe errorResponse
      }
    }
  }
}

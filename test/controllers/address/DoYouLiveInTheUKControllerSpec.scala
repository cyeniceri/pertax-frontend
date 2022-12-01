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

package controllers.address

import config.ConfigDecorator
import models.dto.{AddressPageVisitedDto, Dto}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.personaldetails.InternationalAddressChoiceView

class DoYouLiveInTheUKControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: DoYouLiveInTheUKController =
      new DoYouLiveInTheUKController(
        addressJourneyCachingHelper,
        mockAuthJourney,
        cc,
        injected[InternationalAddressChoiceView],
        displayAddressInterstitialView
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    def currentRequest[A]: Request[A]          = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {

    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      override def fetchAndGetEntryDto: Option[Dto] = Some(AddressPageVisitedDto(true))

      val result = controller.onPageLoad(currentRequest)

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
    }
  }

  "onSubmit" must {

    "redirect to postcode lookup page when supplied with value = Yes (true)" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "true")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/find-address")
    }

    "redirect to 'cannot use this service' when service configured to prevent updating International Addresses" in new LocalSetup {

      lazy val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

      when(mockConfigDecorator.updateInternationalAddressInPta).thenReturn(false)

      override def controller: DoYouLiveInTheUKController =
        new DoYouLiveInTheUKController(
          addressJourneyCachingHelper,
          mockAuthJourney,
          cc,
          injected[InternationalAddressChoiceView],
          displayAddressInterstitialView
        )(mockConfigDecorator, ec)

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "false")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/cannot-use-the-service")
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      val result = controller.onSubmit(currentRequest)

      status(result) mustBe BAD_REQUEST
    }
  }
}

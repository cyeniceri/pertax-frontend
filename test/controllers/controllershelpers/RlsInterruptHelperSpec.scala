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

package controllers.controllershelpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.admin.{FeatureFlag, RlsInterruptToggle}
import models.{AddressesLock, NonFilerSelfAssessmentUser, UserName}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import services.admin.FeatureFlagService
import testUtils.Fixtures.{buildFakeAddress, buildFakeCorrespondenceAddress, buildFakePersonDetails}
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}

import scala.concurrent.Future

class RlsInterruptHelperSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(bind[FeatureFlagService].toInstance(mockFeatureFlagService))
    .build()

  val okBlock: Result = Ok("Block")

  val interrupt: Result      = SeeOther("/personal-account/update-your-address")
  val mockFeatureFlagService = mock[FeatureFlagService]

  implicit val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  val rlsInterruptHelper: RlsInterruptHelper = app.injector.instanceOf[RlsInterruptHelper]

  implicit val userRequest: UserRequest[AnyContent] = UserRequest(
    Some(Fixtures.fakeNino),
    Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    NonFilerSelfAssessmentUser,
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    None,
    Set(),
    None,
    None,
    None,
    FakeRequest()
  )

  override def beforeEach(): Unit = {
    reset(mockFeatureFlagService)
    super.beforeEach()
  }

  "enforceByRlsStatus" when {
    "the enforce getAddressStatusFromCID toggle is set to true" must {

      "return the result of the block when residential and correspondence are not 1" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "redirect to /personal-account/check-your-address when residential address status is 1" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(buildFakePersonDetails.copy(address = Some(buildFakeAddress.copy(isRls = true)))),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when correspondence address status is 1" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)))
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "return the result as s block when residential address status is 1 and residential address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(true, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(buildFakePersonDetails.copy(address = Some(buildFakeAddress.copy(isRls = true)))),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return the result as a block when postal address status is 1 and postal address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(false, true)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)))
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "return result as a block when both residential and correspondence address status is 1 and both addresses have been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(true, true)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe okBlock
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1 and residential address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(true, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

      "redirect to /personal-account/check-your-address when both residential and correspondence address status is 1 and correspondence address has been updated" in {
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(false, true)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock)).futureValue
        result mustBe interrupt
      }

    }

    "the enforce rlsInterruptToggle toggle is set to false" must {
      "return the result of a passed in block" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, false)))
        when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
          .thenReturn(Future.successful(AddressesLock(false, false)))

        implicit val userRequest: UserRequest[AnyContent] = UserRequest(
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          Some(
            buildFakePersonDetails.copy(
              address = Some(buildFakeAddress.copy(isRls = true)),
              correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true))
            )
          ),
          None,
          Set(),
          None,
          None,
          None,
          FakeRequest()
        )

        val result = rlsInterruptHelper.enforceByRlsStatus(Future(okBlock))
        result.futureValue mustBe okBlock
      }
    }
  }
}

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

package controllers.bindable

import play.api.test.Injecting
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import util.BaseSpec

class BindableSpec extends BaseSpec with Injecting {

  val safeContinueUrl = inject[SafeContinueUrl]

  trait LocalSetup {}

  "Calling continueUrlBinder.unbind" must {

    "return the key and the ContinueUrl" in {

      safeContinueUrl.continueUrlBinder
        .unbind("continue", SafeRedirectUrl("/relative/url")) mustBe "continue=%2Frelative%2Furl"
    }
  }

  "Calling continueUrlBinder.bind" must {

    "return an url when called with a relative url" in {

      val url = "/relative/url"
      safeContinueUrl.continueUrlBinder.bind("continue", Map("continue" -> Seq(url))) mustBe Some(
        Right(SafeRedirectUrl(url)))
    }

    "return error when not url" in {

      val url = "gtuygyg"
      safeContinueUrl.continueUrlBinder.bind("continue", Map("continue" -> Seq(url))) mustBe Some(
        Left(s"'$url' is not a valid continue URL"))
    }

    "return error for urls with /\\" in {

      val url = "/\\www.example.com"
      safeContinueUrl.continueUrlBinder.bind("continue", Map("continue" -> Seq(url))) mustBe Some(
        Left(s"'$url' is not a valid continue URL"))
    }

    "return error for none relative urls" in {

      val url = "http://nonrelativeurl.com"
      safeContinueUrl.continueUrlBinder.bind("continue", Map("continue" -> Seq(url))) mustBe Some(
        Left(s"Provided URL [$url] doesn't comply with redirect policy"))
    }
  }
}

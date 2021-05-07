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

package util

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.partials.{FormPartialRetriever, HeaderCarrierForPartialsConverter}

// TODO: // delete this and inject in the partial retriever where required
@Singleton
class LocalPartialRetriever @Inject()(override val httpGet: HttpClient, hc: HeaderCarrierForPartialsConverter)
    extends FormPartialRetriever {
  override def headerCarrierForPartialsConverter: HeaderCarrierForPartialsConverter = hc
}

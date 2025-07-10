/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.dashboardfeature.model


data class ClaimsUI(
    val key: String,
    val value: Any?
)

sealed class ClaimValue {
    data class Simple(val text: String): ClaimValue()
    data class Obj(val entries: Map<String,Any?>): ClaimValue()
    data class Arr(val items: List<Any?>): ClaimValue()
}

data class UserData(
    val firstName: String,
    val lastName: String,
    val claims: List<ClaimsUI>,
    val portraitBase64: String?
)

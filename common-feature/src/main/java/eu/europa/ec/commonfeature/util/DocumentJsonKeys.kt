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

package eu.europa.ec.commonfeature.util

object DocumentJsonKeys {
    const val FAMILY_NAME                    = "family_name"
    const val GIVEN_NAME                     = "given_name"
    const val BIRTH_DATE                     = "birth_date"
    const val PLACE_OF_BIRTH                 = "place_of_birth"
    const val NATIONALITY                    = "nationality"
    const val RESIDENT_ADDRESS               = "resident_address"
    const val RESIDENT_COUNTRY               = "resident_country"
    const val RESIDENT_STATE                 = "resident_state"
    const val RESIDENT_CITY                  = "resident_city"
    const val RESIDENT_POSTAL_CODE           = "resident_postal_code"
    const val RESIDENT_STREET                = "resident_street"
    const val RESIDENT_HOUSE_NUMBER          = "resident_house_number"
    const val PERSONAL_ADMIN_NUMBER          = "personal_administrative_number"
    const val PORTRAIT                       = "portrait"
    const val FAMILY_NAME_AT_BIRTH           = "family_name_birth"
    const val GIVEN_NAME_AT_BIRTH            = "given_name_birth"
    const val SEX                            = "sex"
    const val EMAIL_ADDRESS                  = "email_address"
    const val MOBILE_PHONE_NUMBER            = "mobile_phone_number"
    const val EXPIRY_DATE                    = "expiry_date"
    const val ISSUING_AUTHORITY              = "issuing_authority"
    const val ISSUING_COUNTRY                = "issuing_country"
    const val DOCUMENT_NUMBER                = "document_number"
    const val ISSUING_JURISDICTION           = "issuing_jurisdiction"
    const val LOCATION_STATUS                = "location_status"
    const val ISSUANCE_DATE                  = "issuance_date"
    const val AGE_OVER_18                    = "age_over_18"
    const val AGE_OVER_NN                    = "age_over_NN"
    const val AGE_IN_YEARS                   = "age_in_years"
    const val AGE_BIRTH_YEAR                 = "age_birth_year"
    const val TRUST_ANCHOR                   = "trust_anchor"


    const val SIGNATURE                      = "signature_usual_mark"
    const val USER_PSEUDONYM                 = "user_pseudonym"

    private const val GENDER                  = "gender"
    private const val SEX_DUP                 = SEX
    private const val AddressLocality         = "addressLocality"
    private const val BirthDate               = "birthDate"

    val GENDER_KEYS: List<String> = listOf(GENDER, SEX_DUP)

    val BASE64_IMAGE_KEYS: List<String> = listOf(PORTRAIT, SIGNATURE)

}
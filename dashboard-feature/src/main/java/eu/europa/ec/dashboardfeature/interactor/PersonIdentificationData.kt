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

package eu.europa.ec.dashboardfeature.interactor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import eu.europa.ec.commonfeature.util.docNamespace
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.model.ClaimValue
import eu.europa.ec.dashboardfeature.model.ClaimsUI
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import java.time.ZoneId
import java.time.format.DateTimeFormatter


interface PersonIdentificationDataInteractor {
    fun getPidDocuments(): List<String>
    fun getPidDocumentDetails(): List<IssuedDocument>
    fun printPidDocumentDetails()
    fun getAllIssuedDocuments(): List<IssuedDocument>
    fun printAllDocumentDetails()
    fun getUserFirstAndLastName (): Pair<String, String>
    fun getUserWithPortrait(): String
    fun getListClaims(): List<ClaimsUI>
}


class PersonIdentificationDataImpl(
    private val walletCoreDocumentsController: WalletCoreDocumentsController
) : PersonIdentificationDataInteractor {

    private val pidTypes = listOf(
        DocumentIdentifier.MdocPid,
        DocumentIdentifier.SdJwtPid
    )

    override fun getPidDocuments(): List<String> =
        walletCoreDocumentsController
            .getAllDocumentsByType(documentIdentifiers = pidTypes)
            .map { it.id }

    override fun getPidDocumentDetails(): List<IssuedDocument> =
        walletCoreDocumentsController
            .getAllDocumentsByType(documentIdentifiers = pidTypes)

    override fun printPidDocumentDetails() {
        val docs = getPidDocumentDetails()
        if (docs.isEmpty()) {
            println("No PID documents found.")
            return
        }
        printDocuments("PID Document", docs)
    }

    override fun getAllIssuedDocuments(): List<IssuedDocument> =
        walletCoreDocumentsController.getAllIssuedDocuments()

    override fun printAllDocumentDetails() {
        val docs = getAllIssuedDocuments()
        if (docs.isEmpty()) {
            println("No issued documents found.")
            return
        }
        printDocuments("Issued Document", docs)
    }


    override fun getUserFirstAndLastName (): Pair<String, String> {
        val docs = getPidDocumentDetails()

        if (docs.isEmpty()) {
            println("No PID documents found.")
            return "" to ""
        }


        val allClaims = docs.flatMap { it.data.claims }
        val firstNameClaim = allClaims.find { it.identifier.equals("given_name", ignoreCase = true) }
        val lastNameClaim  = allClaims.find { it.identifier.equals("family_name", ignoreCase = true) }
        val extractedFirstName = firstNameClaim?.value ?: ""
        val extractedLastName  = lastNameClaim?.value  ?: ""

        println("First Name: $extractedFirstName")
        println("Last Name: $extractedLastName")

        return (extractedFirstName to extractedLastName) as Pair<String, String>

    }

    override fun getUserWithPortrait(): String {
        val docs = getPidDocumentDetails()
        if (docs.isEmpty()) {
            println("No PID documents found.")
            return ""
        }

        val portraitClaim = docs
            .flatMap { it.data.claims }
            .firstOrNull { it.identifier.equals("portrait", ignoreCase = true) }

        val base64 = portraitClaim?.value?.toString()
        println("Portrait: $base64")

        return base64 ?: ""
    }

    override fun getListClaims(): List<ClaimsUI> {
        val docs = walletCoreDocumentsController.getAllIssuedDocuments()
        val allClaims = docs
            .flatMap { it.data.claims }
            .filterNot { claim ->
            when (claim.identifier.lowercase()) {
                "portrait", "given_name", "family_name" -> true
                else                                   -> false
            }
        }

        val uniqueClaims = allClaims
            .distinctBy { it.identifier.lowercase() }

        val claims = uniqueClaims.map { claim ->
            val normalizedKey = claim.identifier.replace("_"," ").lowercase()

            val v: Any? = claim.value   // o claim.value original
            val cv = when (v) {
                is Map<*,*>        -> ClaimValue.Obj(v.mapKeys { it.key.toString().replace("_"," ").lowercase() })
                is Collection<*>   -> ClaimValue.Arr(v.toList())
                else               -> ClaimValue.Simple(v.toString())
            }

            ClaimsUI(
                key   = normalizedKey,
                value = cv
            )
        }



        return claims

    }


    private fun printDocuments(label: String, docs: List<IssuedDocument>) {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        println("---- $label Details ----")
        docs.forEachIndexed { index, doc ->
            val issuedAt = doc.createdAt
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDateTime()
                ?.format(formatter)
                ?: "unknown"

            println("$label #${index + 1}")
            println("  ID         : ${doc.id}")
            println("  Format     : ${doc.format.javaClass.simpleName}")
            println("  Issued At  : $issuedAt")
            println("  Namespace  : ${doc.docNamespace ?: "n/a"}")
            println("  Credential Policy   : ${doc.credentialPolicy}...")
            println("  Claims:")
            doc.data.claims.forEach { claim ->
                println("    - ${claim.identifier}: ${claim.value}")
            }
            println("-------------------------------")
        }
    }
}

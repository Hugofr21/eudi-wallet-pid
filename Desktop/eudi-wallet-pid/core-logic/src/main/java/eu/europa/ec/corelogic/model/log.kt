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

package eu.europa.ec.corelogic.model

data class SecureDocumentMetadata(
    val documentId: String,
    val encryptedContent: String,
    val signature: String,
    val integrityHash: String,
    val timestamp: Long,
    val userId: String,
    val complianceLevel: String = "HIGH"
)

data class SecureTransactionLogData(
    val id: String,
    val encryptedData: String,
    val signature: String,
    val timestamp: Long,
    val userId: String,
    val action: String,
    val complianceMetadata: String
)

data class SecureAuditEntry(
    val sessionId: String,
    val userId: String,
    val action: String,
    val resourceId: String,
    val timestamp: Long,
    val result: String,
    val metadata: Map<String, String>
)
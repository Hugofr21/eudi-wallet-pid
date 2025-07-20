package eu.europa.ec.backuplogic.controller.dto

import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import kotlinx.serialization.Serializable

@Serializable
data class DocumentDto(
    val id: String,
    val name: String,
    val format: String,
    val documentManagerId: String,
    val createdAt: String,
    val issuerName: String? = null,
    val issuerDid: String? = null
)


fun Document.toDto(): DocumentDto {
    val formatStr = when (val f = this.format) {
        is MsoMdocFormat -> "MsoMdoc: ${f.docType}"
        is SdJwtVcFormat -> "SdJwtVc: ${f.vct}"
        else -> "Unknown"
    }

    val issuerName = this.issuerMetadata
        ?.issuerDisplay
        ?.firstOrNull()
        ?.name

    val issuerDid = this.issuerMetadata
        ?.credentialIssuerIdentifier

    return DocumentDto(
        id = this.id,
        name = this.name,
        format = formatStr,
        documentManagerId = this.documentManagerId,
        createdAt = this.createdAt.toString(),
        issuerName = issuerName,
        issuerDid = issuerDid
    )
}
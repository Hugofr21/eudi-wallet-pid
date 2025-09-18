package eu.europa.ec.backuplogic.controller.dto

import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.eudi.wallet.document.format.MsoMdocFormat
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.eudi.wallet.document.metadata.IssuerMetadata
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant


object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}


@Serializable
data class DocumentDto(
    val id: DocumentId,
    val name: String,
    val format: String,
    val documentManagerId: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    val issuerMetadata: IssuerMetadata?
)


fun Document.toDto(): DocumentDto {
    val formatStr = when (val f = this.format) {
        is MsoMdocFormat -> "MsoMdoc: ${f.docType}"
        is SdJwtVcFormat -> "SdJwtVc: ${f.vct}"
        else -> "MsoMdoc"
    }

    return DocumentDto(
        id = this.id.toString(),
        name = this.name.toString(),
        format = formatStr,
        documentManagerId = this.documentManagerId,
        createdAt = this.createdAt,
        issuerMetadata = this.issuerMetadata,
    )
}

fun DocumentDto.toDocumentIdentifier(): DocumentIdentifier {
    val formatType = when (val f = this.format) {
        is MsoMdocFormat -> f.docType
        is SdJwtVcFormat -> f.vct
        else -> {}
    }
    return createDocumentIdentifier(formatType as FormatType)
}

private fun createDocumentIdentifier(
    formatType: FormatType
): DocumentIdentifier {
    return when (formatType.lowercase()) {
        DocumentIdentifier.MdocPid.formatType.lowercase() -> DocumentIdentifier.MdocPid
        DocumentIdentifier.SdJwtPid.formatType.lowercase() -> DocumentIdentifier.SdJwtPid
        DocumentIdentifier.MdocAgeOver18ProofPseudonym.formatType.lowercase() -> DocumentIdentifier.MdocAgeOver18ProofPseudonym
        DocumentIdentifier.AgeOver18Pid.formatType.lowercase() -> DocumentIdentifier.AgeOver18Pid
        DocumentIdentifier.MdocEUDIAgeOver18.formatType.lowercase() -> DocumentIdentifier.MdocEUDIAgeOver18
        else -> DocumentIdentifier.OTHER(formatType = formatType)
    }
}

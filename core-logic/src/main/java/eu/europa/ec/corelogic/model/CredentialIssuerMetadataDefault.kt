package eu.europa.ec.corelogic.model

import eu.europa.ec.eudi.openid4vci.CredentialIssuerEndpoint
import eu.europa.ec.eudi.openid4vci.ProofTypesSupported
import kotlinx.serialization.SerialName
import java.net.URL


data class CredentialIssuerMetadataDefault(
val credentialIssuerIdentifier: CredentialIssuerId,
val authorizationServers: List<HttpsUrl> = runCatching {
    listOf(HttpsUrl.invoke(credentialIssuerIdentifier.value).getOrThrow())
}.getOrElse { emptyList() },
    val credentialEndpoint: CredentialIssuerEndpoint,
     val nonceEndpoint: CredentialIssuerEndpoint? = null,
     val deferredCredentialEndpoint: CredentialIssuerEndpoint? = null,
    val notificationEndpoint: CredentialIssuerEndpoint? = null,
     val credentialResponseEncryption: CredentialResponseEncryption = CredentialResponseEncryption.NotSupported,
     val batchCredentialIssuance: BatchCredentialIssuance = BatchCredentialIssuance.NotSupported,
     val credentialConfigurationsSupported: Map<CredentialConfigurationIdentifier, CredentialConfiguration>,
    val display: List<Display> = emptyList(),
) {
    init {

        if (credentialConfigurationsSupported.isEmpty()) {
            println("Warning: credentialConfigurationsSupported is empty")
        }
    }

    inline fun <reified T : CredentialConfiguration> findByFormat(predicate: (T) -> Boolean): Map<CredentialConfigurationIdentifier, T> {
        return credentialConfigurationsSupported.mapNotNull { (k, v) -> if (v is T && predicate(v)) k to v else null }.toMap()
    }
}


@JvmInline
value class CredentialIssuerId(val value: String) {
    init {
        require(value.isNotBlank()) { "CredentialIssuerId must not be blank" }
    }
}


@JvmInline
value class HttpsUrl(val value: URL) {
    init {
        require(value.protocol == "https") { "HttpsUrl must use HTTPS protocol" }
        require(value.toURI().fragment.isNullOrBlank()) { "HttpsUrl must not have a fragment" }
    }

    companion object {
        fun invoke(value: String): Result<HttpsUrl> = runCatching {
            val url = URL(value)
            require(url.protocol == "https") { "HttpsUrl must use HTTPS protocol" }
            HttpsUrl(url)
        }
    }
}

@JvmInline
value class CredentialConfigurationIdentifier(val value: String) {
    init {
        require(value.isNotBlank()) { "CredentialConfigurationIdentifier must not be blank" }
    }
}

@kotlinx.serialization.Serializable
sealed interface CredentialResponseEncryption {

    data object NotSupported : CredentialResponseEncryption

    data class SupportedNotRequired(
        val encryptionAlgorithmsAndMethods: SupportedEncryptionAlgorithmsAndMethods,
    ) : CredentialResponseEncryption


    data class Required(
        val encryptionAlgorithmsAndMethods: SupportedEncryptionAlgorithmsAndMethods,
    ) : CredentialResponseEncryption
}

@kotlinx.serialization.Serializable
data class SupportedEncryptionAlgorithmsAndMethods(
    val algorithms: List<String>,
    val encryptionMethods: List<String>,
) {
    init {
        require(encryptionMethods.isNotEmpty()) { "encryptionMethodsSupported cannot be empty" }
        require(algorithms.isNotEmpty()) { "algorithms must not be empty" }
    }
}

@kotlinx.serialization.Serializable
sealed interface BatchCredentialIssuance {
    @kotlinx.serialization.Serializable
    data object NotSupported : BatchCredentialIssuance

    @kotlinx.serialization.Serializable
    data class Supported(val batchSize: Int) : BatchCredentialIssuance {
        init {
            require(batchSize > 0) { "batchSize must be greater than 0" }
        }
    }
}


sealed interface CredentialConfiguration


data class SdJwtVcCredential(
    val scope: String? = null,
    @SerialName("cryptographic_binding_methods_supported") val cryptographicBindingMethodsSupported: List<String> = emptyList(),
    @SerialName("credential_signing_alg_values_supported") val credentialSigningAlgorithmsSupported: List<String> = emptyList(),
    @SerialName("proof_types_supported") val proofTypesSupported: ProofTypesSupported = ProofTypesSupported.Empty,
    val display: List<Display> = emptyList(),
    val vct: String,
    val claims: Map<String, Claim> = emptyMap(),
    val order: List<String> = emptyList(),
) : CredentialConfiguration



data class Display(
    val name: String,
    val locale: String? = null,
    val logo: Logo? = null,
    val description: String? = null,
    @SerialName("background_color") val backgroundColor: String? = null,
    @SerialName("background_image") val backgroundImage: BackgroundImage? = null,
    @SerialName("text_color") val textColor: String? = null,
) {

    data class Logo(
        val uri: String? = null,
        @SerialName("alt_text") val alternativeText: String? = null,
    )


    data class BackgroundImage(
        val uri: String? = null,
    )
}


data class Claim(
    val display: List<Display> = emptyList(),
    val mandatory: Boolean? = false,
    @SerialName("order") val order: List<String> = emptyList(),
    val nested: Map<String, Claim>? = null,
)

// Placeholder for ProofTypesSupported
@JvmInline
value class ProofTypesSupported(val values: Map<String, ProofTypeMeta> = emptyMap()) {
    companion object {
        val Empty = ProofTypesSupported(emptyMap())
    }
}

data class ProofTypeMeta(
    @SerialName("proof_signing_alg_values_supported") val algorithms: List<String>,
    val keyAttestationRequirement: String = "NotRequired",
)
package eu.europa.ec.corelogic.model

enum class ProviderCategory {
    SIGNATURE,
    SEAL,
    WEBSITE_AUTH,
    REMOTE_SIGNATURE,
    WALLETS,
    PID,
    EAA,
    RELYING_PARTY,
    OTHER
}

data class QtspInfo(
    val country: String,
    val providerName: String,
    val serviceType: String,
    val status: String,
    val category: ProviderCategory,
    val digitalIdentityBase64: String? = null,
)

@kotlinx.serialization.Serializable
data class TslLocationInfo(
    val country: String,
    val tslUrl: String,
    val category: ProviderCategory = ProviderCategory.OTHER
)
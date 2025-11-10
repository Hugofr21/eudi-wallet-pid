package eu.europa.ec.authenticationlogic.model.did

data class DidIdentity(
    val didIdentifier: String,
    val alias: String,
    val displayName: String,
    val didDocumentJson: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

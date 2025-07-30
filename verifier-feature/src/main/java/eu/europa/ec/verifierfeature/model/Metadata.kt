package eu.europa.ec.verifierfeature.model


data class ClientMetadata(
    val vpFormats: Map<String, VpFormat> // vp_formats
)

data class VpFormat(
    // “vc+sd-jwt” e “dc+sd-jwt”
    val sdJwtAlgValues: List<String>? = null, // sd-jwt_alg_values
    val kbJwtAlgValues: List<String>? = null, // kb-jwt_alg_values
    // “mso_mdoc”
    val alg: List<String>? = null // alg
)
package eu.europa.ec.verifierfeature.model

data class PresentationRequest(
    val type: String, //"id_token"
    val id_token_type: String, //"subject_signed_id_token"
    val jar_mode: String, //"by_value"
    val nonce: String
)
package eu.europa.ec.corelogic.model.did


import com.google.gson.annotations.SerializedName

/**
 * Decentralized Identifiers (DIDs) v1.1
 * W3C Working Draft 18 September 2025
 *
 * Algorithm EC -> P-256
 * */

data class DidDocument(
    @SerializedName("@context")
    val context: List<String>,
    val id: String, // did:key:method
    val verificationMethod: List<VerificationMethod>,
    val authentication: List<String>,
    val assertionMethod: List<String>,
    val keyAgreement: List<String>,
    val service: List<Service>? = null,
    val proof: Proof? = null,
    val created: String? = null,
    val updated: String? = null,
    val expires: String? = null
)

data class Proof(
    val type: String,
    val created: String,
    val proofPurpose: String,
    val verificationMethod: String,
    val jws: String? = null,
    val proofValue: String? = null,
    val domain: String? = null,
    val challenge: String? = null
)

data class VerificationMethod(
    val id: String,
    val type: String,
    val controller: String,
    val publicKeyJwk: PublicKeyJwk?
)

data class PublicKeyJwk(
    val kty: String,
    val crv: String,
    val x: String,
    val y: String
)

data class Service(
    val id: String,
    val type: String,
    val serviceEndpoint: String
)
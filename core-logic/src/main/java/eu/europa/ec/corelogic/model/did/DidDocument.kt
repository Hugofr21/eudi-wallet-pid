package eu.europa.ec.corelogic.model.did


import com.google.gson.annotations.SerializedName

/**
 * Decentralized Identifiers (DIDs) v1.1
 * W3C Working Draft 18 September 2025
 * The did:key Method v0.9
 * A DID Method for Static Cryptographic Keys
 * Draft Community Group Report 02 November 2025
 * Algorithm EC -> P-256
 *
 * did:key
 * did:peer
 * */




data class DidDocument(
    @SerializedName("@context")
    val context: List<String>,
    val id: String, // did:key:method
    val verificationMethod: List<VerificationMethod>,
    val authentication: List<String>,
    val assertionMethod: List<String>,
    val keyAgreement: List<String>? = null,
    val service: List<Service>? = null,
    val proof: Proof? = null,
    val created: String? = null,
    val updated: String? = null,
    val expires: String? = null,
    val capabilityDelegation: List<String>? = null,
    val capabilityInvocation: List<String>,

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
    val publicKeyJwk: PublicKeyJwk?,
    val publicKeyMultibase: String? ? = null
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
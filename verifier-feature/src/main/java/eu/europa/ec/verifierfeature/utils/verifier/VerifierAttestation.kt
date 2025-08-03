package eu.europa.ec.verifierfeature.utils.verifier

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.JWTClaimsSet
import net.minidev.json.JSONArray
import net.minidev.json.JSONObject
import com.nimbusds.jose.util.JSONObjectUtils

object JarUtils {
    @Throws(java.text.ParseException::class)
    fun parseRequestObject(jarJwt: String): JWTClaimsSet =
        SignedJWT.parse(jarJwt).jwtClaimsSet

    fun JWTClaimsSet.verifierAttestationsArray(): JSONArray? =
        getClaim("verifier_attestations") as? JSONArray

    fun SignedJWT.verifySignature(jwkSet: JWKSet): Boolean {
        val key = jwkSet.keys.find { it.keyID == this.header.keyID } ?: return false
        val verifier = com.nimbusds.jose.crypto.RSASSAVerifier(key.toRSAKey())
        return this.verify(verifier)
    }
}

data class VerifierAttestation(val jwt: SignedJWT)

object VerifierAttestations {
    fun fromJson(array: JSONArray?): List<VerifierAttestation> {
        require(array != null) { "verifier_attestations must not be null" }
        return array.map { elem ->
            when (elem) {
                is JSONObject -> {
                    val jwt = JSONObjectUtils.getString(elem, "jwt")
                    VerifierAttestation(SignedJWT.parse(jwt))
                }
                is String -> VerifierAttestation(SignedJWT.parse(elem))
                else -> throw IllegalArgumentException("Unsupported entry: ${elem::class}")
            }
        }
    }
}
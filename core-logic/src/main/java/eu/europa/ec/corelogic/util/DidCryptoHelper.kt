package eu.europa.ec.corelogic.util

import eu.europa.ec.corelogic.model.did.PublicKeyJwk
import org.multipaz.crypto.toEcPublicKey
import java.security.MessageDigest
import java.security.PublicKey


object DidCryptoHelper {

    /**
     * Converts a P-256 PublicKey to the "did:peer" format.
     */
    fun convertPublicKeyToDidKey_P256(publicKey: PublicKey, method:String): String {
        val md  = MessageDigest.getInstance("SHA-256")
        val keyHash = md.digest(publicKey.encoded)
        return "did:peer:eudi${keyHash}"
    }

    /**
     * Converts a Java PublicKey into a JSON Web Key (JWK) format.
     * This is the default for 'verificationMethod'.
     */
    fun convertPublicKeyToJwk(publicKey: PublicKey): PublicKeyJwk {

        val alg = publicKey.algorithm

        return PublicKeyJwk(
            kty = alg,
            crv = "P-256", // default
            x = "",
            y = ""
        )
    }
}
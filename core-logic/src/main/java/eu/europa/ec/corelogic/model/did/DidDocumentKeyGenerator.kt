package eu.europa.ec.corelogic.model.did


import io.github.novacrypto.base58.Base58
import java.math.BigInteger
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

/**
 * did-key-format := did:key:<mb-value>
 * did-key-base58-btc-format := did:key:MULTIBASE(base58-btc, MULTICODEC(public-key-type, raw-public-key-bytes))
 * did-peer-format := did:peer:<mb-value>
 * mb-value       := (z[a-km-zA-HJ-NP-Z1-9]+|u[A-Za-z0-9_-]+)
 */
object DidDocumentKeyGenerator{
    // --- Cryptographic Constants (P-256) ---
    // Multicodec prefix for public key secp256r1 (P-256).
    // The code is 0x1200.
    private val P256_MULTICODEC_PREFIX = byteArrayOf(0x12.toByte(), 0x00.toByte())

    // Prefix for an uncompressed EC key, as per P1363.
    // 0x04 indicates that the X and Y coordinates follow.
    private const val UNCOMPRESSED_KEY_PREFIX = 0x04.toByte()

    private fun createDidDocumentKey(
        publicKey: PublicKey,
    ): String {

        val ecPublicKey = validate(publicKey)

        val xBytes = bigIntegerToFixedBytes(ecPublicKey.w.affineX, 32)
        val yBytes = bigIntegerToFixedBytes(ecPublicKey.w.affineY, 32)

        val rawPublicKey = ByteArray(1 + 32 * 2).apply {
            this[0] = UNCOMPRESSED_KEY_PREFIX
            System.arraycopy(xBytes, 0, this, 1, 32)
            System.arraycopy(yBytes, 0, this, 1 + 32, 32)
        }

        val multicodecPayload = P256_MULTICODEC_PREFIX + rawPublicKey

        val encoded = Base58.base58Encode(multicodecPayload)

        return "did:key:z$encoded"
    }

    /**
     * Generates the DID Document (as a data class object) following
     * the link specification (type: Multikey).
     * @param publicKey The P-256 public key.
     * @return The DidDocument object.
     */
    fun createDidDocument(publicKey: PublicKey): DidDocument {

        val didId = createDidDocumentKey(publicKey)

        // 2. O 'publicKeyMultibase' é o fragmento do ID (o "mb-value")
        val multibaseFragment = didId.substringAfter("did:key:")

        // 3. O ID do VM (o "fragment identifier")
        val vmId = "$didId#$multibaseFragment"

        // 4. Criar o VerificationMethod (usando Multikey)
        val vm = VerificationMethod(
            id = vmId,
            type = "Multikey",
            controller = didId,
            publicKeyMultibase = multibaseFragment,
            publicKeyJwk = null
        )

        // 5. Assembling the DID Document
        // The specification and examples indicate that, for signing keys
        // (such as P-256 and Ed25519), the key is used for all
        // verification relationships, except keyAgreement.
        return DidDocument(
            context = listOf("https://www.w3.org/ns/did/v1"),
            id = didId,
            verificationMethod = listOf(vm),
            authentication = listOf(vmId),
            assertionMethod = listOf(vmId),
            capabilityDelegation = listOf(vmId),
            capabilityInvocation = listOf(vmId),

            // KeyAgreement is for encryption (X25519, P-256-ECDH).
            // The P-256 signing key should *not* be used for this.
            keyAgreement = emptyList(),
            // did:key does not require standard services
            service = null
        )
    }

    /**
     * Converts a BigInteger (coordinate) into a fixed-size byte array,
     * handling sign byte (0x00) and padding.
     */
    private fun bigIntegerToFixedBytes(bi: BigInteger, size: Int): ByteArray {
        val bytes = bi.toByteArray()

        if (bytes.size == size) {
            return bytes
        }

        if (bytes.size == size + 1 && bytes[0] == 0x00.toByte()) {
            return bytes.copyOfRange(1, bytes.size)
        }

        if (bytes.size < size) {
            val padded = ByteArray(size)
            System.arraycopy(bytes, 0, padded, size - bytes.size, bytes.size)
            return padded
        }

        throw IllegalArgumentException("Size unexpected of BigInteger (${bytes.size} bytes) for the expected $size")
    }

    private fun validate(publicKey: PublicKey): ECPublicKey {

        if (publicKey !is ECPublicKey) {
            throw IllegalArgumentException("The public key is not of type ECPublicKey..")
        }

        if (!publicKey.params.curve.toString().contains("secp256r1")) {
            throw IllegalArgumentException("The key curve is not secp256r1 (P-256).")
        }
        return publicKey
    }
}
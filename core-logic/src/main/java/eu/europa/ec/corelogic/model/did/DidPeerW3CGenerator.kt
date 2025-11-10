package eu.europa.ec.corelogic.model.did

import android.util.Base64
import io.github.novacrypto.base58.Base58
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

/**
 * Decentralized Identifiers (DIDs) v1.1
 * Core architecture, data model, and representations
 * W3C Working Draft 18 September 202
 *
 * Generates 'did:peer:2' and its genesis 'DidDocument'
 * following the "Peer-to-Peer DID Method Specification".
 * Focused on P-256 keys (secp256r1) for Authentication
 * and services (e.g., DIDCommMessaging).
 */
object DidPeerW3CGenerator {

    // --- Constantes Criptográficas (P-256) ---
    private val P256_MULTICODEC_PREFIX = byteArrayOf(0x12.toByte(), 0x00.toByte())
    private const val UNCOMPRESSED_KEY_PREFIX = 0x04.toByte()
    private const val P256_COORDINATE_SIZE_BYTES = 32

    // --- Constantes Criptográficas (Hash) ---
    // Multihash prefixo para SHA-256 (0x12) + tamanho (0x20 = 32 bytes)
    private val SHA256_MULTIHASH_PREFIX = byteArrayOf(0x12, 0x20)


    /**
     * Creates the genesis DID document for a new 'did:peer:2'.
     * The document ID (did:peer:2...) is hash-generated
     * from the key and the service.
     * @param authPublicKey The P-256 key for authentication (signature).
     * @param serviceType The service type (e.g., "DIDCommMessaging").
     * @param serviceEndpoint The endpoint URL (e.g., "https://relay.example.com").
     * @return The DidDocument object.

     */
    fun createDidDocument(
        authPublicKey: PublicKey,
        serviceType: String,
        serviceEndpoint: String
    ): DidDocument {

        val ecAuthKey = validateAndGetEcKey(authPublicKey)


        val authKeyMultibase = createDidKeyMultibaseFragment(ecAuthKey)

        val keyBlockJson = """{"t":"k","k":"$authKeyMultibase","c":"P-256"}"""
        val encodedKeyBlock = hashAndEncodeBase58(keyBlockJson)


        val serviceBlockJson = """{"t":"s","s":"$serviceEndpoint"}"""
        val encodedServiceBlock = hashAndEncodeBase58(serviceBlockJson)

        // id:peer:2.Ez[hash_service].Vz[hash_key]
        // exemple: did:peer:2.Vz<base>.Ez<base>
        val didPeerId = "did:peer:2.Ez$encodedServiceBlock.Vz$encodedKeyBlock"

        val jwk = createPublicKeyJwk(ecAuthKey)
        val vmId = "$didPeerId#key-1"

        val vm = VerificationMethod(
            id = vmId,
            type = "JsonWebKey2020",
            controller = didPeerId,
            publicKeyMultibase = null,
            publicKeyJwk = jwk
        )


        val service = Service(
            id = "$didPeerId#service-1",
            type = serviceType,
            serviceEndpoint = serviceEndpoint
        )

        return DidDocument(
            context = listOf("https://www.w3.org/ns/did/v1"),
            id = didPeerId,
            verificationMethod = listOf(vm),

            authentication = listOf(vmId),
            assertionMethod = listOf(vmId),
            capabilityDelegation = listOf(vmId),
            capabilityInvocation = listOf(vmId),

            keyAgreement = emptyList(),

            service = listOf(service)
        )
    }

    /**
     * Validates and converts PublicKey to ECPublicKey.
     */
    private fun validateAndGetEcKey(publicKey: PublicKey): ECPublicKey {
        if (publicKey !is ECPublicKey) {
            throw IllegalArgumentException("The public key is not of type ECPublicKey..")
        }
        return publicKey
    }

    /**
     * Helper: Creates the multibase fragment (zDn...) of a P-256 key.
     * Used for ID hash calculation.
     */
    private fun createDidKeyMultibaseFragment(publicKey: ECPublicKey): String {
        val xBytes = bigIntegerToFixedBytes(publicKey.w.affineX, P256_COORDINATE_SIZE_BYTES)
        val yBytes = bigIntegerToFixedBytes(publicKey.w.affineY, P256_COORDINATE_SIZE_BYTES)

        val rawPublicKey = ByteArray(1 + P256_COORDINATE_SIZE_BYTES * 2).apply {
            this[0] = UNCOMPRESSED_KEY_PREFIX
            System.arraycopy(xBytes, 0, this, 1, P256_COORDINATE_SIZE_BYTES)
            System.arraycopy(yBytes, 0, this, 1 + P256_COORDINATE_SIZE_BYTES, P256_COORDINATE_SIZE_BYTES)
        }
        val multicodecPayload = P256_MULTICODEC_PREFIX + rawPublicKey

        return "z${Base58.base58Encode(multicodecPayload)}" // Retorna "zDn..."
    }

    /**
     * Helper: Created the objet PublicKeyJwk (of document).
     */
    private fun createPublicKeyJwk(publicKey: ECPublicKey): PublicKeyJwk {
        val x = bigIntegerToBase64Url(publicKey.w.affineX, P256_COORDINATE_SIZE_BYTES)
        val y = bigIntegerToBase64Url(publicKey.w.affineY, P256_COORDINATE_SIZE_BYTES)

        return PublicKeyJwk(
            kty = "EC",
            crv = "P-256",
            x = x,
            y = y
        )
    }

    /**
     * Helper: Hashes (SHA-256), prefix (Multihash) and codification (Base58-btc).
     */
    private fun hashAndEncodeBase58(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        val multihashPayload = SHA256_MULTIHASH_PREFIX + hash
        return Base58.base58Encode(multihashPayload)
    }

    /**
     * Converts a BigInteger for Base64URL-encoded string the size fixe.
     */
    private fun bigIntegerToBase64Url(bi: BigInteger, size: Int): String {
        val bytes = bigIntegerToFixedBytes(bi, size)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Converts a BigInteger (coordinate) into a fixed-size byte array.
     */
    private fun bigIntegerToFixedBytes(bi: BigInteger, size: Int): ByteArray {
        val bytes = bi.toByteArray()
        return when {
            bytes.size == size -> bytes
            bytes.size == size + 1 && bytes[0] == 0x00.toByte() -> bytes.copyOfRange(1, bytes.size)
            bytes.size < size -> ByteArray(size).apply {
                System.arraycopy(bytes, 0, this, size - bytes.size, bytes.size)
            }
            else -> throw IllegalArgumentException("Size unexpected of BigInteger (${bytes.size} bytes) for $size")
        }
    }
}
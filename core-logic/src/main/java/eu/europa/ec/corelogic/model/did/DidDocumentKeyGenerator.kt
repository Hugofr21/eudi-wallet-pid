package eu.europa.ec.corelogic.model.did


import org.bitcoinj.base.Base58
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
    // --- Constantes Criptográficas (P-256) ---
    // Multicodec prefixo para secp256r1 (P-256) public key.
    // O código é 0x1200.
    private val P256_MULTICODEC_PREFIX = byteArrayOf(0x12.toByte(), 0x00.toByte())
    // Prefixo para uma chave EC não comprimida, conforme P1363.
    // 0x04 indica que se seguem as coordenadas X e Y.
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

        val encoded = Base58.encode(multicodecPayload)

        return "did:key:z$encoded"
    }

    /**
     * Gera o Documento DID (como objeto data class) seguindo
     * a especificação do link (type: Multikey).
     *
     * @param publicKey A chave pública P-256.
     * @return O objeto DidDocument.
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

        // 5. Montar o Documento DID
        // A especificação e os exemplos indicam que, para chaves de assinatura
        // (como P-256 e Ed25519), a chave é usada para todas as
        // relações de verificação, exceto keyAgreement.
        return DidDocument(
            context = listOf("https://www.w3.org/ns/did/v1"),
            id = didId,
            verificationMethod = listOf(vm),
            authentication = listOf(vmId),
            assertionMethod = listOf(vmId),
            capabilityDelegation = listOf(vmId),
            capabilityInvocation = listOf(vmId),

            // KeyAgreement é para cifragem (X25519, P-256-ECDH).
            // A chave de assinatura P-256 *não* deve ser usada para isto.
            keyAgreement = emptyList(),
            // did:key não requer serviços por defeito
            service = null
        )
    }

    /**
     * Converte um BigInteger (coordenada) para um array de bytes de tamanho fixo,
     * lidando com o byte de sinal (0x00) e padding.
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

        throw IllegalArgumentException("Tamanho inesperado do BigInteger (${bytes.size} bytes) para o esperado $size")
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
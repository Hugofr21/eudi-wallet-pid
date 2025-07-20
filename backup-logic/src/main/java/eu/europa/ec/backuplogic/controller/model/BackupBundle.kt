package eu.europa.ec.backuplogic.controller.model

import kotlinx.serialization.Serializable


@Serializable
data class SecurityParams(
    val requireBiometric: Boolean,
    val requirePin: Boolean,
    val authValidityDurationSeconds: Int,
    val mnemonicPhrase: MnemonicPhrase? = null
)

@Serializable
data class MnemonicPhrase(
    val mnemonic: String? = null,
    val salt: String,
    val hash: String
)


@Serializable
data class KdfParams(
    val alg: String,
    val iters: Int,
    val keyLen: Int
)

@Serializable
data class FileEntry(
    val identifier: String,
    val name: String,
    val nonce: String,
    val ciphertext: String
)

@Serializable
data class Credential(
    val id: String,
    val type: String,
    val iv: ByteArray,
    val alg: String,
    val data: ByteArray?
)

@Serializable
data class Signature(
    val alg: String,
    val sig: String
)

@Serializable
data class BackupBundle(
    val kdf: KdfParams,
    val securityParams: SecurityParams,
    val files: List<FileEntry>? = null,
    val credentials: List<Credential>,
    var signature: Signature? = null
)
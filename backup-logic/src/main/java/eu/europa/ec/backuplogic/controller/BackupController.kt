/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.backuplogic.controller

import android.net.Uri
import android.util.Base64
import android.util.Base64.NO_WRAP
import android.util.Base64.encodeToString
import androidx.core.content.FileProvider
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.authenticationlogic.controller.authentication.PassphraseAuthenticationController
import eu.europa.ec.backuplogic.controller.dto.DocumentDto
import eu.europa.ec.backuplogic.controller.dto.toDto
import eu.europa.ec.backuplogic.controller.model.BackupBundle
import eu.europa.ec.backuplogic.controller.model.Credential
import eu.europa.ec.backuplogic.controller.model.KdfParams
import eu.europa.ec.backuplogic.controller.model.MnemonicPhrase
import eu.europa.ec.backuplogic.controller.model.SecurityParams
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.storagelogic.model.BackupLog
import eu.europa.ec.businesslogic.util.crypto.HashUtils
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.SecureRandom

interface BackupController {
    suspend fun exportBackup(passPhrase: List<String>, provider: String): List<Uri>
    suspend fun deleteBackup(identifier: String): Boolean
    suspend fun restoreBackup(file: Uri , passPhrase: List<String>): List<String>
    suspend fun getLastBackup(): BackupLog?
    fun existBackupMkdir(): Boolean

    suspend fun finalizeRestoreOptions(options: List<String>, overwriteExisting: Boolean = false): Boolean

}

class BackupControllerImpl(
    private val context: ResourceProvider,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val cryptoController: CryptoController,
    private val passphraseAuthenticationController: PassphraseAuthenticationController,
    private val walletBackupLogController: WalletBackupLogController,
) : BackupController {

    companion object {
        private const val LOG_FILE_NAME_JSON = "eudi-android-wallet-backup%g.enc.json"
        private const val PBKDF2_ITERATIONS  = 310_000   // OWASP 2023
        private const val KEY_LENGTH_BITS    = 256
        private const val PBKDF2_ALGORITHM   = "PBKDF2WithHmacSHA256"
        private const val GCM_IV_LENGTH      = 12
        private const val GCM_TAG_LENGTH     = 128
    }

    @Volatile
    private var cachedDecryptedCredentials: List<DocumentDto> = emptyList()

    private val logsDir = File(context.provideContext().filesDir.absolutePath + "/backup").apply { mkdirs() }


    override suspend fun exportBackup(passPhrase: List<String>, provider: String): List<Uri> {
        passphraseAuthenticationController.setPassphrase(passPhrase)

        val json = assembleBackupBundle(passPhrase)
            ?: throw IllegalStateException("Backup bundle is null")

        val encryptedFile = createdFileJsonEncrypt(json, passPhrase)

        val backupLog = BackupLog(
            identifier = HashUtils.sha256(
                (provider + System.currentTimeMillis()).toByteArray()
            ),
            provider   = provider,
            value      = LOG_FILE_NAME_JSON,
            createdAt  = System.currentTimeMillis(),
        )
        walletBackupLogController.storeBackupLog(backupLog)

        val uri = FileProvider.getUriForFile(
            context.provideContext(), "${context.provideContext().packageName}.provider", encryptedFile
        )
        return listOf(uri)
    }

    override suspend fun deleteBackup(identifier: String): Boolean {
        walletBackupLogController.deleteBackupLog(identifier)
        File(logsDir, identifier).takeIf { it.exists() }?.delete()
        return false
    }

    override suspend fun restoreBackup(file: Uri, passPhrase: List<String>): List<String> {
        val decryptedFile = decryptZipFile(file, passPhrase)

        var jsonString: String? = null
        ZipInputStream(FileInputStream(decryptedFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".json")) {
                    jsonString = zip.bufferedReader().readText()
                    break
                }
                entry = zip.nextEntry
            }
        }
        if (jsonString == null) throw IllegalStateException("JSON file not found in backup")

        val bundle = Gson().fromJson(jsonString, BackupBundle::class.java)

        val saltBytes = passphraseAuthenticationController.retrieveSalt()
        val keyBytes  = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

        return try {

            verifyBundleHmac(bundle, keyBytes)

            val verifier  = cryptoController.computeVerifier(keyBytes)
            val storedB64 = bundle.securityParams.mnemonicPhrase?.hash
                ?: return emptyList()
            val stored = Base64.decode(storedB64, NO_WRAP)

            if (!java.security.MessageDigest.isEqual(verifier, stored)) {
                return emptyList()
            }

            cachedDecryptedCredentials = decryptCredentials(bundle, keyBytes)

            listOfNotNull(
                bundle.securityParams.biometric?.hash?.let { "Biometric" },
                bundle.securityParams.pin?.hash?.let { "Pin" },
                bundle.credentials.any { it.data != null }.takeIf { it }?.let { "Verifiable Credentials" },
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    override suspend fun getLastBackup(): BackupLog? {
        val log = walletBackupLogController.getClosestBackupLog(System.currentTimeMillis())
        return log?.let {
            val fmt = java.text.SimpleDateFormat("yy-MM-dd", java.util.Locale.getDefault())
            it.copy(value = fmt.format(Date(it.createdAt)))
        }
    }

    override fun existBackupMkdir(): Boolean =
        logsDir.exists() && logsDir.isDirectory && logsDir.listFiles()?.isNotEmpty() == true

    override suspend fun finalizeRestoreOptions(options: List<String>, overwriteExisting: Boolean): Boolean {
        if (cachedDecryptedCredentials.isEmpty()) return false

        val json = Json { encodeDefaults = true }

        for (credential in cachedDecryptedCredentials) {
            val existing = walletCoreDocumentsController.getDocumentById(credential.id)
            if (existing != null && !overwriteExisting) continue

            json.encodeToString(DocumentDto.serializer(), credential)
            walletCoreDocumentsController.storeBookmark(credential.id)
        }

        cachedDecryptedCredentials = emptyList()
        return true
    }

    private fun assembleBackupBundle(passPhrase: List<String>): String? {
        val documentsDto = walletCoreDocumentsController.getAllDocuments().map { it.toDto() }
        val saltBytes = passphraseAuthenticationController.retrieveSalt()
        val keyBytes = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

        return try {
            val kdf = KdfParams(alg = PBKDF2_ALGORITHM, iters = PBKDF2_ITERATIONS, keyLen = KEY_LENGTH_BITS)
            val verifier = cryptoController.computeVerifier(keyBytes)
            val securityParams = SecurityParams(
                requireBiometric = false,
                requirePin = false,
                authValidityDurationSeconds = 30,
                mnemonicPhrase = MnemonicPhrase(hash = encodeToString(verifier, NO_WRAP))
            )

            val rng = SecureRandom.getInstanceStrong()
            val serializer = Json { encodeDefaults = true }

            val credentials = documentsDto.map { doc ->
                val plainBytes = serializer.encodeToString(doc).toByteArray(Charsets.UTF_8)
                val ivBytes = ByteArray(GCM_IV_LENGTH).also { rng.nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, ivBytes))
                }
                Credential(
                    id = doc.id,
                    type = doc.format::class.simpleName ?: "unknown",
                    alg = "AES/GCM/NoPadding",
                    iv = encodeToString(ivBytes, NO_WRAP),
                    data = encodeToString(cipher.doFinal(plainBytes), NO_WRAP)
                )
            }

            Gson().toJson(BackupBundle(kdf = kdf, securityParams = securityParams, credentials = credentials, signature = null))
        } finally {
            keyBytes.fill(0)
        }
    }

    /** Verifies the bundle HMAC before decrypting any credential. */
    private fun verifyBundleHmac(bundle: BackupBundle, keyBytes: ByteArray) {
        val storedSig = bundle.signature?.sig
            ?: throw SecurityException("Backup has no signature – rejected")

        val sig = bundle.signature
        bundle.signature = null
        val payload = Gson().toJson(bundle).toByteArray(Charsets.UTF_8)
        bundle.signature = sig

        val computed = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(keyBytes, "HmacSHA256"))
        }.doFinal(payload)

        val stored = Base64.decode(storedSig, NO_WRAP)
        if (!java.security.MessageDigest.isEqual(computed, stored)) {
            throw SecurityException("Backup integrity check failed – HMAC mismatch")
        }
    }

    fun decryptCredentials(bundle: BackupBundle, keyBytes: ByteArray): List<DocumentDto> {
        val jsonParser = Json { ignoreUnknownKeys = true }
        val secretKey  = SecretKeySpec(keyBytes, "AES")

        return bundle.credentials.mapIndexed { index, credential ->
            try {

                val ivBytes    = Base64.decode(credential.iv,   NO_WRAP)
                val cipherBytes = Base64.decode(credential.data, NO_WRAP)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(
                        DECRYPT_MODE,
                        secretKey,
                        GCMParameterSpec(GCM_TAG_LENGTH, ivBytes),
                    )
                }
                val plainBytes = cipher.doFinal(cipherBytes)
                val jsonString = String(plainBytes, Charsets.UTF_8)
                println("Credencial decriptada com sucesso: $jsonString")

                jsonParser.decodeFromString<DocumentDto>(String(plainBytes, Charsets.UTF_8))
            } catch (e: AEADBadTagException) {
                throw SecurityException("Credential #$index: authentication tag mismatch – tampered?", e)
            } catch (e: GeneralSecurityException) {
                throw IllegalStateException("Cryptographic failure decrypting credential #$index", e)
            }
        }
    }

    private fun createdFileJsonEncrypt(json: String, passPhrase: List<String>): java.io.File {
        val jsonFile = File(logsDir, LOG_FILE_NAME_JSON)
        jsonFile.writeText(json)

        val now     = Instant.ofEpochMilli(System.currentTimeMillis())
        val zipFile = File(logsDir, "eudi_wallet_backup_${DateTimeFormatter.ISO_INSTANT.format(now)}.zip")

        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val entry = ZipEntry(jsonFile.name)
                zos.putNextEntry(entry)
                jsonFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        jsonFile.delete()
        return encryptZipFile(zipFile, passPhrase)
    }

    private fun encryptZipFile(zipFile: File, passPhrase: List<String>): File {
        val saltBytes = passphraseAuthenticationController.retrieveSalt()
        val ivBytes   = passphraseAuthenticationController.retrieveIv()
        val keyBytes  = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    ENCRYPT_MODE,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(GCM_TAG_LENGTH, ivBytes),
                )
            }
            val encFile = File(zipFile.parent, "${zipFile.name}.enc")
          FileInputStream(zipFile).use { fis ->
                FileOutputStream(encFile).use { fos ->
                   CipherOutputStream(fos, cipher).use { cos -> fis.copyTo(cos) }
                }
            }
            zipFile.delete()
            encFile
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun decryptZipFile(encryptedStream: Uri, passPhrase: List<String>): File {
        val saltBytes = passphraseAuthenticationController.retrieveSalt()
        val keyBytes = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

        return try {
            context.provideContext().contentResolver.openInputStream(encryptedStream).use { stream ->
                requireNotNull(stream) { "Fluxo de dados indisponível." }
                val ivBytes = ByteArray(GCM_IV_LENGTH)
                if (stream.read(ivBytes) != GCM_IV_LENGTH) {
                    throw SecurityException("Arquivo corrompido: tamanho insuficiente para extração do vetor.")
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, ivBytes))
                }
                val encryptedBytes = stream.readBytes()
                val decryptedBytes = try {
                    cipher.doFinal(encryptedBytes)
                } catch (e: AEADBadTagException) {
                    throw SecurityException("Falha de integridade GCM: arquivo adulterado ou senha incorreta.", e)
                }
                File.createTempFile("decrypted", ".zip", context.provideContext().cacheDir).also { file ->
                    FileOutputStream(file).use { f -> f.write(decryptedBytes) }
                }
            }
        } finally {
            keyBytes.fill(0)
        }
    }
}
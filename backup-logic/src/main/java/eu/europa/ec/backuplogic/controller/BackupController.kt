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

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Base64.NO_WRAP
import androidx.core.content.FileProvider
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.authenticationlogic.controller.authentication.PassphraseAuthenticationController
import eu.europa.ec.backuplogic.controller.dto.DocumentDto
import eu.europa.ec.backuplogic.controller.dto.toDocumentIdentifier
import eu.europa.ec.backuplogic.controller.dto.toDto
import eu.europa.ec.backuplogic.controller.model.BackupBundle
import eu.europa.ec.backuplogic.controller.model.Credential
import eu.europa.ec.backuplogic.controller.model.KdfParams
import eu.europa.ec.backuplogic.controller.model.MnemonicPhrase
import eu.europa.ec.backuplogic.controller.model.SecurityParams
import eu.europa.ec.backuplogic.controller.model.Signature
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.storagelogic.model.BackupLog
import eu.europa.ec.businesslogic.util.crypto.HashUtils
import eu.europa.ec.eudi.wallet.document.DocumentExtensions.getDefaultCreateDocumentSettings
import eu.europa.ec.eudi.wallet.issue.openid4vci.Offer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
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
import java.io.StringWriter
import java.security.GeneralSecurityException

interface BackupController {
    suspend fun exportBackup(passPhrase: List<String>, provider: String): List<Uri>
    suspend fun deleteBackup(identifier: String): Boolean
    suspend fun restoreBackup(file: Uri , passPhrase: List<String>): List<String>
    suspend fun getLastBackup(): BackupLog?
    fun existBackupMkdir(): Boolean

    suspend fun finalizeRestoreOptions(options: List<String>, overwriteExisting: Boolean = false): Boolean

}


class BackupControllerImpl(
    private val context: Context,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val cryptoController: CryptoController,
    private val passphraseAuthenticationController: PassphraseAuthenticationController,
    private val walletBackupLogController: WalletBackupLogController

) : BackupController  {
    companion object {
        private const val LOG_FILE_NAME_JSON = "eudi-android-wallet-backup%g.enc.json"
        private const val ITERATIONS = 100000
        private const val KEY_LENGTH = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    }

    @Volatile
    private var cachedDecryptedCredentials: List<DocumentDto> = emptyList()
    private val logsDir = File(context.filesDir.absolutePath + "/backup").apply { mkdirs() }


    /**
     *  Created a new backup file.
     *  Using the Key derivation function
     *  saving credentials in encrypted files.
     *  mnemonic phrase in plain text.
     */

    override suspend fun exportBackup(passPhrase: List<String>, provider: String):List<Uri>{
        // save password in storage. this device

        passphraseAuthenticationController.setPassphrase(passPhrase)
        println("passphrase Authentication Controller set Passphrase")
        // created struct json file
        val json = assembleBackupBundle(passPhrase) ?: throw IllegalStateException("Backup bundle is null")
        println("Json $json")

        // read json file and created zip
        val encryptedFile = createdFileJsonEncrypt(json, passPhrase)

        // created log backup
        val backupLog = BackupLog(
            identifier = HashUtils.sha256((provider + System.currentTimeMillis()).toByteArray()),
            provider = provider,
            value =LOG_FILE_NAME_JSON ,
            createdAt = System.currentTimeMillis()
        )

        walletBackupLogController.storeBackupLog(backupLog)


        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            encryptedFile
        )
        return listOf(uri)
    }


    /**
     *  Delete backup before version, so current version can be restored
     */
    override suspend fun deleteBackup(identifier: String): Boolean {
        walletBackupLogController.deleteBackupLog(identifier)
        val file = File(logsDir, identifier)
        if (file.exists()) {
            file.delete()
        }
        return false
    }


    /**
     * Read the zip enc file and extract the JSON
     * Check the phrase that the user is using with this JSON
     * If valid, decrypt the file en.json
     * Read the contents and add the Wallet Controller document
     */
    override suspend fun restoreBackup(file: Uri, passPhrase: List<String>): List<String> {
        println(">>> Iniciando restoreBackup com arquivo: ${file.path}")

        val decryptedFile = decryptZipFile(file, passPhrase)
        println(">>> Arquivo ZIP descriptografado: ${decryptedFile.path}")

        val zipInput = ZipInputStream(FileInputStream(decryptedFile))
        var entry: ZipEntry? = zipInput.nextEntry
        var jsonString: String? = null

        while (entry != null) {
            println(">>> Entrada ZIP encontrada: ${entry.name}")
            if (entry.name.endsWith(".json")) {
                jsonString = zipInput.bufferedReader().use { it.readText() }
                println(">>> JSON encontrado e lido")
                break
            }
            entry = zipInput.nextEntry
        }

        if (jsonString == null) {
            throw IllegalStateException("JSON file not found in backup")
        }

        val bundle = Gson().fromJson(jsonString, BackupBundle::class.java)
        println(">>> BackupBundle carregado com sucesso")

        val expectedHashB64 = bundle.securityParams.mnemonicPhrase?.hash
        println(">>> Hash esperado (Base64): $expectedHashB64")

        val saltBytes = cryptoController.generateSaltFromMnemonic(passPhrase)
        val derivedKeyBytes = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)
        val derivedHashB64 = Base64.encodeToString(derivedKeyBytes, Base64.NO_WRAP)
        println(">>> Hash derivado (Base64): $derivedHashB64")

        if (!derivedHashB64.contentEquals(expectedHashB64)) {
            println("!!! Passphrase inválida")
            return emptyList()
        }

        println(">>> Passphrase válida, iniciando decriptação das credenciais")

        val decryptedCredentials = decryptCredentials(bundle, derivedKeyBytes)

        decryptedCredentials.forEachIndexed { index, credentialJson ->
            println(">>> Credential #$index: $credentialJson")
        }

        cachedDecryptedCredentials = decryptedCredentials.toList()

        val result = listOfNotNull(
            bundle.securityParams.biometric?.hash?.let { "Biometric" },
            bundle.securityParams.pin?.hash?.let { "Pin" },
            bundle.credentials
                .any { it.data != null }
                .takeIf { present -> present }
                ?.let { "Verifiable Credentials" }
        )

        println(">>> Opções disponíveis no backup: $result")

        return result
    }


    override suspend fun getLastBackup(): BackupLog? {
        val now = System.currentTimeMillis()
        val backupLogLast = walletBackupLogController.getClosestBackupLog(now)
        return backupLogLast?.let { log ->
            val sdf = SimpleDateFormat("yy-MM-dd", Locale.getDefault())
            val formattedDate = sdf.format(Date(log.createdAt))
            log.copy(value = formattedDate)
        }

    }

    override fun existBackupMkdir(): Boolean {
        return logsDir.exists() &&
                logsDir.isDirectory &&
                logsDir.listFiles()?.isNotEmpty() == true
    }

    /** After receiving backup options, start restore from options+
     *  delete cache data * check id in room and see documentManger
     */
    override suspend fun finalizeRestoreOptions(options: List<String>, overwriteExisting: Boolean): Boolean {
        if(cachedDecryptedCredentials.isEmpty()){
            for ((index, credential) in cachedDecryptedCredentials.withIndex()){
                val existing = walletCoreDocumentsController.getDocumentById(credential.id)

                if (existing !=null && !overwriteExisting ){
                    continue
                }

                val json = Json { encodeDefaults = true }.encodeToString(DocumentDto.serializer(), credential)

                val documentIssuanceRule = walletCoreDocumentsController.getWalletCoreConfig()
                    .documentIssuanceConfig
                    .getRuleForDocument(documentIdentifier = credential.toDocumentIdentifier())


                walletCoreDocumentsController.storeBookmark(credential.id)
            }
        }
        return false
    }

    /**
         * Get the hash and sslr used to create the pbkdf2 derivative of EncryptedSharedPreferences
         * Get Verifiable Credentials in the wallet controller
         * Encrypt credentials
         * Create backup bundle (
         * Create json
         * Encrypted AES file
    */

    private fun assembleBackupBundle(passPhrase: List<String>): String? {

        val documentsDto = walletCoreDocumentsController
            .getAllDocuments()
            .map { it.toDto() }

//        documentsDto.forEachIndexed { index, dto ->
//            println("Document ${index + 1}:")
//            println("  id: ${dto.id}")
//            println("  name: ${dto.name}")
//            println("  format: ${dto.format}")
//            println("  documentManagerId: ${dto.documentManagerId}")
//            println("  createdAt: ${dto.createdAt}")
//            val im = dto.issuerMetadata
//            if (im == null) {
//                println("  issuerMetadata: null")
//            } else {
//                println("  issuerMetadata:")
//                println("    documentConfigurationIdentifier: ${im.documentConfigurationIdentifier}")
//                println("    credentialIssuerIdentifier: ${im.credentialIssuerIdentifier}")
//
//                if (im.display.isEmpty()) {
//                    println("    display: []")
//                } else {
//                    println("    display:")
//                    im.display.forEachIndexed { i, d ->
//                        println("      - display[$i]: $d")
//                    }
//                }
//
//                if (im.claims.isNullOrEmpty()) {
//                    println("    claims: null or empty")
//                } else {
//                    println("    claims:")
//                    (im.claims as Iterable<Any?>).forEachIndexed { i, c ->
//                        println("      - claim[$i]: $c")
//                    }
//                }
//                if (im.issuerDisplay.isNullOrEmpty()) {
//                    println("    issuerDisplay: null or empty")
//                } else {
//                    println("    issuerDisplay:")
//                    (im.issuerDisplay as Iterable<Any?>).forEachIndexed { i, id ->
//                        println("      - issuerDisplay[$i]: $id")
//                    }
//                }
//            }
//
//            println()
//
//        }

        val keyBytes = passphraseAuthenticationController.retrieveKeyBytes()
        val ivMaster = passphraseAuthenticationController.retrieveIv()
        val kdf = KdfParams(alg = ALGORITHM, iters = ITERATIONS, keyLen = KEY_LENGTH)

        val securityParams = SecurityParams(
            requireBiometric = false,
            requirePin = false,
            authValidityDurationSeconds = 30,
            mnemonicPhrase = MnemonicPhrase(
                hash = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
            )
        )

        val json = Json { encodeDefaults = true }

        val credentials = documentsDto.map { doc ->
            val plaintextBytes = json.encodeToString(doc).toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(128, ivMaster)
                )
            }

            val ciphertext = cipher.doFinal(plaintextBytes)

            Credential(
                id = doc.id,
                type = doc.format::class.simpleName ?: "unknown",
                alg = "AES/GCM/NoPadding",
                iv = Base64.encodeToString(ivMaster, Base64.NO_WRAP),
                data = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
        }

        val bundleNoSig = BackupBundle(
            kdf = kdf,
            securityParams = securityParams,
            credentials = credentials,
            signature = null
        )

        val gson = Gson()
        val jsonPartial = gson.toJson(bundleNoSig)

        val hmac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(keyBytes, "HmacSHA256"))
        }.doFinal(jsonPartial.toByteArray(Charsets.UTF_8))

        bundleNoSig.signature = Signature(
            alg = "HmacSHA256",
            sig = Base64.encodeToString(hmac, Base64.NO_WRAP)
        )

       return  gson.toJson(bundleNoSig)
    }


    /**
     * 1) Generates JSON with encrypted data and HMAC
     * 2) Creates backup.json, zip, and then encrypts the zip
     * @return File “backup-{timestamp}.zip.enc” in logsDir
     */

    private fun createdFileJsonEncrypt(json: String, passPhrase: List<String>): File{
        val jsonFile = File(logsDir, LOG_FILE_NAME_JSON)
        jsonFile.writeText(json)

        val now = Instant.ofEpochMilli(System.currentTimeMillis())

        val zipFile = File(logsDir, "eudi_wallet_backup_${DateTimeFormatter.ISO_INSTANT.format(now)}.zip")

        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val entry = ZipEntry(jsonFile.name)
                zos.putNextEntry(entry)

                jsonFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        jsonFile.delete()
        return encryptZipFile(zipFile, passPhrase)

    }

    private fun encryptZipFile(zipFile: File, passPhrase: List<String>): File {
        val saltBytes = cryptoController.generateSaltFromMnemonic(passPhrase)
        val ivBytes   = cryptoController.deriveIvFromMnemonic(passPhrase)
        val keyBytes  = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

//        println("(encryptZipFile) salt (Base64): ${Base64.encodeToString(saltBytes, NO_WRAP)}")
//        println("(encryptZipFile) iv   (Base64): ${Base64.encodeToString(ivBytes,   NO_WRAP)}")
//        println("(encryptZipFile) key  (Base64): ${Base64.encodeToString(keyBytes,  NO_WRAP)}")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, ivBytes))
        }

        val encryptedZipFile = File(zipFile.parent, "${zipFile.name}.enc")
        FileInputStream(zipFile).use { fis ->
            FileOutputStream(encryptedZipFile).use { fos ->
                CipherOutputStream(fos, cipher).use { cos ->
                    fis.copyTo(cos)
                }
            }
        }
        zipFile.delete()
        return encryptedZipFile
    }

    private fun decryptZipFile(encryptedStream: Uri, passPhrase: List<String>): File {

        val saltBytes = cryptoController.generateSaltFromMnemonic(passPhrase)
        val ivBytes   = cryptoController.deriveIvFromMnemonic(passPhrase)
        val keyBytes  = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)
//
//        println("(decryptZipFile) salt (B64): ${Base64.encodeToString(saltBytes, NO_WRAP)}")
//        println("(decryptZipFile) iv   (B64): ${Base64.encodeToString(ivBytes,   NO_WRAP)}")
//        println("(decryptZipFile) key  (B64): ${Base64.encodeToString(keyBytes,  NO_WRAP)}")

        val encryptedBytes = context.contentResolver.openInputStream(encryptedStream).use { inputStream ->
            inputStream?.readBytes() ?: throw IllegalStateException("Failed to read encrypted stream")
        }

//        println("(decryptZipFile) encryptedBytes.size = ${encryptedBytes.size}")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, ivBytes))
        }


        val decryptedBytes = try {
            cipher.doFinal(encryptedBytes)
        } catch (badTag: AEADBadTagException) {
            throw SecurityException("Corrupted backup or wrong passphrase", badTag)
        }

//        println("(decryptZipFile) decryptedBytes.size = ${decryptedBytes.size}")

        val out = File.createTempFile("decrypted", ".zip", context.cacheDir)
        FileOutputStream(out).use { it.write(decryptedBytes) }
        return out
    }

    fun decryptCredentials(bundle: BackupBundle, derivedKeyBytes: ByteArray): List<DocumentDto> {
        val json = Json { ignoreUnknownKeys = true }
        val secretKey = SecretKeySpec(derivedKeyBytes, "AES")
        val decrypted = mutableListOf<DocumentDto>()

        for ((index, credential) in bundle.credentials.withIndex()) {
            try {
                val ivBytes = Base64.decode(credential.iv, Base64.NO_WRAP)
                val gcmSpec = GCMParameterSpec(128, ivBytes)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                val cipherBytes = Base64.decode(credential.data, Base64.NO_WRAP)
                val plainBytes = cipher.doFinal(cipherBytes)
                val plainText = String(plainBytes, Charsets.UTF_8)

                val documentDto = json.decodeFromString<DocumentDto>(plainText)
                decrypted.add(documentDto)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Error decoding Base64 from credential #$index: ${e.message}", e)
            } catch (e: GeneralSecurityException) {
                throw IllegalStateException("Cryptographic failure when decrypting credential#$index: ${e.message}", e)
            } catch (e: Exception) {
                throw IllegalStateException("Error processing credential #$index: ${e.message}", e)
            }
        }

        return decrypted
    }

}

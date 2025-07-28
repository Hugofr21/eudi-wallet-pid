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
import android.util.Base64
import android.util.Base64.NO_WRAP
import com.nimbusds.jose.shaded.gson.Gson
import eu.europa.ec.authenticationlogic.controller.authentication.PassphraseAuthenticationController
import eu.europa.ec.backuplogic.controller.dto.DocumentDto
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface BackupController {
    suspend fun exportBackup(passPhrase: List<String>, provider: String):File?
    suspend fun deleteBackup(identifier: String): Boolean
    suspend fun restoreBackup(file: InputStream , passPhrase: List<String>): List<String>
    suspend fun getLastBackup(): BackupLog?

    fun existBackupMkdir(): Boolean

}

/**
 * TODO: refute code duplication
 */
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
        private const val BUFFER_ARRAY = 1024
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ALGORITHM_CREDENTIAL = "AES"

    }

    private val logsDir = File(context.filesDir.absolutePath + "/backup").apply { mkdirs() }


    /**
     *  Created a new backup file.
     *  Using the Key derivation function
     *  saving credentials in encrypted files.
     *  mnemonic phrase in plain text.
     */

    override suspend fun exportBackup(passPhrase: List<String>, provider: String):File?{
        // save password in storage. this device

        passphraseAuthenticationController.setPassphrase(passPhrase)
        println("passphrase Authentication Controller set Passphrase")
        // created struct json file
        val json = assembleBackupBundle(passPhrase) ?: throw IllegalStateException("Backup bundle is null")
        println("Json $json")

        // read json file and created zip
        val zipFile = createdFileJsonEncrypt(json, passPhrase)

        // created log backup
        val backupLog = BackupLog(
            identifier = HashUtils.sha256((provider + System.currentTimeMillis()).toByteArray()),
            provider = provider,
            value =LOG_FILE_NAME_JSON ,
            createdAt = System.currentTimeMillis()
        )
        walletBackupLogController.storeBackupLog(backupLog)

        return zipFile
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
    override suspend fun restoreBackup(file: InputStream, passPhrase: List<String>): List<String> {
        println(">>> Iniciando restoreBackup com arquivo: ${file.available()}")

        val decryptedFile = decryptZipFile(file, passPhrase)
        println(">>> Arquivo ZIP descriptografado: ${decryptedFile?.path}")

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
            println("!!! Nenhum arquivo .json encontrado no ZIP")
            throw IllegalStateException("JSON file not found in backup")
        }

        val bundle = Gson().fromJson(jsonString, BackupBundle::class.java)
        println(">>> BackupBundle carregado com sucesso")

        val saltB64 = bundle.securityParams.mnemonicPhrase?.salt
        val expectedHashB64 = bundle.securityParams.mnemonicPhrase?.hash
        println(">>> Salt recebido (Base64): $saltB64")
        println(">>> Hash esperado (Base64): $expectedHashB64")

        val saltBytes = Base64.decode(saltB64, Base64.NO_WRAP)
        val derivedKeyBytes = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)
        val derivedHashB64 = Base64.encodeToString(derivedKeyBytes, Base64.NO_WRAP)
        println(">>> Hash derivado (Base64): $derivedHashB64")

        if (!derivedHashB64.contentEquals(expectedHashB64)) {
            println("!!! Passphrase inválida")
            throw SecurityException("Passphrase invalid!!!!")
        }

        println(">>> Passphrase válida, iniciando decriptação das credenciais")

        val decryptedCredentials = decryptCredentials(bundle, derivedKeyBytes)
        decryptedCredentials.forEachIndexed { index, credentialJson ->
            val documentDto = Gson().fromJson(credentialJson, DocumentDto::class.java)
            println(">>> Credential #$index: $documentDto")
        }

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
//        println("Loaded ${documentsDto.size} documents")

        val keyBytes = passphraseAuthenticationController.retrieveKeyBytes()
//        println(
//            " keyBytes (${keyBytes.size} bytes): ${
//                Base64.encodeToString(
//                    keyBytes,
//                    Base64.NO_WRAP
//                )
//            }"
//        )
        val ivMaster = passphraseAuthenticationController.retrieveIv()
//        println("Master IV (Base64): ${Base64.encodeToString(ivMaster, Base64.NO_WRAP)}")

        val kdf = KdfParams(alg = ALGORITHM, iters = ITERATIONS, keyLen = KEY_LENGTH)
//        println("KDF params: $kdf")
        val securityParams = SecurityParams(
            requireBiometric = false,
            requirePin = false,
            authValidityDurationSeconds = 30,
            mnemonicPhrase = MnemonicPhrase(
                hash = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
            )
        )
//        println("Security params: $securityParams")

        val credentials = documentsDto.map { doc ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(128, ivMaster)
                )
            }
//            println("Cipher initialized: $cipher")

            val plaintext = doc.toString().toByteArray(Charsets.UTF_8)
//            println(
//                "Plaintext (${plaintext.size} bytes): ${
//                    String(
//                        plaintext,
//                        Charsets.UTF_8
//                    )
//                }"
//            )

            val ciphertext = cipher.doFinal(plaintext)
            val cipherTextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
//            println("Ciphertext (Base64, ${ciphertext.size} bytes): $cipherTextB64")

            Credential(
                id = doc.id,
                type = doc.format::class.simpleName ?: "unknown",
                alg = "AES/GCM/NoPadding",
                iv = Base64.encodeToString(ivMaster, Base64.NO_WRAP),
                data = cipherTextB64
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
//        println("jsonPartial: $jsonPartial")

        val hmac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(keyBytes, "HmacSHA256"))
        }.doFinal(jsonPartial.toByteArray(Charsets.UTF_8))
//        println("HMAC raw: ${hmac.contentToString()}")

        bundleNoSig.signature = Signature(
            alg = "HmacSHA256",
            sig = Base64.encodeToString(hmac, Base64.NO_WRAP)
        )
//        println("Attached signature: ${bundleNoSig.signature}")

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
            ?: throw IllegalStateException("Failed to encrypt zip file")

    }

    private fun encryptZipFile(zipFile: File, passPhrase: List<String>): File {
        // 1) Deriva salt, IV e chave a partir da frase mnemônica
        val saltBytes = cryptoController.generateSaltFromMnemonic(passPhrase)
        val ivBytes   = cryptoController.deriveIvFromMnemonic(passPhrase)
        val keyBytes  = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

        // (opcionais) logs para debug – você verá exatamente os mesmos valores no decrypt
        println("(encryptZipFile) salt (Base64): ${Base64.encodeToString(saltBytes, NO_WRAP)}")
        println("(encryptZipFile) iv   (Base64): ${Base64.encodeToString(ivBytes,   NO_WRAP)}")
        println("(encryptZipFile) key  (Base64): ${Base64.encodeToString(keyBytes,  NO_WRAP)}")

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

    private fun decryptZipFile(encryptedStream: InputStream, passPhrase: List<String>): File {

        val saltBytes = cryptoController.generateSaltFromMnemonic(passPhrase)
        val ivBytes   = cryptoController.deriveIvFromMnemonic(passPhrase)
        val keyBytes  = cryptoController.deriveKeyFromMnemonic(passPhrase, saltBytes)

        println("(decryptZipFile) salt (B64): ${Base64.encodeToString(saltBytes, NO_WRAP)}")
        println("(decryptZipFile) iv   (B64): ${Base64.encodeToString(ivBytes,   NO_WRAP)}")
        println("(decryptZipFile) key  (B64): ${Base64.encodeToString(keyBytes,  NO_WRAP)}")

        val encryptedBytes = encryptedStream.readBytes()
        println("(decryptZipFile) encryptedBytes.size = ${encryptedBytes.size}")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, ivBytes))
        }


        val decryptedBytes = try {
            cipher.doFinal(encryptedBytes)
        } catch (badTag: AEADBadTagException) {
            println("BackupCtl AEADBadTagException ao decrypt: ${badTag.message}")
            throw SecurityException("Backup corrompido ou passphrase errada", badTag)
        }

        println("(decryptZipFile) decryptedBytes.size = ${decryptedBytes.size}")

        val out = File.createTempFile("decrypted", ".zip", context.cacheDir)
        FileOutputStream(out).use { it.write(decryptedBytes) }
        println("(decryptZipFile) saída em: ${out.path}")
        return out
    }

    fun decryptCredentials(bundle: BackupBundle, derivedKeyBytes: ByteArray): List<String> {
        val secretKey: SecretKey = SecretKeySpec(derivedKeyBytes, "AES")
        val decryptedCredentials = mutableListOf<String>()

        for (credential in bundle.credentials) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val ivBytes = Base64.decode(credential.iv, Base64.NO_WRAP)
            val gcmSpec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val cipherBytes = Base64.decode(
                credential.data ?: throw IllegalArgumentException("Missing credential data"),
                Base64.NO_WRAP
            )
            val plainBytes = cipher.doFinal(cipherBytes)
            val plainText = String(plainBytes, Charsets.UTF_8)
            decryptedCredentials.add(plainText)
        }

        return decryptedCredentials
    }

}

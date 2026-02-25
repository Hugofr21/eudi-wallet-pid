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

package eu.europa.ec.authenticationlogic.controller.authentication

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.model.biometric.BiometricAuthentication
import eu.europa.ec.authenticationlogic.model.biometric.BiometricCrypto
import eu.europa.ec.businesslogic.controller.crypto.CryptoController
import eu.europa.ec.businesslogic.extension.decodeFromPemBase64String
import eu.europa.ec.businesslogic.extension.encodeToPemBase64String
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import kotlin.coroutines.resume


interface BiometricAuthenticationController {
    fun deviceSupportsBiometrics(listener: (BiometricsAvailability) -> Unit)
    fun authenticate(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit,
        retryCount: Int = 0
    )

    suspend fun authenticate(
        activity: FragmentActivity,
        biometryCrypto: BiometricCrypto,
        promptInfo: BiometricPrompt.PromptInfo,
        notifyOnAuthenticationFailure: Boolean,
    ): BiometricPromptData

    fun launchBiometricSystemScreen()
}

class BiometricAuthenticationControllerImpl(
    private val resourceProvider: ResourceProvider,
    private val cryptoController: CryptoController,
    private val biometryStorageController: BiometryStorageController,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : BiometricAuthenticationController {

    private companion object {
        const val MAX_RETRIES = 3
        const val VALIDATION_TOKEN = "VALID_BIOMETRIC_PAYLOAD"
    }

    private fun isRecoverableError(errorCode: Int): Boolean {
        return errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                errorCode == BiometricPrompt.ERROR_UNABLE_TO_PROCESS ||
                errorCode == BiometricPrompt.ERROR_TIMEOUT
    }

    override fun deviceSupportsBiometrics(listener: (BiometricsAvailability) -> Unit) {
        val biometricManager = BiometricManager.from(resourceProvider.provideContext())
        val authenticators = BIOMETRIC_STRONG
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> listener(BiometricsAvailability.CanAuthenticate)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> listener(BiometricsAvailability.NonEnrolled)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> listener(
                BiometricsAvailability.Failure(
                    resourceProvider.getString(R.string.biometric_no_hardware)
                )
            )

            else -> listener(BiometricsAvailability.Failure("Biometric status unknown or unavailable"))
        }
    }

    override fun authenticate(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit,
        retryCount: Int
    ) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            listener(BiometricsAuthenticate.Failed("Invalid context: Must be FragmentActivity"))
            return
        }

        activity.lifecycleScope.launch {
            try {
                val (storedBiometry, cipher) = retrieveCrypto()

                if (cipher == null) {
                    listener(
                        BiometricsAuthenticate.Failed("Crypto initialization failed. Keystore key might be invalidated.")
                    )
                    return@launch
                }

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.biometric_prompt_title))
                    .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
                    .setNegativeButtonText(activity.getString(R.string.generic_cancel))
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build()

                val authResult = authenticateInternal(
                    activity = activity,
                    cryptoObject = BiometricPrompt.CryptoObject(cipher),
                    promptInfo = promptInfo,
                    notifyOnAuthenticationFailure = notifyOnAuthenticationFailure
                )


                if (authResult.authenticationResult != null) {
                    val verificationState =
                        processCryptoResult(authResult.authenticationResult, storedBiometry)
                    listener(verificationState)
                } else {

                    handleBiometricFailure(
                        authResult,
                        retryCount,
                        context,
                        notifyOnAuthenticationFailure,
                        listener
                    )
                }

            } catch (e: Exception) {

                biometryStorageController.setBiometricAuthentication(null)
                listener(BiometricsAuthenticate.Failed("Security Error: ${e.message}"))
            }
        }
    }


    private suspend fun authenticateInternal(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject,
        promptInfo: BiometricPrompt.PromptInfo,
        notifyOnAuthenticationFailure: Boolean
    ): BiometricPromptData = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resume(BiometricPromptData(null, errorCode, errString))
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(BiometricPromptData(result))
                    }
                }

                override fun onAuthenticationFailed() {
                    if (continuation.isActive && notifyOnAuthenticationFailure) {
                        continuation.resume(BiometricPromptData(null))
                    }
                }
            }
        )
        prompt.authenticate(promptInfo, cryptoObject)
    }

    private suspend fun processCryptoResult(
        result: BiometricPrompt.AuthenticationResult,
        storedBiometry: BiometricAuthentication?
    ): BiometricsAuthenticate = withContext(dispatcher) {
        val cipher = result.cryptoObject?.cipher
            ?: return@withContext BiometricsAuthenticate.Failed("Cipher is null after auth")

        try {
            if (storedBiometry == null) {
                val encryptedBytes = cryptoController.encryptDecrypt(
                    cipher = cipher,
                    byteArray = VALIDATION_TOKEN.toByteArray(StandardCharsets.UTF_8)
                )
                val newBiometry = BiometricAuthentication(
                    encryptedPayload = encryptedBytes.encodeToPemBase64String().orEmpty(),
                    ivString = cipher.iv.encodeToPemBase64String().orEmpty()
                )
                biometryStorageController.setBiometricAuthentication(newBiometry)
                BiometricsAuthenticate.Success
            } else {
                val decryptedBytes = cryptoController.encryptDecrypt(
                    cipher = cipher,
                    byteArray = storedBiometry.encryptedPayload.decodeFromPemBase64String()
                        ?: ByteArray(0)
                )

                val decryptedString = String(decryptedBytes, StandardCharsets.UTF_8)

                if (decryptedString == VALIDATION_TOKEN) {
                    BiometricsAuthenticate.Success
                } else {
                    BiometricsAuthenticate.Failed("Integrity check failed: Decrypted data mismatch.")
                }
            }
        } catch (e: Exception) {
            BiometricsAuthenticate.Failed("Crypto Operation Failed: ${e.message}")
        }
    }

    private fun handleBiometricFailure(
        result: BiometricPromptData,
        retryCount: Int,
        context: Context,
        notifyFailure: Boolean,
        listener: (BiometricsAuthenticate) -> Unit
    ) {
        when {
            result.errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    result.errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                listener(BiometricsAuthenticate.Cancelled)
            }

            isRecoverableError(result.errorCode) && retryCount < MAX_RETRIES -> {
                authenticate(context, notifyFailure, listener, retryCount + 1)
            }

            else -> {
                listener(BiometricsAuthenticate.Failed(result.errorString.toString()))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun launchBiometricSystemScreen() {
        val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG)
        }
        enrollIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        resourceProvider.provideContext().startActivity(enrollIntent)
    }

    override suspend fun authenticate(
        activity: FragmentActivity,
        biometryCrypto: BiometricCrypto,
        promptInfo: BiometricPrompt.PromptInfo,
        notifyOnAuthenticationFailure: Boolean
    ): BiometricPromptData = suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resume(
                            BiometricPromptData(null, errorCode, errString)
                        )
                    }
                }

                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    if (continuation.isActive) {
                        continuation.resume(BiometricPromptData(result))
                    }
                }

                override fun onAuthenticationFailed() {
                    if (continuation.isActive && notifyOnAuthenticationFailure) {
                        continuation.resume(BiometricPromptData(null))
                    }
                }
            }
        )
        biometryCrypto.cryptoObject?.let {
            prompt.authenticate(
                promptInfo,
                it
            )
        } ?: prompt.authenticate(promptInfo)
    }

    private suspend fun retrieveCrypto(): Pair<BiometricAuthentication?, Cipher?> =
        withContext(dispatcher) {
            val biometricData = biometryStorageController.getBiometricAuthentication()
            val cipher = cryptoController.getCipher(
                encrypt = biometricData == null,
                ivBytes = biometricData?.ivString?.decodeFromPemBase64String() ?: ByteArray(0)
            )
            Pair(biometricData, cipher)
        }

}

sealed class BiometricsAuthenticate {
    data object Success : BiometricsAuthenticate()
    data class Failed(val errorMessage: String) : BiometricsAuthenticate()
    data object Cancelled : BiometricsAuthenticate()
}

sealed class BiometricsAvailability {
    data object CanAuthenticate : BiometricsAvailability()
    data object NonEnrolled : BiometricsAvailability()
    data class Failure(val errorMessage: String) : BiometricsAvailability()
}

data class BiometricPromptData(
    val authenticationResult: AuthenticationResult?,
    val errorCode: Int = -1,
    val errorString: CharSequence = "",
) {
    val hasError: Boolean get() = errorCode != -1
}
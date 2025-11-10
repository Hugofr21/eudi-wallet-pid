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
    }

    private fun isRecoverableError(errorCode: Int): Boolean {
        return errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                errorCode == BiometricPrompt.ERROR_UNABLE_TO_PROCESS ||
                errorCode == BiometricPrompt.ERROR_TIMEOUT
    }

    override fun deviceSupportsBiometrics(listener: (BiometricsAvailability) -> Unit) {
        val biometricManager = BiometricManager.from(resourceProvider.provideContext())
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> listener.invoke(BiometricsAvailability.CanAuthenticate)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> listener.invoke(BiometricsAvailability.NonEnrolled)
            else -> {

                when (biometricManager.canAuthenticate(BIOMETRIC_WEAK)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> listener.invoke(BiometricsAvailability.CanAuthenticate)
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> listener.invoke(BiometricsAvailability.NonEnrolled)
                    else -> listener.invoke(BiometricsAvailability.Failure(resourceProvider.getString(R.string.biometric_no_hardware)))
                }
            }
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
            listener.invoke(BiometricsAuthenticate.Failed("Invalid context"))
            return
        }

        activity.lifecycleScope.launch {
            val (biometricData, cipher) = retrieveCrypto()
            if (cipher == null) {
                listener.invoke(BiometricsAuthenticate.Failed(context.getString(R.string.generic_error_description)))
                return@launch
            }

            val data = authenticate(
                activity = activity,
                biometryCrypto = BiometricCrypto(BiometricPrompt.CryptoObject(cipher)),
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.biometric_prompt_title))
                    .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
                    .setNegativeButtonText(activity.getString(R.string.generic_cancel))
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build(),
                notifyOnAuthenticationFailure = notifyOnAuthenticationFailure
            )

            when {
                data.authenticationResult != null -> {
                    val state = verifyCrypto(context, data.authenticationResult, biometricData)
                    listener.invoke(state)
                }
                isRecoverableError(data.errorCode) && retryCount < MAX_RETRIES -> {
                    listener.invoke(BiometricsAuthenticate.Failed("Too many attempts. Please use PIN or password."))
                    authenticate(context, notifyOnAuthenticationFailure, listener, retryCount + 1)
                }
                data.errorCode == BiometricPrompt.ERROR_USER_CANCELED || data.errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                    listener.invoke(BiometricsAuthenticate.Cancelled)
                }
                else -> {
                    listener.invoke(BiometricsAuthenticate.Failed(data.errorString.toString()))
                }
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
//            println("BiometricAuthenticationControllerImpl.retrieveCrypto: biometricData=$biometricData")
            val cipher = cryptoController.getCipher(
                encrypt = biometricData == null,
                ivBytes = biometricData?.ivString?.decodeFromPemBase64String() ?: ByteArray(0)
            )
//            println("BiometricAuthenticationControllerImpl.retrieveCrypto: cipher=$cipher")
            Pair(biometricData, cipher)
        }

    private suspend fun verifyCrypto(
        context: Context,
        result: AuthenticationResult?,
        biometricAuthentication: BiometricAuthentication?
    ): BiometricsAuthenticate = withContext(dispatcher) {
        result?.cryptoObject?.cipher?.let {
            if (biometricAuthentication == null) {
                val randomString = cryptoController.generateCodeVerifier()
                biometryStorageController.setBiometricAuthentication(
                    BiometricAuthentication(
                        randomString = randomString,
                        encryptedString = cryptoController.encryptDecrypt(
                            cipher = it,
                            byteArray = randomString.toByteArray(StandardCharsets.UTF_8)
                        ).encodeToPemBase64String().orEmpty(),
                        ivString = it.iv.encodeToPemBase64String().orEmpty()
                    )
                )
                BiometricsAuthenticate.Success
            } else {
                if (biometricAuthentication.randomString
                        .toByteArray(StandardCharsets.UTF_8)
                        .contentEquals(
                            cryptoController.encryptDecrypt(
                                cipher = it,
                                byteArray = biometricAuthentication.encryptedString
                                    .decodeFromPemBase64String() ?: ByteArray(0)
                            )
                        )
                ) {
                    BiometricsAuthenticate.Success
                } else {
                    BiometricsAuthenticate.Failed(context.getString(R.string.generic_error_description))
                }
            }
        } ?: BiometricsAuthenticate.Failed(context.getString(R.string.generic_error_description))
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
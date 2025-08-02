package eu.europa.ec.verifierfeature.di

import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.corelogic.controller.WalletCoreTransactionLogController
import eu.europa.ec.corelogic.controller.WalletCoreTransactionLogControllerImpl
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofController
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofControllerImpl
import eu.europa.ec.verifierfeature.controller.VerifierApiSwaggerController
import eu.europa.ec.verifierfeature.controller.VerifierApiSwaggerControllerImpl
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single


@Module
@ComponentScan("eu.europa.ec.verifierfeature")
class FeatureVerifierModule


@Single
fun provideVerifierApiSwaggerController(
    okHttpClient: OkHttpClient
): VerifierApiSwaggerController = VerifierApiSwaggerControllerImpl(
    okHttpClient = okHttpClient
)

@Single
fun provideVerifierAgeProofController(
    api: VerifierApiSwaggerController,
    uuidProvider: UuidProvider
): VerifierAgeProofController = VerifierAgeProofControllerImpl(
    api = api,
    uuidProvider = uuidProvider

)


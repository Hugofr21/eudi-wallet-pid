package eu.europa.ec.verifierfeature.di

import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.di.PRESENTATION_SCOPE_ID
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofController
import eu.europa.ec.verifierfeature.controller.VerifierAgeProofControllerImpl
import eu.europa.ec.verifierfeature.controller.VerifierApiSwaggerController
import eu.europa.ec.verifierfeature.controller.VerifierApiSwaggerControllerImpl
import eu.europa.ec.verifierfeature.interactor.AgeProofInteractor
import eu.europa.ec.verifierfeature.interactor.AgeProofInteractorImpl
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
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
): VerifierAgeProofController = VerifierAgeProofControllerImpl(
    api = api,
)

@Factory
fun providerAgeProofInteractor(
    verifierAgeProofController: VerifierAgeProofController,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    documentDetailsInteractor: DocumentDetailsInteractor,
    deviceAuthenticationInteractor: DeviceAuthenticationInteractor

): AgeProofInteractor = AgeProofInteractorImpl(
    verifierAgeProofController = verifierAgeProofController,
    walletCoreDocumentsController =  walletCoreDocumentsController,
    resourceProvider = resourceProvider,
    documentDetailsInteractor = documentDetailsInteractor,
    deviceAuthenticationInteractor = deviceAuthenticationInteractor
)


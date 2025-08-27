package eu.europa.ec.verifierfeature.di


import eu.europa.ec.businesslogic.provider.UuidProvider
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.dashboardfeature.interactor.DocumentDetailsInteractor
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.verifierfeature.controller.verifier.VerifierApiSwaggerController
import eu.europa.ec.verifierfeature.controller.verifier.VerifierApiSwaggerControllerImpl
import eu.europa.ec.verifierfeature.controller.verifier.VerifierEUDIController
import eu.europa.ec.verifierfeature.controller.verifier.VerifierEUDIControllerImpl
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentController
import eu.europa.ec.verifierfeature.controller.document.EventPresentationDocumentControllerImpl
import eu.europa.ec.verifierfeature.controller.verifier.VerifierController
import eu.europa.ec.verifierfeature.controller.verifier.VerifierControllerImpl
import eu.europa.ec.verifierfeature.interactor.PresentationLoadingVerifierInteractor
import eu.europa.ec.verifierfeature.interactor.PresentationLoadingVerifierInteractorImpl
import eu.europa.ec.verifierfeature.interactor.PresentationRequestVerifierInteractor
import eu.europa.ec.verifierfeature.interactor.PresentationRequestVerifierInteractorImpl
import eu.europa.ec.verifierfeature.interactor.PresentationSuccessVerifierInteractor
import eu.europa.ec.verifierfeature.interactor.PresentationSuccessVerifierInteractorImpl
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
    uuidProvider: UuidProvider
): VerifierController = VerifierControllerImpl(
    api = api,
    uuidProvider = uuidProvider
)




@Single
fun provideVerifierEUDIController(
): VerifierEUDIController = VerifierEUDIControllerImpl()


@Single
fun providerEventPresentationDocumentController(
    verifierProofController: VerifierController,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    documentDetailsInteractor: DocumentDetailsInteractor,
    deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    eudiWallet: EudiWallet,
): EventPresentationDocumentController = EventPresentationDocumentControllerImpl(
    api = verifierProofController,
    resourceProvider = resourceProvider,
    walletCoreDocumentsController = walletCoreDocumentsController,
    documentDetailsInteractor = documentDetailsInteractor,
    eudiWallet = eudiWallet

)

@Factory
fun providerPresentationRequestVerifierInteractor(
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider,
    eventPresentationDocumentController: EventPresentationDocumentController,
    walletCoreDocumentsController: WalletCoreDocumentsController
): PresentationRequestVerifierInteractor = PresentationRequestVerifierInteractorImpl(
    resourceProvider = resourceProvider,
    uuidProvider  = uuidProvider,
    eventPresentationDocumentController =  eventPresentationDocumentController,
    walletCoreDocumentsController = walletCoreDocumentsController

)


@Factory
fun providerPresentationLoadingVerifierInteractor(
    eventPresentationDocumentController: EventPresentationDocumentController,
    deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
): PresentationLoadingVerifierInteractor = PresentationLoadingVerifierInteractorImpl(
    eventPresentationDocumentController = eventPresentationDocumentController,
    deviceAuthenticationInteractor = deviceAuthenticationInteractor,

)

@Factory
fun providerPresentationSuccessVerifierInteractor(
    eventPresentationDocumentController: EventPresentationDocumentController,
    walletCoreDocumentsController: WalletCoreDocumentsController,
    resourceProvider: ResourceProvider,
    uuidProvider: UuidProvider
): PresentationSuccessVerifierInteractor = PresentationSuccessVerifierInteractorImpl(
    eventPresentationDocumentController = eventPresentationDocumentController,
    walletCoreDocumentsController = walletCoreDocumentsController,
    resourceProvider = resourceProvider,
    uuidProvider = uuidProvider
)




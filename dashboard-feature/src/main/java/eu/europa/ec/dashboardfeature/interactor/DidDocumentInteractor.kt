package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.corelogic.controller.WalletDidDocumentController
import eu.europa.ec.resourceslogic.provider.ResourceProvider

interface DidDocumentInteractor {
    fun getDidDocumentForSharing(): String
}

class DidDocumentInteractorImpl(
    private val walletDidDocumentController : WalletDidDocumentController,
    private val resourceProvider: ResourceProvider,
) : DidDocumentInteractor {
    override fun getDidDocumentForSharing(): String {
        TODO("Not yet implemented")
    }


}

package eu.europa.ec.dashboardfeature.interactor

import android.content.Context
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.config.WalletCoreConfig
import eu.europa.ec.corelogic.controller.TransferEventWifiAwarePartialState
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.controller.WalletLiveDataController
import eu.europa.ec.corelogic.controller.wifi.WifiAwareConfig
import eu.europa.ec.corelogic.service.WifiAwareService
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.text.orEmpty

sealed class WifiAwareInteractorPartialState {
    data class Success(
        val userFirstName: String,
    ) : WifiAwareInteractorPartialState()

    data class Failure(
        val error: String
    ) : WifiAwareInteractorPartialState()
}

interface WifiAwareInteractor {
    fun isWifiAvailable(): Boolean
    fun scanPeers()

    fun startSubscription()

    fun stopScan()

}


class WifiAwareInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletLiveDataController: WalletLiveDataController,
    private val context: Context
) : WifiAwareInteractor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var discoveryJob: Job? = null


    override fun isWifiAvailable(): Boolean =
        walletLiveDataController.isWifiAwareAvailable()

    override fun scanPeers() {
        val intent = WifiAwareService.createStartIntent(context)
        context.startService(intent)
    }


    override fun startSubscription() {
        val intent = WifiAwareService.createSubscribeIntent(context)
        context.startService(intent)
        scope.launch {
            getResponse().collect { event ->
                handleTransferEvent(event)
            }
        }
    }

    suspend fun getResponse(): Flow<TransferEventWifiAwarePartialState> {
        return walletLiveDataController.events
            .filterNotNull() 
            .map { response ->
                println("Received response: $response")
                response
            }
            .catch { exception ->
                println("Exception caught in flow: ${exception.localizedMessage}")
                emit(TransferEventWifiAwarePartialState.Error(
                    message = exception.localizedMessage ?: "Unknown error",
                    code = -1
                ))
            }
    }

    private fun handleTransferEvent(event: TransferEventWifiAwarePartialState) {
        when (event) {
            is TransferEventWifiAwarePartialState.Error -> {
                println("Error received: ${event.message}, code: ${event.code}")

            }
            is TransferEventWifiAwarePartialState.Disconnected -> {
                println("Disconnected from verifier")

            }
            else -> {
                println("Unhandled event type: $event")

            }
        }
    }


    override fun stopScan() {
        println("Stop scan!!!!")
        walletLiveDataController.stopWifiAware()
        val intent = WifiAwareService.createStopIntent(context)
        context.startService(intent)
    }


}
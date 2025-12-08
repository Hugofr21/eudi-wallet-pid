package eu.europa.ec.corelogic.controller


import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.corelogic.model.ProviderCategory
import eu.europa.ec.corelogic.model.TslLocationInfo
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob

interface WalletLotlController{
    suspend fun getAllQtsp(): List<TslLocationInfo>
    suspend fun getQtspByCountry(country: String): TslLocationInfo

    fun init()

}

/**
 *  Verifiable Issuers and Verifiers v0.2
 *  Electronic Signatures and Trust Infrastructures (ESI);
 *  Trusted Lists
 *  ETSI TS 119 612 V2.3.1 (2024-11): https://ec.europa.eu/tools/lotl/eu-lotl.xml
    - Wallet Issuers
    - PID Issuers
    - (Q)EAA Issuers
    - Relying Parties (via Access Certificate Authorities)
 */

class WalletLotlControllerImpl(
    private val client: HttpClient,
    private val resourceProvider: ResourceProvider,
):WalletLotlController  {
    companion object {
        private const val LOG_FILE_PATTERN = "eudi-android-wallet-trust-list%g.json"
        private const val FILE_SIZE_LIMIT = 5 * 1024 * 1024
        private const val FILE_COUNT_LIMIT = 10
    }

    private val logsDir = File(resourceProvider.provideContext().filesDir.absolutePath + "/trust_list")
    private val trustListJsonFile = File(logsDir, LOG_FILE_PATTERN)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedTslList: List<TslLocationInfo>? = null
    private var cachedTslMap: Map<String, TslLocationInfo>? = null
    private val mutex = Mutex()

    override  fun init(){
        logsDir.mkdirs()
        scope.launch {
            fetchAndSaveTslList()
        }
    }

    private suspend fun fetchAndSaveTslList() {
        val tslList = fetchAllQtsp()
        val json = Json.encodeToString(mapOf("trustList" to tslList))
        val jsonBytes = json.toByteArray()
        if (jsonBytes.size <= FILE_SIZE_LIMIT) {
            val file = getRotatedFile()
            file.writeBytes(jsonBytes)
            Timber.tag("TrustList").d("Trust list JSON saved to ${file.name}")
        } else {
            Timber.tag("TrustList").w("Trust list JSON exceeds size limit (${jsonBytes.size} bytes). Skipping save.")
        }
    }

    private fun verifySizeJson(jsonBytes: ByteArray) {
        if (jsonBytes.size > FILE_SIZE_LIMIT) {
            Timber.tag("TrustList").w("Trust list JSON exceeds size limit (${jsonBytes.size} bytes). Skipping save.")
            return
        }
    }

    fun getRotatedFile(): File {
        println("")
        val existingFiles = (0 until FILE_COUNT_LIMIT).map { i ->
            File(logsDir, LOG_FILE_PATTERN.replace("%g", i.toString()))
        }.filter { it.exists() }
        println("existingFiles $existingFiles")

        val nextIndex = if (existingFiles.size < FILE_COUNT_LIMIT) {
            println("nextIndex ${existingFiles.size}")
            existingFiles.size
        } else {
            val oldest = existingFiles.minByOrNull { it.lastModified() }?.name
            println("oldest ${oldest}")
            LOG_FILE_PATTERN.replace("%g", "(\\d+)").toRegex().find(oldest ?: "")?.groupValues?.get(1)?.toInt() ?: 0
        }
        return File(logsDir, LOG_FILE_PATTERN.replace("%g", nextIndex.toString()))
    }

    suspend fun fetchAllQtsp(): List<TslLocationInfo> {
        val lotlUrl = "https://ec.europa.eu/tools/lotl/eu-lotl.xml"
        val lotlStream = httpGetAsStream(lotlUrl)
        return parseLotlLocationsWithCategory(lotlStream)
    }

    private suspend fun httpGetAsStream(url: String): InputStream {
        try {
            val bytes: ByteArray = client.get(url).bodyAsBytes()
            return ByteArrayInputStream(bytes)
        } catch (e: Exception) {
            throw IOException("HTTP error when loading $url", e)
        }
    }

    private fun parseLotlLocationsWithCategory(stream: InputStream): List<TslLocationInfo> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(stream, null)
        }

        val list = mutableListOf<TslLocationInfo>()
        var event = parser.eventType
        var currCountry: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "SchemeInformation" -> currCountry = null
                    "SchemeTerritory" -> currCountry = parser.nextText().trim()
                    "TSLLocation" -> {
                        val url = parser.nextText().trim()
                        currCountry?.let { country ->
                            list += TslLocationInfo(
                                country = country,
                                tslUrl = url,
                                category = ProviderCategory.REMOTE_SIGNATURE
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun mapCategory(uri: String): ProviderCategory = when (uri) {
        "http://uri.etsi.org/TrstSvc/Svctype/QCertESig"   -> ProviderCategory.SIGNATURE
        "http://uri.etsi.org/TrstSvc/Svctype/QCertESeal"  -> ProviderCategory.SEAL
        "http://uri.etsi.org/TrstSvc/Svctype/QWAC"        -> ProviderCategory.WEBSITE_AUTH
        "http://uri.etsi.org/TrstSvc/Svctype/QESigRemote" -> ProviderCategory.REMOTE_SIGNATURE
        "http://uri.etsi.org/TrstSvc/Svctype/WalletIssuer"-> ProviderCategory.WALLETS
        "http://uri.etsi.org/TrstSvc/Svctype/PIDIssuer"   -> ProviderCategory.PID
        "http://uri.etsi.org/TrstSvc/Svctype/QEAAIssuer"  -> ProviderCategory.EAA
        "http://uri.etsi.org/TrstSvc/Svctype/RelyingParty"-> ProviderCategory.RELYING_PARTY
        else                                               -> ProviderCategory.OTHER
    }
    private suspend fun ensureCachePopulated() {
        mutex.withLock {
            if (cachedTslList == null) {
                val list = fetchAllQtsp()
                cachedTslList = list
                cachedTslMap = list.associateBy { it.country }
            }
        }
    }
    override suspend fun getAllQtsp(): List<TslLocationInfo> {
        ensureCachePopulated()
        return cachedTslList!!
    }

    override suspend fun getQtspByCountry(country: String): TslLocationInfo {
        ensureCachePopulated()
        return cachedTslMap!![country] ?: throw IllegalArgumentException("Country not found: $country")
    }

}
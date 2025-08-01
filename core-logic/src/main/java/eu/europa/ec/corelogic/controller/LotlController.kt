package eu.europa.ec.corelogic.controller


import eu.europa.ec.corelogic.model.ProviderCategory
import eu.europa.ec.corelogic.model.QtspInfo
import eu.europa.ec.corelogic.model.TslLocationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

interface LotlController{
    suspend  fun  fetchAllQtsp(): List<TslLocationInfo>
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

class LotlControllerImpl(
    private val client: OkHttpClient,
):LotlController  {

    override suspend fun fetchAllQtsp(): List<TslLocationInfo> = withContext(Dispatchers.IO) {
        val lotlUrl = "https://ec.europa.eu/tools/lotl/eu-lotl.xml"
        val lotlStream = httpGetAsStream(lotlUrl)

        val tslList = parseLotlLocationsWithCategory(lotlStream)

        println("Trust List Providers:")
        tslList.forEach {
            println("${it.country} - ${it.tslUrl} - ${it.category}")
        }

        tslList
    }


    private fun httpGetAsStream(url: String): InputStream {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} when loading $url")
            }
            val bytes = resp.body!!.bytes()
            return ByteArrayInputStream(bytes)
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

}
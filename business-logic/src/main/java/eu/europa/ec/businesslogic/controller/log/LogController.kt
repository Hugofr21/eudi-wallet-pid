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

package eu.europa.ec.businesslogic.controller.log

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.squareup.moshi.JsonAdapter
import eu.europa.ec.businesslogic.config.ConfigLogic
import fr.bipi.treessence.file.FileLoggerTree
import timber.log.Timber
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okio.buffer
import okio.sink
import java.security.MessageDigest

interface LogController {
    fun d(tag: String, message: () -> String)
    fun d(message: () -> String)
    fun e(tag: String, message: () -> String)
    fun e(tag: String, exception: Throwable)
    fun e(message: () -> String)
    fun e(exception: Throwable)
    fun w(tag: String, message: () -> String)
    fun w(message: () -> String)
    fun i(tag: String, message: () -> String)
    fun i(message: () -> String)

    fun retrieveLogFileUris(): List<Uri>
    fun getLogStatistics(): JsonLogEntry
    fun clearLogs()
}


data class JsonLogEntry (
    val timestamp:   Long,
    val level:       String,
    val tag:         String,
    val message:     String,
    val correlationId: String? = null,
    val component:   String? = null,
    val method:      String? = null,
    val url:         String? = null,
    val status:      Int?    = null,
    val durationMs:  Long?   = null,
    val parentHash:  String? = null,
    val hash:        String? = null
)

class LogControllerImpl(
    private val context: Context,
    configLogic: ConfigLogic,
) : LogController {

    companion object {
        private const val LOG_FILE_PATTERN = "eudi-android-wallet-logs%g.bin"
        private const val FILE_SIZE_LIMIT = 5 * 1024 * 1024
        private const val FILE_COUNT_LIMIT = 10
    }

    private val logsDir = File(context.filesDir.absolutePath + "/logs")
    private val tag: String = "EUDI Wallet ${configLogic.appFlavor}-${configLogic.appBuildType}"

    private val queue = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val jsonAdapter: JsonAdapter<JsonLogEntry> = moshi.adapter(JsonLogEntry::class.java)
    private var lastHash: String = "0".repeat(64)


    private val fileLoggerTree = FileLoggerTree.Builder()
        .withFileName(LOG_FILE_PATTERN)
        .withDir(logsDir)
        .withSizeLimit(FILE_SIZE_LIMIT)
        .withFileLimit(FILE_COUNT_LIMIT)
        .withMinPriority(Log.DEBUG)
        .appendToFile(true)
        .build()

    init {

        logsDir.mkdirs()
        Timber.plant(Timber.DebugTree(), fileLoggerTree)
        Timber.tag(tag).d("[Wallet init logs] Secure log initialized!")
        scope.launch {
            File(logsDir, "secure_logs.bin").sink().buffer().use { sink ->
                for (line in queue) {
                    sink.writeUtf8(line).writeByte(' '.code)
                                sink.flush()
                }
            }
        }
    }


    private fun computeSha256(parent: String, payload: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(parent.toByteArray(Charsets.UTF_8))
        md.update(payload.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun logJson(
        level: String,
        tag: String,
        message: String,
        correlationId: String? = null,
        component: String? = null,
        method: String? = null,
        url: String? = null,
        status: Int? = null,
        durationMs: Long? = null
    ) {
        val ts = System.currentTimeMillis()
        val baseEntry = JsonLogEntry(
            timestamp = ts,
            level = level,
            tag = tag,
            message = message,
            correlationId = correlationId,
            component = component,
            method = method,
            url = url,
            status = status,
            durationMs = durationMs,
            parentHash = lastHash,
            hash = null
        )
        val baseJson = jsonAdapter.toJson(baseEntry)
        val newHash = computeSha256(lastHash, baseJson)
        lastHash = newHash
        val fullEntry = baseEntry.copy(hash = newHash)
        val fullJson = jsonAdapter.toJson(fullEntry)
        Timber.tag(tag).d(fullJson)
    }


    override fun d(tag: String, message: () -> String) = logJson("DEBUG", tag, message())
    override fun d(message: () -> String) = d(tag, message)
    override fun i(tag: String, message: () -> String) = logJson("INFO", tag, message())
    override fun i(message: () -> String) = i(tag, message)
    override fun w(tag: String, message: () -> String) = logJson("WARN", tag, message())
    override fun w(message: () -> String) = w(tag, message)
    override fun e(tag: String, message: () -> String) = logJson("ERROR", tag, message())
    override fun e(tag: String, exception: Throwable) = e(tag) { exception.stackTraceToString() }
    override fun e(message: () -> String) = e(tag, message)
    override fun e(exception: Throwable) = e(tag) { exception.stackTraceToString() }


    override fun retrieveLogFileUris(): List<Uri> {
        return fileLoggerTree.files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
        }
    }

    override fun getLogStatistics(): JsonLogEntry {
        val lastLine = fileLoggerTree.files
            .flatMap { it.readLines() }
            .lastOrNull() ?: return JsonLogEntry(0, "", "", "No logs")
        return jsonAdapter.fromJson(lastLine) ?: JsonLogEntry(0, "", "", "Parse error")
    }

    override fun clearLogs() {
        logsDir.listFiles()?.forEach { it.delete() }
        lastHash = "0".repeat(64)
    }
}
package com.safelink.app.data.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.core.content.FileProvider
import com.safelink.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val versionName: String,    // e.g. "v1.1.0"
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val isNewer: Boolean
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()  // 0-100
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class AppUpdateService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val GITHUB_REPO   = "sumit01-coder/safelink"
        private const val RELEASES_API  = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val APK_FILE_NAME = "safelink-update.apk"
    }

    /**
     * Finds an internet-capable network (e.g. cellular) to use when Wi-Fi
     * has no internet (e.g. when connected to the SafeLink ESP32 hotspot).
     */
    private fun getInternetClient(): OkHttpClient {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Find any network that actually has internet (cellular, etc.)
        val internetNetwork: Network? = connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .apply {
                // Bind socket to internet-capable network if found
                if (internetNetwork != null) {
                    socketFactory(internetNetwork.socketFactory)
                }
            }
            .build()
    }

    /**
     * Check GitHub Releases API for a newer version.
     * Returns null if the app is already up to date or check fails.
     */
    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val internetClient = getInternetClient()
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = internetClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val tagName = json.getString("tag_name")          // e.g. "v1.1.0"
            val cleanTag = tagName.trimStart('v')             // "1.1.0"
            val notes    = json.optString("body", "Bug fixes and improvements.")
            val assets   = json.getJSONArray("assets")

            // Find the APK asset
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name  = asset.getString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isEmpty()) return@withContext null

            val isNewer = isVersionNewer(cleanTag, BuildConfig.VERSION_NAME)

            ReleaseInfo(
                versionName    = tagName,
                releaseNotes   = notes,
                apkDownloadUrl = apkUrl,
                isNewer        = isNewer
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads the APK and emits download progress as a Flow.
     */
    fun downloadUpdate(url: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))
        try {
            val request  = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("Download failed: HTTP ${response.code}"))
                return@flow
            }

            val body          = response.body ?: run {
                emit(DownloadState.Error("Empty response body"))
                return@flow
            }
            val contentLength = body.contentLength()

            val updateDir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val apkFile   = File(updateDir, APK_FILE_NAME)

            var bytesRead = 0L
            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        bytesRead += bytes
                        if (contentLength > 0) {
                            val progress = (bytesRead * 100 / contentLength).toInt()
                            emit(DownloadState.Downloading(progress))
                        }
                    }
                }
            }
            emit(DownloadState.Done(apkFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Launches the system APK installer for the downloaded file.
     */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Compare two semantic version strings ("1.1.0" vs "1.0.0").
     * Returns true if [remote] is strictly newer than [local].
     */
    private fun isVersionNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}

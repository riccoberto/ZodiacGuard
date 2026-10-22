package com.zodiacguard.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseUpdater(private val context: Context) {

    data class AvailableUpdate(
        val versionName: String,
        val downloadUrl: String
    )

    fun findAvailableUpdate(installedVersion: String): AvailableUpdate? {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = CONNECTION_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub ha risposto con HTTP $responseCode")
            }

            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val versionName = release.optString("tag_name").removePrefix("v")
            if (!isNewerVersion(versionName, installedVersion)) return null

            val assets = release.optJSONArray("assets")
                ?: throw IOException("La release non contiene APK")
            val apk = (0 until assets.length())
                .asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("La release non contiene un file APK")

            val downloadUrl = apk.optString("browser_download_url")
            if (!downloadUrl.startsWith("https://github.com/")) {
                throw IOException("Indirizzo APK della release non valido")
            }

            return AvailableUpdate(versionName, downloadUrl)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadAndVerify(update: AvailableUpdate): File {
        val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY)
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw IOException("Impossibile creare la cartella temporanea")
        }

        val temporaryFile = File(updateDirectory, "$APK_FILE_NAME.part")
        val apkFile = File(updateDirectory, APK_FILE_NAME)
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download fallito con HTTP $responseCode")
            }
            if (connection.contentLengthLong > MAX_APK_SIZE_BYTES) {
                throw IOException("Il file APK supera la dimensione consentita")
            }

            connection.inputStream.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        totalBytes += bytesRead
                        if (totalBytes > MAX_APK_SIZE_BYTES) {
                            throw IOException("Il file APK supera la dimensione consentita")
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            verifyApk(temporaryFile, update.versionName)
            if (apkFile.exists() && !apkFile.delete()) {
                throw IOException("Impossibile sostituire il vecchio APK temporaneo")
            }
            if (!temporaryFile.renameTo(apkFile)) {
                throw IOException("Impossibile preparare l’APK scaricato")
            }
            return apkFile
        } catch (exception: Exception) {
            temporaryFile.delete()
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyApk(apkFile: File, expectedVersion: String) {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: throw IOException("Il file scaricato non è un APK valido")

        if (packageInfo.packageName != context.packageName) {
            throw IOException("L’APK scaricato non appartiene a Z Guard")
        }
        if (packageInfo.versionName != expectedVersion ||
            !isNewerVersion(packageInfo.versionName.orEmpty(), installedVersionName())
        ) {
            throw IOException("La versione dell’APK non corrisponde alla release")
        }
    }

    private fun installedVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/riccoberto/ZodiacGuard/releases/latest"
        private const val USER_AGENT = "ZodiacGuard-Android"
        private const val UPDATE_DIRECTORY = "updates"
        private const val APK_FILE_NAME = "ZodiacGuard-update.apk"
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_TIMEOUT_MS = 60_000
        private const val MAX_APK_SIZE_BYTES = 100L * 1024L * 1024L

        internal fun isNewerVersion(candidate: String, installed: String): Boolean {
            val candidateParts = numericVersionParts(candidate) ?: return false
            val installedParts = numericVersionParts(installed) ?: return false
            val segmentCount = maxOf(candidateParts.size, installedParts.size)
            for (index in 0 until segmentCount) {
                val candidatePart = candidateParts.getOrElse(index) { 0 }
                val installedPart = installedParts.getOrElse(index) { 0 }
                if (candidatePart != installedPart) return candidatePart > installedPart
            }
            return false
        }

        private fun numericVersionParts(version: String): List<Int>? {
            val numericVersion = version.removePrefix("v").substringBefore('-').substringBefore('+')
            if (!numericVersion.matches(Regex("""\d+(?:\.\d+)*"""))) return null
            return numericVersion.split('.').map { it.toIntOrNull() ?: return null }
        }
    }
}

package com.insomdroid.dislauncher

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
            .takeIf { it != 0 }?.let { return it }

        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1
            other.prerelease == null -> -1
            else -> comparePrereleaseIdentifiers(prerelease, other.prerelease)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (prerelease?.let { "-$it" } ?: "")

    companion object {
        private val pattern = Regex(
            "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?$"
        )

        fun parse(value: String): SemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            val prerelease = match.groupValues[4].ifBlank { null }
            return SemanticVersion(major, minor, patch, prerelease)
        }

        private fun comparePrereleaseIdentifiers(first: String, second: String): Int {
            val firstParts = first.split(".")
            val secondParts = second.split(".")
            for (index in 0 until minOf(firstParts.size, secondParts.size)) {
                val left = firstParts[index]
                val right = secondParts[index]
                val leftNumber = left.toLongOrNull()
                val rightNumber = right.toLongOrNull()
                val comparison = when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> left.compareTo(right)
                }
                if (comparison != 0) return comparison
            }
            return firstParts.size.compareTo(secondParts.size)
        }
    }
}

internal data class StableReleaseInfo(
    val version: SemanticVersion,
    val releaseUrl: String,
    val publishedAt: String,
    val notes: String
)

internal sealed interface UpdateCheckResult {
    data object Checking : UpdateCheckResult

    data class Success(
        val release: StableReleaseInfo,
        val installedVersion: String,
        val isDevelopmentBuild: Boolean,
        val updateAvailable: Boolean
    ) : UpdateCheckResult

    data class Failure(val message: String) : UpdateCheckResult
}

internal object ReleaseUpdateChecker {
    private const val RELEASE_API_URL =
        "https://api.github.com/repos/insomdroid/Dislauncher/releases/latest"
    private const val RELEASE_PAGE_PREFIX =
        "https://github.com/insomdroid/Dislauncher/releases/tag/"

    suspend fun check(installedVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val installed = SemanticVersion.parse(installedVersion)
                ?: throw IOException("The installed app version is not valid.")
            val connection = (URL(RELEASE_API_URL).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Dislauncher-Android")

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("GitHub returned HTTP ${connection.responseCode}.")
                }

                val response = connection.inputStream.bufferedReader().use { reader ->
                    buildString {
                        val buffer = CharArray(8192)
                        var totalCharacters = 0
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            totalCharacters += count
                            if (totalCharacters > 1_000_000) {
                                throw IOException("The GitHub response was too large.")
                            }
                            append(buffer, 0, count)
                        }
                    }
                }
                val releaseJson = JSONObject(response)
                if (releaseJson.optBoolean("draft", false) ||
                    releaseJson.optBoolean("prerelease", false)
                ) {
                    throw IOException("GitHub did not return a stable release.")
                }

                val tagName = releaseJson.optString("tag_name")
                val latest = SemanticVersion.parse(tagName)
                    ?: throw IOException("The stable release tag is invalid.")
                val releaseUrl = releaseJson.optString("html_url")
                if (!releaseUrl.startsWith(RELEASE_PAGE_PREFIX)) {
                    throw IOException("The release URL is not trusted.")
                }

                val release = StableReleaseInfo(
                    version = latest,
                    releaseUrl = releaseUrl,
                    publishedAt = releaseJson.optString("published_at").takeIf { it != "null" }.orEmpty(),
                    notes = releaseJson.optString("body").takeIf { it != "null" }.orEmpty()
                )
                UpdateCheckResult.Success(
                    release = release,
                    installedVersion = installedVersion,
                    isDevelopmentBuild = installed.prerelease != null,
                    updateAvailable = latest > installed
                )
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UpdateCheckResult.Failure(
                "Check your internet connection and try again. GitHub may also be temporarily unavailable."
            )
        }
    }
}

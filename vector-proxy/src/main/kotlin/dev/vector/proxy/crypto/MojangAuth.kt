package dev.vector.proxy.crypto

import dev.vector.proxy.model.GameProfile
import dev.vector.proxy.model.ProfileProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

object MojangAuth {
    private val http = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verify(username: String, serverHash: String): GameProfile? = withContext(Dispatchers.IO) {
        val encodedUser = URLEncoder.encode(username, Charsets.UTF_8)
        val url = "https://sessionserver.mojang.com/session/minecraft/hasJoined" +
                "?username=$encodedUser&serverId=$serverHash"

        val request = HttpRequest.newBuilder(URI(url)).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) return@withContext null

        val profile = runCatching {
            json.decodeFromString<MojangProfileResponse>(response.body())
        }.getOrNull() ?: return@withContext null

        profile.toGameProfile()
    }

    @Serializable
    private data class MojangProfileResponse(
        val id: String,
        val name: String,
        val properties: List<MojangProperty> = emptyList(),
    )

    @Serializable
    private data class MojangProperty(
        val name: String,
        val value: String,
        val signature: String? = null,
    )

    private fun MojangProfileResponse.toGameProfile(): GameProfile {
        // Mojang returns UUID without hyphens — insert them.
        val uuid = UUID.fromString(
            id.replaceFirst(
                Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"),
                "$1-$2-$3-$4-$5"
            )
        )
        return GameProfile(
            uuid = uuid,
            username = name,
            properties = properties.map { ProfileProperty(it.name, it.value, it.signature) },
        )
    }
}

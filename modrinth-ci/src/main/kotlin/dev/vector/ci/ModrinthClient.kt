package dev.vector.ci

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private const val BASE = "https://api.modrinth.com/v2"
private const val UA   = "vector-modrinth-ci/1.0 (github.com/Earth1283/Vector)"

private val http = HttpClient.newHttpClient()
private val gson = Gson()

data class PluginInfo(
    val id: String,
    val slug: String,
    val title: String,
    val follows: Int,
)

data class VersionFile(
    val url: String,
    val filename: String,
    val size: Long,
)

fun searchVelocityPlugins(limit: Int): List<PluginInfo> {
    val facets = URLEncoder.encode(
        """[["project_type:plugin"],["categories:velocity"]]""",
        StandardCharsets.UTF_8,
    )
    val req = get("$BASE/search?query=&facets=$facets&limit=$limit&index=follows")
    check(req.statusCode() == 200) { "Modrinth search failed: ${req.statusCode()}" }

    return gson.fromJson(req.body(), JsonObject::class.java)
        .getAsJsonArray("hits")
        .map { el ->
            val o = el.asJsonObject
            PluginInfo(
                id      = o.get("project_id").asString,
                slug    = o.get("slug").asString,
                title   = o.get("title").asString,
                follows = o.get("follows").asInt,
            )
        }
}

fun resolveVelocityJar(projectId: String): VersionFile? {
    val loaders = URLEncoder.encode("""["velocity"]""", StandardCharsets.UTF_8)
    val resp = get("$BASE/project/$projectId/version?loaders=$loaders")
    if (resp.statusCode() != 200) return null

    val versions = gson.fromJson(resp.body(), JsonArray::class.java)
    if (versions.size() == 0) return null

    val files = versions[0].asJsonObject.getAsJsonArray("files")
    if (files.size() == 0) return null

    var primary = files[0].asJsonObject
    for (f in files) {
        val fo = f.asJsonObject
        if (fo.has("primary") && fo.get("primary").asBoolean) { primary = fo; break }
    }

    return VersionFile(
        url      = primary.get("url").asString,
        filename = primary.get("filename").asString,
        size     = primary.get("size").asLong,
    )
}

fun downloadJar(url: String, destDir: Path, filename: String): Path {
    val dest = destDir.resolve(filename)
    val resp = http.send(
        HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", UA).GET().build(),
        HttpResponse.BodyHandlers.ofInputStream(),
    )
    check(resp.statusCode() == 200) { "Download failed: ${resp.statusCode()}" }
    Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
        resp.body().use { it.copyTo(out) }
    }
    return dest
}

private fun get(url: String) = http.send(
    HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", UA).GET().build(),
    HttpResponse.BodyHandlers.ofString(),
)

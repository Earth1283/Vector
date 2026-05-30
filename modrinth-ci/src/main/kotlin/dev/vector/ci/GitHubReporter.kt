package dev.vector.ci

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val ghHttp = HttpClient.newHttpClient()
private val ghGson = Gson()

fun createGitHubIssue(title: String, body: String) {
    val token = System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() } ?: run {
        println("[GitHubReporter] GITHUB_TOKEN not set — skipping issue creation")
        return
    }
    val repo = (System.getenv("GITHUB_REPOSITORY") ?: System.getenv("GITHUB_REPO"))
        ?.takeIf { it.isNotBlank() } ?: run {
        println("[GitHubReporter] GITHUB_REPOSITORY not set — skipping issue creation")
        return
    }

    val payload = JsonObject().apply {
        addProperty("title", title)
        addProperty("body", body)
        add("labels", ghGson.toJsonTree(listOf("modrinth-ci", "bug")))
    }

    val resp = ghHttp.send(
        HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/$repo/issues"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(ghGson.toJson(payload)))
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    if (resp.statusCode() == 201) {
        val url = ghGson.fromJson(resp.body(), JsonObject::class.java).get("html_url").asString
        println("[GitHubReporter] Issue created: $url")
    } else {
        System.err.println("[GitHubReporter] Failed to create issue: ${resp.statusCode()} — ${resp.body().take(300)}")
    }
}

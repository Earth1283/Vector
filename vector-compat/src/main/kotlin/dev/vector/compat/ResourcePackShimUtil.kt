package dev.vector.compat

import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import java.net.URI
import java.util.UUID

// Builds an Adventure ResourcePackRequest from the fields Velocity's ResourcePackInfo carries.
// The hash is encoded as a lowercase hex SHA-1 string, which is what Adventure expects.
internal fun buildResourcePackRequest(
    id: UUID,
    url: String,
    hash: ByteArray?,
    prompt: Component?,
    force: Boolean,
): ResourcePackRequest {
    val hashHex = hash?.joinToString("") { "%02x".format(it) } ?: ""
    val pack = ResourcePackInfo.resourcePackInfo(id, URI.create(url), hashHex)
    return ResourcePackRequest.resourcePackRequest()
        .packs(pack)
        .prompt(prompt)
        .required(force)
        .build()
}

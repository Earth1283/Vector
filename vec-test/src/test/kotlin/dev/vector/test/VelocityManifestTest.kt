package dev.vector.test

import dev.vector.compat.VelocityManifest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VelocityManifestTest {

    @Test
    fun `parses minimal velocity-plugin json`() {
        val json = """{"id":"myplugin","main":"com.example.MyPlugin"}"""
        val manifest = VelocityManifest.parse(json)
        assertEquals("myplugin", manifest.id)
        assertEquals("com.example.MyPlugin", manifest.main)
        assertNull(manifest.name)
        assertNull(manifest.version)
        assertTrue(manifest.authors.isEmpty())
        assertTrue(manifest.dependencies.isEmpty())
    }

    @Test
    fun `parses full velocity-plugin json`() {
        val json = """
        {
            "id": "testplugin",
            "name": "Test Plugin",
            "version": "2.0.0",
            "description": "A test plugin",
            "url": "https://example.com",
            "authors": ["Alice", "Bob"],
            "dependencies": [
                {"id": "luckperms", "optional": false},
                {"id": "minimotd", "optional": true}
            ],
            "main": "com.example.TestPlugin"
        }
        """.trimIndent()
        val manifest = VelocityManifest.parse(json)
        assertEquals("testplugin", manifest.id)
        assertEquals("Test Plugin", manifest.name)
        assertEquals("2.0.0", manifest.version)
        assertEquals("A test plugin", manifest.description)
        assertEquals(listOf("Alice", "Bob"), manifest.authors)
        assertEquals(2, manifest.dependencies.size)
        val lp = manifest.dependencies.first { it.id == "luckperms" }
        assertFalse(lp.optional)
        val mm = manifest.dependencies.first { it.id == "minimotd" }
        assertTrue(mm.optional)
    }
}

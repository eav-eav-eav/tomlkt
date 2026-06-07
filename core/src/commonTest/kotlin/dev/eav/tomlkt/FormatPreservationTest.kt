package dev.eav.tomlkt

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Round-trip goal: parse a TOML document, re-encode the resulting [TomlTable],
 * and get the original text back verbatim. These pin the target for comment-
 * and format-preserving round-tripping.
 *
 * They currently FAIL: the parser discards comments and the emitter reformats.
 * The failure diffs show exactly what is lost.
 */
class FormatPreservationTest {
    private fun assertRoundTrip(toml: String) {
        val table = Toml.parseToTomlTable(toml)
        val encoded = Toml.encodeToString(TomlTable.serializer(), table)
        assertEquals(toml, encoded)
    }

    @Test
    fun inlineComment() {
        assertRoundTrip("name = \"Tom\" # the owner")
    }

    @Test
    fun standaloneComment() {
        assertRoundTrip(
            """
            # The owner's name.
            name = "Tom"
            """.trimIndent()
        )
    }

    @Test
    fun commentAboveTable() {
        assertRoundTrip(
            """
            # Database settings.
            [database]
            enabled = true
            """.trimIndent()
        )
    }

    @Test
    fun multipleCommentsAndKeys() {
        assertRoundTrip(
            """
            # Top comment.
            title = "Example"

            # A section.
            [owner]
            name = "Tom" # inline
            """.trimIndent()
        )
    }

    @Test
    fun multilineBasicString() {
        assertRoundTrip("text = \"\"\"\nline one\nline two\n\"\"\"")
    }

    @Test
    fun literalString() {
        assertRoundTrip("path = 'C:\\Users'")
    }

    @Test
    fun plainNoComments() {
        // Even with no comments, the emitter must match the input's layout.
        assertRoundTrip(
            """
            title = "Example"

            [owner]
            name = "Tom"
            age = 42
            """.trimIndent()
        )
    }
}

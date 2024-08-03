package dev.extframework.boot.test.maven

import com.durganmcbroom.artifact.resolver.simple.maven.SimpleMavenDescriptor
import dev.extframework.boot.maven.VERSION_REGEX
import dev.extframework.boot.maven.rankVersion
import dev.extframework.boot.maven.sortMavenDescriptorVersion
import dev.extframework.boot.maven.writeBase
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestVersionSort {
    @Test
    fun `Test release version descriptor parse`() {
        val version = "5.60.2"

        val versionComponents = VERSION_REGEX.matchEntire(version)?.groupValues

        assertTrue(versionComponents != null && versionComponents.size >= 4)
    }

    @Test
    fun `Test snapshot version descriptor parse`() {
        val version = "5.60-SNAPSHOT"

        val versionComponents = VERSION_REGEX.matchEntire(version)!!.groups

        assertTrue(versionComponents.size >= 4)
        assertEquals(versionComponents["scheme"]?.value, "SNAPSHOT")
    }

    @Test
    fun `Test base generates correctly`() {
        val base = writeBase(1U, 2U, 3U)

        val flag = 0b0100000100000010000000110000000000000000000000000000000000000000U
        assertTrue(base and flag == flag && base or flag == flag)
    }

    infix fun String.greaterThan(other: String): Boolean {
        val thisRank = rankVersion(this)
        val otherRank = rankVersion(other)

        return thisRank > otherRank
    }

    @Test
    fun `Test version comparison`() {
        assertTrue("5.5.5" greaterThan "1.1.1")
        assertTrue("5.5.5" greaterThan "6.6.6-SNAPSHOT")
        assertTrue("2020.08.05" greaterThan "2015.12.5")
        assertTrue("5.5.5-RC2" greaterThan "5.5.5-RC1")
        assertTrue("5.5.6-RC1" greaterThan "5.5.5-RC2")
    }

    @Test
    fun `Test sorting descriptors`() {
        val descriptors = listOf("1.0", "1.0-SNAPSHOT", "1.1", "1.0-RC1", "1.2")
            .map { SimpleMavenDescriptor.parseDescription("a:a:$it")!! }

        assertContentEquals(
            descriptors
                .sortedBy(::sortMavenDescriptorVersion)
                .map { it.version },

            listOf("1.0-SNAPSHOT", "1.0-RC1", "1.0", "1.1", "1.2")
        )
    }
}
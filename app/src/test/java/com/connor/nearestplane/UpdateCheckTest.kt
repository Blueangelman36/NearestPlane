package com.connor.nearestplane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun `versions compare by part, not as strings`() {
        assertTrue(UpdateCheck.isNewer("1.10", "1.9"))
        assertFalse(UpdateCheck.isNewer("1.9", "1.10"))
    }

    @Test fun `equal is not newer, and a missing part is zero`() {
        assertFalse(UpdateCheck.isNewer("1.8", "1.8"))
        assertTrue(UpdateCheck.isNewer("1.8.1", "1.8"))
        assertFalse(UpdateCheck.isNewer("1.8", "1.8.0"))
    }

    @Test fun `a leading v and a suffix are tolerated`() {
        assertTrue(UpdateCheck.isNewer("v2.0", "1.9"))
        assertTrue(UpdateCheck.isNewer("1.9-beta", "1.8"))
    }

    // Shaped like GitHub's releases list, newest-created first, trimmed to the
    // fields the update check reads.
    private fun release(
        tag: String,
        vararg assets: String,
        draft: Boolean = false,
        prerelease: Boolean = false
    ): String {
        val files = assets.joinToString(",") { name ->
            """{"name":"$name","browser_download_url":"https://example.test/$tag/$name"}"""
        }
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,""" +
            """"html_url":"https://example.test/releases/$tag","assets":[$files]}"""
    }

    private fun list(vararg releases: String) = releases.joinToString(",", "[", "]")

    @Test fun `a release with no APK yet is passed over for the newest that has one`() {
        // The case that sent Download to a source-code-only page: v1.11 was
        // published, and its build refused it, so it never got an APK.
        val pick = UpdateCheck.newestInstallable(list(
            release("v1.11"),
            release("v1.10", "nearest-plane-1.10.apk", "SHA256SUMS"),
            release("v1.9", "nearest-plane-1.9.apk", "SHA256SUMS")
        ))!!
        assertEquals("1.10", pick.version)
        assertEquals("https://example.test/v1.10/nearest-plane-1.10.apk", pick.apkUrl)
        assertEquals("https://example.test/releases/v1.10", pick.pageUrl)
    }

    @Test fun `a checksum file is not an APK`() {
        assertNull(UpdateCheck.newestInstallable(list(release("v1.11", "SHA256SUMS"))))
    }

    @Test fun `drafts and pre-releases are never offered`() {
        val pick = UpdateCheck.newestInstallable(list(
            release("v1.12", "a.apk", prerelease = true),
            release("v1.11", "b.apk", draft = true),
            release("v1.10", "c.apk")
        ))!!
        assertEquals("1.10", pick.version)
    }

    @Test fun `the highest version wins, not the most recently created`() {
        val pick = UpdateCheck.newestInstallable(list(
            release("v1.9", "old.apk"),
            release("v1.10", "new.apk")
        ))!!
        assertEquals("1.10", pick.version)
        assertEquals("https://example.test/v1.10/new.apk", pick.apkUrl)
    }

    @Test fun `nothing installable means nothing to offer`() {
        assertNull(UpdateCheck.newestInstallable("[]"))
        assertNull(UpdateCheck.newestInstallable(list(release("v1.11"), release("v1.10"))))
    }
}

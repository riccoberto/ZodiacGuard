package com.zodiacguard.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdaterTest {

    @Test
    fun `newer numeric release is available`() {
        assertTrue(GitHubReleaseUpdater.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(GitHubReleaseUpdater.isNewerVersion("v2.0", "1.9.9"))
    }

    @Test
    fun `same or older release is not available`() {
        assertFalse(GitHubReleaseUpdater.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(GitHubReleaseUpdater.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(GitHubReleaseUpdater.isNewerVersion("invalid", "1.0.0"))
    }
}

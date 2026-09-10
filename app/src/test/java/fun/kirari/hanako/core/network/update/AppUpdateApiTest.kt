package `fun`.kirari.hanako.core.network.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateApiTest {
    @Test
    fun extractReleaseChangelogMarkdown_keepsOnlyChangelogBeforeDownloadHint() {
        val markdown = """
            **Full Changelog**: https://example.com

            ## Changelog
            - 修复启动流程
            - 优化悬浮窗

            ## 下载提示
            下载 app-lite-arm64-v8a-release.apk
        """.trimIndent()

        assertEquals(
            "- 修复启动流程\n- 优化悬浮窗",
            extractReleaseChangelogMarkdown(markdown)
        )
    }

    @Test
    fun extractReleaseChangelogMarkdown_supportsChineseHeadingAndDownloadDescription() {
        val markdown = """
            ## 更新日志
            - 完善联网搜索

            ## 下载说明
            下载 Lite 版本
        """.trimIndent()

        assertEquals("- 完善联网搜索", extractReleaseChangelogMarkdown(markdown))
    }

    @Test
    fun extractReadmeUpdateInfo_readsDownloadVersionAndReleaseNotesLink() {
        val markdown = """
            # Hanako

            [[Download 0.0.13-alpha](https://github.com/shinanyan/HanakoAI/releases/download/v0.0.13-alpha/app-lite-arm64-v8a-release.apk)]  [[View Release Notes](https://github.com/shinanyan/HanakoAI/releases/tag/v0.0.13-alpha)]

            ## Changelog

            - 新增升级提示
            - 修复通知权限提示
        """.trimIndent()

        val update = extractReadmeUpdateInfo(markdown, currentVersion = "0.0.12-alpha")

        assertEquals("0.0.13-alpha", update?.version)
        assertEquals("v0.0.13-alpha", update?.releaseName)
        assertEquals("https://github.com/shinanyan/HanakoAI/releases/tag/v0.0.13-alpha", update?.releaseUrl)
        assertEquals("- 新增升级提示\n- 修复通知权限提示", update?.changelogMarkdown)
    }

    @Test
    fun extractReadmeUpdateInfo_ignoresForkSuffixInDownloadVersion() {
        // fork 仓库的 README 会在版本号后追加括号后缀，例如 "0.0.20-alpha (fork · debug)"。
        // 该后缀不能进入 AppUpdateInfo.version，否则更新弹窗会显示「发现新版本 0.0.20-alpha (fork · debug)」。
        val markdown = """
            # Hanako

            [[Download 0.0.20-alpha (fork · debug)](https://github.com/shinanyan/HanakoAI/releases/tag/v0.0.20-alpha)]  [[View Release Notes](https://github.com/shinanyan/HanakoAI/releases/tag/v0.0.20-alpha)] [[Telegram](https://t.me/hutao_space)]

            ## Changelog

            - 移除作者自建 AI 服务
        """.trimIndent()

        val update = extractReadmeUpdateInfo(markdown, currentVersion = "0.0.19-alpha")

        assertEquals("0.0.20-alpha", update?.version)
        assertEquals("v0.0.20-alpha", update?.releaseName)
        assertEquals("https://github.com/shinanyan/HanakoAI/releases/tag/v0.0.20-alpha", update?.releaseUrl)
        assertEquals("- 移除作者自建 AI 服务", update?.changelogMarkdown)
    }

    @Test
    fun extractReadmeUpdateInfo_stripsForkSuffixWhenReleaseNotesLinkIsMissing() {
        val markdown = """
            [[Download 0.0.20-alpha (fork · debug)](https://example.com/download.apk)]
        """.trimIndent()

        val update = extractReadmeUpdateInfo(markdown, currentVersion = "0.0.19-alpha")

        assertEquals("0.0.20-alpha", update?.version)
        assertEquals("https://example.com/download.apk", update?.releaseUrl)
    }

    @Test
    fun extractReadmeUpdateInfo_returnsNullWhenReadmeVersionIsNotNewer() {
        val markdown = """
            [[Download 0.0.12-alpha](https://example.com/download.apk)]  [[View Release Notes](https://example.com/release)]
        """.trimIndent()

        assertNull(extractReadmeUpdateInfo(markdown, currentVersion = "0.0.12-alpha"))
    }

    @Test
    fun extractReadmeUpdateInfo_usesEmptyChangelogWhenReadmeHasNoChangelogSection() {
        val markdown = """
            [[Download 0.0.12-alpha](https://example.com/download.apk)]  [[View Release Notes](https://example.com/release)]
        """.trimIndent()

        val update = extractReadmeUpdateInfo(markdown, currentVersion = "0.0.11-alpha")

        assertEquals("", update?.changelogMarkdown)
    }

    @Test
    fun extractReadmeChangelogMarkdown_keepsEverythingAfterChangelogHeading() {
        val markdown = """
            # Hanako

            ## Other
            ignored

            ## Changelog
            - A

            ### Detail
            full detail
        """.trimIndent()

        assertEquals("- A\n\n### Detail\nfull detail", extractReadmeChangelogMarkdown(markdown))
    }

    @Test
    fun isRemoteVersionNewer_comparesSemverAndPrereleaseRank() {
        assertTrue(isRemoteVersionNewer("v0.0.12-alpha", "0.0.11-alpha"))
        assertTrue(isRemoteVersionNewer("0.0.12", "0.0.12-alpha"))
        assertFalse(isRemoteVersionNewer("0.0.12-alpha", "0.0.12"))
    }
}

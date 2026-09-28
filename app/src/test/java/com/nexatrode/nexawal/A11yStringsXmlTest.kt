package com.nexatrode.nexawal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM unit tests for accessibility string resources.
 * Runs in CI without an emulator (`:app:testDebugUnitTest`).
 */
class A11yStringsXmlTest {

    private val valuesDir = File("src/main/res/values")

    private val localeDirs = File("src/main/res")
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        .map { it.name }
        .sorted()

    private val translatedHistoryAndSafetyKeys = listOf(
        "history_back",
        "history_title",
        "history_view_all_fmt",
        "history_pending_count_fmt",
        "history_search_txid",
        "history_filter_all",
        "history_from_date",
        "history_through_date",
        "history_invalid_date_range",
        "history_results_fmt",
        "history_incomplete_sync",
        "history_changed_reload",
        "history_load_failed",
        "history_retry",
        "history_no_matches",
        "history_reload",
        "history_loading_transaction",
        "history_details_failed",
        "send_fee_increased_unapproved",
        "send_already_in_progress",
    )

    private val translatedCommonUiKeys = listOf(
        "label_throughput_avg",
        "label_throughput_recent",
        "toggle_mainnet",
        "legal_load_error",
        "source_on_github",
        "link_copied",
        "section_info",
        "section_chain",
        "daemon_url_label",
    )

    private val requiredA11yKeys = listOf(
        "a11y_sync_progress_fmt",
        "a11y_show_sync_details",
        "a11y_hide_sync_details",
        "a11y_transfer_row_fmt",
        "a11y_camera_preview",
        "a11y_currency_menu",
        "a11y_currency_menu_expanded",
        "a11y_currency_menu_collapsed",
        "a11y_on",
        "a11y_off",
        "scan_qr_cd",
        "receive_qr_cd",
        "nav_wallet",
        "nav_send",
        "nav_receive",
        "nav_settings",
        "toggle_techno_theme",
        "toggle_mainnet",
    )

    @Test
    fun allLocales_haveMatchingKeys() {
        val en = loadStringNames(File(valuesDir, "strings.xml"))
        for (dir in localeDirs) {
            val locale = loadStringNames(File("src/main/res/$dir", "strings.xml"))
            assertEquals("values and $dir must have the same string names", en, locale)
        }
    }

    @Test
    fun requiredA11yKeys_presentInAllLocales() {
        val en = loadStringNames(File(valuesDir, "strings.xml"))
        for (key in requiredA11yKeys) {
            assertTrue("missing EN key: $key", key in en)
        }
        for (dir in localeDirs) {
            val locale = loadStringNames(File("src/main/res/$dir", "strings.xml"))
            for (key in requiredA11yKeys) {
                assertTrue("missing $dir key: $key", key in locale)
            }
        }
    }

    @Test
    fun a11yKeys_haveNonBlankTranslatedValues() {
        for (dir in localeDirs) {
            val values = loadStringValues(File("src/main/res/$dir", "strings.xml"))
            for (key in requiredA11yKeys) {
                val value = values[key]
                assertTrue("blank $dir value for $key", !value.isNullOrBlank())
            }
        }
    }

    @Test
    fun transactionHistoryAndSendSafetyCopy_isTranslatedInEveryLocale() {
        for (dir in localeDirs) {
            val values = loadStringValues(File("src/main/res/$dir", "strings.xml"))
            for (key in translatedHistoryAndSafetyKeys) {
                assertTrue("missing or blank $dir value for $key", !values[key].isNullOrBlank())
            }
        }
    }

    @Test
    fun commonUiCopy_isTranslatedInEveryLocale() {
        val english = loadStringValues(File(valuesDir, "strings.xml"))
        for (dir in localeDirs) {
            val values = loadStringValues(File("src/main/res/$dir", "strings.xml"))
            for (key in translatedCommonUiKeys) {
                val translated = values[key]
                assertTrue("missing or blank $dir value for $key", !translated.isNullOrBlank())
                assertTrue("$dir falls back to English for $key", translated != english[key])
            }
        }
    }

    @Test
    fun transactionHistoryResultFormat_preservesBothArgumentsInEveryLocale() {
        for (dir in localeDirs) {
            val value = loadStringValues(File("src/main/res/$dir", "strings.xml")).getValue("history_results_fmt")
            assertTrue("$dir is missing the matching-count argument", value.contains("%1\$d"))
            assertTrue("$dir is missing the total-count argument", value.contains("%2\$d"))
        }
    }

    @Test
    fun walletHistoryButtonFormats_preserveCountArgumentInEveryLocale() {
        for (dir in localeDirs) {
            val values = loadStringValues(File("src/main/res/$dir", "strings.xml"))
            assertTrue(
                "$dir is missing the count argument in history_view_all_fmt",
                values.getValue("history_view_all_fmt").contains("%1\$d"),
            )
            assertTrue(
                "$dir is missing the count argument in history_pending_count_fmt",
                values.getValue("history_pending_count_fmt").contains("%1\$d"),
            )
        }
    }

    @Test
    fun transferRowFormat_hasThreePlaceholders() {
        val en = loadStringValues(File(valuesDir, "strings.xml")).getValue("a11y_transfer_row_fmt")
        assertTrue(en.contains("%1\$s"))
        assertTrue(en.contains("%2\$s"))
        assertTrue(en.contains("%3\$s"))
    }

    private fun loadStringNames(file: File): Set<String> {
        assertTrue("missing ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val names = linkedSetOf<String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            names += el.getAttribute("name")
        }
        return names
    }

    private fun loadStringValues(file: File): Map<String, String> {
        assertTrue("missing ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = el.textContent.trim()
        }
        return out
    }
}

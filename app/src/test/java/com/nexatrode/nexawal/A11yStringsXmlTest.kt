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

    private val localeDirs = listOf(
        "values-es",
        "values-pt-rBR",
        "values-zh-rCN",
        "values-ru",
        "values-de",
        "values-fr",
        "values-ja",
        "values-ko",
        "values-it",
        "values-nl",
        "values-in",
    )

    private val requiredA11yKeys = listOf(
        "a11y_sync_progress_fmt",
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
        "toggle_classic_ui",
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

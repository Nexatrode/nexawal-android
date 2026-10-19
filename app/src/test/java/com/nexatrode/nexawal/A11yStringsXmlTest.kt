package com.nexatrode.nexawal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        "history_export_csv",
        "history_export_failed",
        "copy_explorer_link",
        "fiat_help_nexatrode",
        "about_disclaimer_nexatrode",
        "toggle_mainnet",
        "legal_load_error",
        "source_on_github",
        "link_copied",
        "section_info",
        "section_chain",
        "daemon_url_label",
        "haptic_feedback",
        "send_sounds",
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
    fun allFormats_preserveArgumentPositionsAndTypes() {
        val english = loadStringValues(File(valuesDir, "strings.xml"))
        for (dir in localeDirs) {
            val translated = loadStringValues(File("src/main/res/$dir", "strings.xml"))
            for ((key, value) in english) {
                val localized = translated.getValue(key)
                assertTrue("blank $dir: $key", localized.isNotBlank())
                assertEquals("format changed in $dir: $key", arguments(value), arguments(localized))
            }
        }
    }

    private fun arguments(value: String): List<String> {
        val regex = Regex("%(?:(\\d+)\\$)?[-+0# ]*\\d*(?:\\.\\d+)?([a-zA-Z])")
        return regex.findAll(value.replace("%%", "")).mapIndexed { index, match ->
            val position = match.groupValues[1].ifEmpty { (index + 1).toString() }
            "$position:${match.groupValues[2]}"
        }.sorted().toList()
    }

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
    fun supportedPrivacyDisclosuresSeparateIpFromWalletSyncQueries() {
        val expectedPhrases = mapOf(
            "es" to "su dirección IP y las consultas de sincronización de su cartera",
            "fr" to "votre adresse IP et les requêtes de synchronisation de votre portefeuille",
            "de" to "Ihre IP-Adresse und die Synchronisierungsanfragen Ihrer Wallet",
            "ar" to "عنوان IP الخاص بك وطلبات مزامنة المحفظة",
            "pl" to "Twój adres IP i zapytania synchronizacji portfela",
            "pt-rBR" to "seu endereço IP e as solicitações de sincronização da carteira",
            "ru" to "ваш IP-адрес и запросы синхронизации кошелька",
            "vi" to "địa chỉ IP và các yêu cầu đồng bộ hóa ví của bạn",
            "zh-rCN" to "您的 IP 地址和钱包同步请求",
            "zh-rTW" to "您的 IP 位址與錢包同步請求",
        )
        for ((locale, phrase) in expectedPhrases) {
            val disclaimer = loadStringValues(File("src/main/res/values-$locale/strings.xml"))
                .getValue("about_disclaimer_nexatrode")
            assertTrue("$locale must distinguish the IP address from wallet sync queries", disclaimer.contains(phrase))
            assertTrue("$locale must identify the rates service", disclaimer.contains("rates.nexawal.com"))
        }
        assertFalse(
            loadStringValues(File("src/main/res/values-es/strings.xml"))
                .getValue("about_disclaimer_nexatrode").contains("consultas de sincronización de IP")
        )
        assertFalse(
            loadStringValues(File("src/main/res/values-fr/strings.xml"))
                .getValue("about_disclaimer_nexatrode").contains("synchronisation IP")
        )
        assertFalse(
            loadStringValues(File("src/main/res/values-de/strings.xml"))
                .getValue("about_disclaimer_nexatrode").contains("IP-Synchronisierungsanfragen")
        )
        assertFalse(loadStringValues(File("src/main/res/values-zh-rCN/strings.xml"))
            .getValue("about_disclaimer_nexatrode").contains("麻省理工学院"))
        assertFalse(loadStringValues(File("src/main/res/values-zh-rTW/strings.xml"))
            .getValue("about_disclaimer_nexatrode").contains("麻省理工學院"))
        assertFalse(loadStringValues(File("src/main/res/values-pt-rBR/strings.xml"))
            .getValue("about_disclaimer_nexatrode").contains("período de recuperação"))
        assertFalse(loadStringValues(File("src/main/res/values-vi/strings.xml"))
            .getValue("about_disclaimer_nexatrode").contains("thời hạn khôi phục"))
        assertFalse(loadStringValues(File("src/main/res/values-pl/strings.xml"))
            .getValue("about_disclaimer_nexatrode").contains("zwrot środków"))
        assertFalse(loadStringValues(File("src/main/res/values-ru/strings.xml"))
            .getValue("about_disclaimer_nexatrode").contains("позвоните по номеру"))
    }

    @Test
    fun germanRecoveryCopyUsesConsistentRecoveryPhraseTerm() {
        val german = loadStringValues(File("src/main/res/values-de/strings.xml"))
        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "terms_body_custody")) {
            assertTrue("German recovery copy should use the established term: $key", german.getValue(key).contains("Wiederherstellungsphrase"))
        }
        assertTrue(german.getValue("recovery_seed_instructions").contains("der Reihe nach"))
        assertTrue(german.getValue("recovery_seed_instructions").contains("offline"))
        assertTrue(german.getValue("recovery_seed_instructions").contains("verschlüsselte Kopie auf dem Gerät"))
        assertFalse(german.getValue("replace_wallet_message").contains("Mnemonic"))
        assertFalse(german.getValue("terms_body_custody").contains("Seed-Phrase"))
    }

    @Test
    fun italianAndJapaneseWalletCriticalCopyIsConsistent() {
        val italian = loadStringValues(File("src/main/res/values-it/strings.xml"))
        val japanese = loadStringValues(File("src/main/res/values-ja/strings.xml"))

        assertTrue(italian.getValue("about_disclaimer_nexatrode").contains("indirizzo IP e le richieste di sincronizzazione"))
        assertTrue(italian.getValue("about_disclaimer_nexatrode").contains("l’app recupera i tassi da rates.nexawal.com"))
        assertFalse(italian.getValue("about_disclaimer_nexatrode").contains("non certificato"))
        assertTrue(italian.getValue("network_policy_help").contains("Entrambe le modalità"))
        assertTrue(italian.getValue("terms_body_custody").contains("frase di recupero di 25 parole"))
        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody")) {
            assertTrue("Italian recovery copy should use the user-facing term: $key", italian.getValue(key).contains("frase di recupero", ignoreCase = true))
        }

        assertTrue(japanese.getValue("about_disclaimer_nexatrode").contains("IPアドレスとウォレット同期のリクエスト"))
        assertTrue(japanese.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com からレートを取得します"))
        assertFalse(japanese.getValue("about_disclaimer_nexatrode").contains("お問い合わせください"))
        assertTrue(japanese.getValue("terms_body_custody").contains("25 語の復元フレーズ"))
        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody")) {
            assertTrue("Japanese recovery copy should use the user-facing term: $key", japanese.getValue(key).contains("復元フレーズ"))
        }
        assertTrue(japanese.getValue("network_policy_help").contains("I2P経由でブロードキャスト"))
        assertFalse(japanese.getValue("starting_height_fast_fmt").contains("height"))
    }

    @Test
    fun arabicAndPolishWalletCriticalCopyIsClear() {
        val locales = listOf(
            Triple("ar", "عبارة الاسترداد", "بعد خصم رسوم الشبكة"),
            Triple("pl", "frazę odzyskiwania", "po odliczeniu opłaty sieciowej"),
        )
        val recoveryKeys = listOf(
            "replace_wallet_message",
            "generate_new_seed",
            "wrote_seed_down",
            "recovery_seed_label",
            "mnemonic_paste_label",
            "mnemonic_paste_placeholder",
            "error_mnemonic_empty",
            "error_rescan_no_mnemonic",
            "storage_missing_mnemonic",
            "stored_metadata_no_mnemonic",
        )

        for ((locale, recoveryTerm, feePhrase) in locales) {
            val values = loadStringValues(File("src/main/res/values-$locale/strings.xml"))
            val custody = values.getValue("terms_body_custody")
            assertTrue("$locale custody copy must name the recovery phrase", custody.contains(recoveryTerm, ignoreCase = true))

            val forbidden = if (locale == "ar") {
                listOf("mnemonic", "seed", "بذرة", "العبارة التذكيرية")
            } else {
                listOf("mnemonic", "seed", "ziarno", "self-custodial", "plik początkowy")
            }
            for (key in recoveryKeys) {
                val value = values.getValue(key)
                for (term in forbidden) {
                    assertFalse("$locale $key contains technical or misleading wording: $term", value.contains(term, ignoreCase = true))
                }
            }

            val sendMax = values.getValue("biometric_send_max_subtitle")
            assertTrue("$locale Send Max confirmation must disclose fee deduction", sendMax.contains(feePhrase, ignoreCase = true))
            assertTrue("$locale total label must mention the fee", values.getValue("total_amount_fee").contains(if (locale == "ar") "الرسوم" else "opłat", ignoreCase = true))
            assertTrue("$locale formatted total must preserve the amount argument", values.getValue("total_with_fee_fmt").contains("%1\$s"))

            assertTrue("$locale filtered-history count must preserve both arguments", values.getValue("history_results_fmt").contains("%1\$d") && values.getValue("history_results_fmt").contains("%2\$d"))
            assertTrue("$locale pending-history count must preserve its argument", values.getValue("history_pending_count_fmt").contains("%1\$d"))
            if (locale == "ar") {
                assertTrue(values.getValue("history_results_fmt").contains("عدد النتائج المطابقة"))
                assertTrue(values.getValue("history_pending_count_fmt").contains("عدد المعاملات المعلّقة"))
            } else {
                assertTrue(values.getValue("history_results_fmt").startsWith("Wyniki:"))
                assertTrue(values.getValue("history_pending_count_fmt").startsWith("Liczba oczekujących transakcji:"))
            }

            val privacy = values.getValue("about_disclaimer_nexatrode")
            assertTrue("$locale privacy copy must name the rates service", privacy.contains("rates.nexawal.com"))
            assertTrue("$locale privacy copy must identify IP visibility", privacy.contains("IP"))
        }
    }

    @Test
    fun brazilianPortugueseAndRussianWalletCriticalCopyIsClear() {
        val locales = listOf(
            Triple("pt-rBR", "frase de recuperação", "após descontar a taxa de rede"),
            Triple("ru", "фраз", "за вычетом сетевой комиссии"),
        )
        val recoveryKeys = listOf(
            "terms_body_custody", "replace_wallet_message", "generate_new_seed", "wrote_seed_down", "seed_challenge_intro",
            "recovery_seed_label", "recovery_seed_instructions", "mnemonic_paste_label", "mnemonic_paste_placeholder",
            "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic",
        )

        for ((locale, recoveryTerm, feePhrase) in locales) {
            val values = loadStringValues(File("src/main/res/values-$locale/strings.xml"))
            val forbidden = if (locale == "pt-rBR") {
                listOf("seed", "mnemônico", "mnemônica", "semente")
            } else {
                listOf("seed", "сид-фраз", "мнемонич")
            }

            for (key in recoveryKeys) {
                val value = values.getValue(key)
                assertTrue("$locale $key must name the recovery phrase", value.contains(recoveryTerm, ignoreCase = true))
                for (term in forbidden) assertFalse("$locale $key contains technical wording: $term", value.contains(term, ignoreCase = true))
            }

            val sendMax = values.getValue("biometric_send_max_subtitle")
            assertTrue("$locale Send Max confirmation must explain the fee deduction", sendMax.contains(feePhrase, ignoreCase = true))
            assertTrue(values.getValue("total_amount_fee").contains(if (locale == "pt-rBR") "taxa" else "комисс", ignoreCase = true))
            assertTrue(values.getValue("total_with_fee_fmt").contains("%1\$s"))
            assertTrue(values.getValue("send_max_amount_fmt").contains("%1\$s"))

            val results = values.getValue("history_results_fmt")
            assertTrue(results.contains("%1\$d") && results.contains("%2\$d"))
            assertTrue(values.getValue("history_pending_count_fmt").contains("%1\$d"))
            assertTrue(values.getValue("confirmations_fmt").contains("%1\$s"))

            val hybridPolicy = values.getValue("network_policy_hybrid")
            val policyHelp = values.getValue("network_policy_help")
            if (locale == "pt-rBR") {
                assertTrue(hybridPolicy.contains("sincronizar", ignoreCase = true) && hybridPolicy.contains("transmitir", ignoreCase = true))
                assertFalse(hybridPolicy.contains("varrer", ignoreCase = true))
                assertTrue(policyHelp.contains("sincroniza", ignoreCase = true))
                assertTrue(values.getValue("restore_height_tip").contains("escanear", ignoreCase = true))
            } else {
                assertTrue(hybridPolicy.contains("синхронизация", ignoreCase = true) && hybridPolicy.contains("транзакций", ignoreCase = true))
                assertTrue(policyHelp.contains("синхронизирует", ignoreCase = true))
                assertTrue(values.getValue("rescan_from_height").contains("синхронизировать", ignoreCase = true))
            }

            val about = values.getValue("about_disclaimer_nexatrode")
            assertTrue(about.contains("rates.nexawal.com"))
            assertTrue(about.contains("IP"))
            assertFalse(about.contains("contact", ignoreCase = true))
            val rates = values.getValue("fiat_help_nexatrode")
            assertTrue(rates.contains("rates.nexawal.com"))
            assertTrue(rates.contains("I2P"))
            for (term in forbidden) assertFalse("$locale rates disclosure contains recovery material: $term", rates.contains(term, ignoreCase = true))

            val english = loadStringValues(File(valuesDir, "strings.xml"))
            for (key in listOf("history_from_date", "history_through_date", "history_search_txid", "history_no_matches", "history_loading_transaction", "toggle_mainnet")) {
                assertNotEquals("$locale falls back to English for $key", english[key], values[key])
            }
        }
    }

    @Test
    fun dutchAndTurkishWalletCriticalCopyIsClear() {
        val dutch = loadStringValues(File("src/main/res/values-nl/strings.xml"))
        val turkish = loadStringValues(File("src/main/res/values-tr/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody")) {
            assertTrue("Dutch recovery copy should use herstelzin: $key", dutch.getValue(key).contains("herstelzin", ignoreCase = true))
            assertTrue("Turkish recovery copy should use kurtarma ifadesi: $key", turkish.getValue(key).contains("kurtarma if", ignoreCase = true))
        }
        assertTrue(dutch.getValue("about_disclaimer_nexatrode").contains("niet onafhankelijk op veiligheid gecontroleerd"))
        assertTrue(dutch.getValue("about_disclaimer_nexatrode").contains("haalt de app koersen op bij rates.nexawal.com"))
        assertFalse(dutch.getValue("about_disclaimer_nexatrode").contains("neem contact op met rates.nexawal.com"))
        assertTrue(dutch.getValue("total_amount_fee").contains("bedrag + netwerkkosten"))
        assertTrue(turkish.getValue("about_disclaimer_nexatrode").contains("bağımsız bir güvenlik denetiminden geçmemiştir"))
        assertTrue(turkish.getValue("about_disclaimer_nexatrode").contains("kurları rates.nexawal.com adresinden alır"))
        assertFalse(turkish.getValue("about_disclaimer_nexatrode").contains("ile iletişime geçin"))
        assertTrue(turkish.getValue("total_amount_fee").contains("tutar + ücret"))
        assertTrue(dutch.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(turkish.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun azerbaijaniAndBulgarianWalletCriticalCopyIsClear() {
        val azerbaijani = loadStringValues(File("src/main/res/values-az/strings.xml"))
        val bulgarian = loadStringValues(File("src/main/res/values-bg/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody")) {
            assertTrue("Azerbaijani recovery copy should use bərpa ifadəsi: $key", azerbaijani.getValue(key).contains("bərpa ifad", ignoreCase = true))
            assertTrue("Bulgarian recovery copy should refer to възстановяване: $key", bulgarian.getValue(key).contains("възстановяване", ignoreCase = true))
        }
        assertTrue(azerbaijani.getValue("about_disclaimer_nexatrode").contains("müstəqil təhlükəsizlik auditindən keçməmiş"))
        assertTrue(azerbaijani.getValue("about_disclaimer_nexatrode").contains("tətbiq məzənnələri rates.nexawal.com saytından alır"))
        assertFalse(azerbaijani.getValue("about_disclaimer_nexatrode").contains("əlaqə saxlayın"))
        assertTrue(azerbaijani.getValue("network_policy_help").contains("Hər iki rejim"))
        assertTrue(azerbaijani.getValue("biometric_send_max_subtitle").contains("kilidi açılmış balansın hamısını göndərmək"))
        assertTrue(bulgarian.getValue("about_disclaimer_nexatrode").contains("не е преминал независим одит по сигурността"))
        assertTrue(bulgarian.getValue("about_disclaimer_nexatrode").contains("приложението получава курсовете от rates.nexawal.com"))
        assertFalse(bulgarian.getValue("about_disclaimer_nexatrode").contains("свържете се с rates.nexawal.com"))
        assertTrue(bulgarian.getValue("network_policy_help").contains("И двата режима сканират"))
        assertTrue(bulgarian.getValue("biometric_send_max_subtitle").contains("цялото отключено салдо"))
        assertTrue(azerbaijani.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(bulgarian.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun bengaliAndCatalanWalletCriticalCopyIsClear() {
        val bengali = loadStringValues(File("src/main/res/values-bn/strings.xml"))
        val catalan = loadStringValues(File("src/main/res/values-ca/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody")) {
            assertTrue("Bengali recovery copy should say পুনরুদ্ধার বাক্যাংশ: $key", bengali.getValue(key).contains("পুনরুদ্ধার বাক্যাংশ"))
            assertTrue("Catalan recovery copy should say frase de recuperació: $key", catalan.getValue(key).contains("frase de recuperació", ignoreCase = true))
        }
        assertTrue(bengali.getValue("about_disclaimer_nexatrode").contains("স্বাধীন নিরাপত্তা নিরীক্ষা হয়নি"))
        assertTrue(bengali.getValue("about_disclaimer_nexatrode").contains("অ্যাপ rates.nexawal.com থেকে বিনিময় হার সংগ্রহ করে"))
        assertFalse(bengali.getValue("about_disclaimer_nexatrode").contains("যোগাযোগ করুন"))
        assertTrue(bengali.getValue("biometric_send_max_subtitle").contains("পুরো আনলক করা ব্যালেন্স পাঠাতে"))
        assertTrue(catalan.getValue("about_disclaimer_nexatrode").contains("sense una auditoria de seguretat independent"))
        assertTrue(catalan.getValue("about_disclaimer_nexatrode").contains("l’aplicació obté els tipus de canvi de rates.nexawal.com"))
        assertFalse(catalan.getValue("about_disclaimer_nexatrode").contains("poseu-vos en contacte"))
        assertTrue(catalan.getValue("network_policy_help").contains("El mode Híbrid escaneja per Clearnet"))
        assertTrue(catalan.getValue("biometric_send_max_subtitle").contains("tot el saldo desbloquejat disponible"))
        assertTrue(bengali.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(catalan.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun czechAndDanishWalletCriticalCopyIsClear() {
        val czech = loadStringValues(File("src/main/res/values-cs/strings.xml"))
        val danish = loadStringValues(File("src/main/res/values-da/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody")) {
            assertTrue("Czech recovery copy should use fráze pro obnovení: $key", czech.getValue(key).contains("obnovení", ignoreCase = true))
            assertTrue("Danish recovery copy should use gendannelsesfrase: $key", danish.getValue(key).contains("gendannelsesfrase", ignoreCase = true))
        }
        assertTrue(czech.getValue("about_disclaimer_nexatrode").contains("nezávislým bezpečnostním auditem"))
        assertTrue(czech.getValue("about_disclaimer_nexatrode").contains("aplikace načítá kurzy z rates.nexawal.com"))
        assertFalse(czech.getValue("about_disclaimer_nexatrode").contains("kontaktujte rates.nexawal.com"))
        assertTrue(czech.getValue("network_policy_help").contains("Hybridní režim skenuje"))
        assertTrue(czech.getValue("biometric_send_max_subtitle").contains("celého odemčeného zůstatku"))
        assertTrue(danish.getValue("about_disclaimer_nexatrode").contains("uafhængigt sikkerhedsrevideret"))
        assertTrue(danish.getValue("about_disclaimer_nexatrode").contains("henter appen kurser fra rates.nexawal.com"))
        assertFalse(danish.getValue("about_disclaimer_nexatrode").contains("kontakt rates.nexawal.com"))
        assertTrue(danish.getValue("network_policy_help").contains("Hybridtilstanden scanner"))
        assertTrue(danish.getValue("biometric_send_max_subtitle").contains("hele den tilgængelige, oplåste saldo"))
        assertTrue(czech.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(danish.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun greekAndEsperantoWalletCriticalCopyIsClear() {
        val greek = loadStringValues(File("src/main/res/values-el/strings.xml"))
        val esperanto = loadStringValues(File("src/main/res/values-eo/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "terms_body_custody", "error_mnemonic_empty")) {
            assertTrue("Greek recovery copy should use ανάκτηση wording: $key", greek.getValue(key).contains("ανάκτη", ignoreCase = true))
            assertTrue("Esperanto recovery copy should use reakira frazo: $key", esperanto.getValue(key).contains("reakir", ignoreCase = true))
        }
        assertTrue(greek.getValue("about_disclaimer_nexatrode").contains("δεν έχει ελεγχθεί ανεξάρτητα ως προς την ασφάλεια"))
        assertTrue(greek.getValue("about_disclaimer_nexatrode").contains("η εφαρμογή λαμβάνει ισοτιμίες από το rates.nexawal.com"))
        assertFalse(greek.getValue("about_disclaimer_nexatrode").contains("επικοινωνήστε με το rates.nexawal.com"))
        assertTrue(greek.getValue("network_policy_help").contains("Η υβριδική λειτουργία σαρώνει"))
        assertTrue(greek.getValue("biometric_send_max_subtitle").contains("όλο το διαθέσιμο ξεκλείδωτο υπόλοιπο"))
        assertTrue(esperanto.getValue("about_disclaimer_nexatrode").contains("ne estis sendepende kontrolita pri sekureco"))
        assertTrue(esperanto.getValue("about_disclaimer_nexatrode").contains("la aplikaĵo ricevas kurzojn de rates.nexawal.com"))
        assertFalse(esperanto.getValue("about_disclaimer_nexatrode").contains("kontaktu rates.nexawal.com"))
        assertTrue(esperanto.getValue("network_policy_help").contains("Hibrida reĝimo skanas"))
        assertTrue(esperanto.getValue("biometric_send_max_subtitle").contains("tutan disponeblan malŝlositan saldon"))
        assertTrue(greek.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(esperanto.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(esperanto.getValue("total_amount_fee").contains("sumo + kotizo"))
    }

    @Test
    fun estonianAndBasqueWalletCriticalCopyIsClear() {
        val estonian = loadStringValues(File("src/main/res/values-et/strings.xml"))
        val basque = loadStringValues(File("src/main/res/values-eu/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "terms_body_custody", "error_mnemonic_empty")) {
            assertTrue("Estonian recovery copy should use taastefraas: $key", estonian.getValue(key).contains("taastefraas", ignoreCase = true))
            assertTrue("Basque recovery copy should use berreskuratze-esaldia: $key", basque.getValue(key).contains("berreskuratze-esaldi", ignoreCase = true))
        }
        assertTrue(estonian.getValue("about_disclaimer_nexatrode").contains("pole sõltumatult turvalisuse suhtes auditeeritud"))
        assertTrue(estonian.getValue("about_disclaimer_nexatrode").contains("hangib rakendus kursid aadressilt rates.nexawal.com"))
        assertFalse(estonian.getValue("about_disclaimer_nexatrode").contains("võtke hindade saamiseks ühendust"))
        assertTrue(estonian.getValue("network_policy_help").contains("Hübriidrežiim skannib"))
        assertTrue(estonian.getValue("biometric_send_max_subtitle").contains("saadaolev lukustamata saldo"))
        assertTrue(basque.getValue("about_disclaimer_nexatrode").contains("ez du segurtasun-auditoria independentea gainditu"))
        assertTrue(basque.getValue("about_disclaimer_nexatrode").contains("aplikazioak rates.nexawal.com-etik eskuratzen ditu truke-tasak"))
        assertFalse(basque.getValue("about_disclaimer_nexatrode").contains("jarri harremanetan rates.nexawal.com"))
        assertTrue(basque.getValue("network_policy_help").contains("Modu hibridoak Clearnet bidez eskaneatzen du"))
        assertTrue(basque.getValue("biometric_send_max_subtitle").contains("saldo desblokeatu osoa bidaltzeko"))
        assertTrue(estonian.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(basque.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun persianAndFinnishWalletCriticalCopyIsClear() {
        val persian = loadStringValues(File("src/main/res/values-fa/strings.xml"))
        val finnish = loadStringValues(File("src/main/res/values-fi/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "terms_body_custody", "error_mnemonic_empty")) {
            assertTrue("Persian recovery copy should use عبارت بازیابی: $key", persian.getValue(key).contains("عبارت بازیابی"))
            assertTrue("Finnish recovery copy should use palautuslauseke: $key", finnish.getValue(key).contains("palautuslause", ignoreCase = true))
        }
        assertTrue(persian.getValue("about_disclaimer_nexatrode").contains("ممیزی مستقل امنیتی نشده است"))
        assertTrue(persian.getValue("about_disclaimer_nexatrode").contains("برنامه نرخ‌ها را از rates.nexawal.com دریافت می‌کند"))
        assertFalse(persian.getValue("about_disclaimer_nexatrode").contains("تماس بگیرید"))
        assertTrue(persian.getValue("network_policy_help").contains("حالت ترکیبی"))
        assertTrue(persian.getValue("biometric_send_max_subtitle").contains("پس از کسر کارمزد"))
        assertTrue(persian.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(finnish.getValue("about_disclaimer_nexatrode").contains("ei ole auditoitu riippumattomasti tietoturvan osalta"))
        assertTrue(finnish.getValue("about_disclaimer_nexatrode").contains("sovellus noutaa valuuttakurssit palvelusta rates.nexawal.com"))
        assertFalse(finnish.getValue("about_disclaimer_nexatrode").contains("ota yhteyttä rates.nexawal.com"))
        assertTrue(finnish.getValue("network_policy_help").contains("Hybriditilassa"))
        assertTrue(finnish.getValue("biometric_send_max_subtitle").contains("koko käytettävissä olevan lukitsemattoman saldon"))
        assertTrue(finnish.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun filipinoAndIrishWalletCriticalCopyIsClear() {
        val filipino = loadStringValues(File("src/main/res/values-tl/strings.xml"))
        val irish = loadStringValues(File("src/main/res/values-ga/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "terms_body_custody", "error_mnemonic_empty")) {
            assertTrue("Filipino recovery copy should use recovery terminology: $key", filipino.getValue(key).contains("pagbawi", ignoreCase = true))
            assertTrue("Irish recovery copy should use recovery terminology: $key", irish.getValue(key).contains("athshlánaithe", ignoreCase = true))
        }
        assertTrue(filipino.getValue("network_policy_help").contains("Sa hybrid mode"))
        assertTrue(filipino.getValue("biometric_send_max_subtitle").contains("matapos ibawas ang bayad sa network"))
        assertTrue(filipino.getValue("send_fee_increased_unapproved").contains("Walang transaksyong naipadala"))
        assertTrue(filipino.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com"))
        assertFalse(filipino.getValue("about_disclaimer_nexatrode").contains("makipag-ugnayan"))
        assertTrue(filipino.getValue("haptic_feedback").contains("panginginig"))
        assertTrue(filipino.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(irish.getValue("about_disclaimer_nexatrode").contains("nár ndearnadh iniúchadh slándála neamhspleách"))
        assertTrue(irish.getValue("about_disclaimer_nexatrode").contains("faigheann an aip rátaí malairte ó rates.nexawal.com"))
        assertFalse(irish.getValue("about_disclaimer_nexatrode").contains("déan teagmháil"))
        assertTrue(irish.getValue("network_policy_help").contains("Sa mhód hibrideach"))
        assertTrue(irish.getValue("biometric_send_max_subtitle").contains("tar éis an táille líonra a asbhaint"))
        assertTrue(irish.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun galicianAndGuaraniWalletCriticalCopyIsClear() {
        val galician = loadStringValues(File("src/main/res/values-gl/strings.xml"))
        val guarani = loadStringValues(File("src/main/res/values-gn/strings.xml"))

        for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "terms_body_custody", "error_mnemonic_empty")) {
            assertTrue("Galician recovery copy should say frase de recuperación: $key", galician.getValue(key).contains("frase de recuperación", ignoreCase = true))
            assertTrue("Guarani recovery copy should say frase de recuperación: $key", guarani.getValue(key).contains("frase de recuperación", ignoreCase = true))
        }
        assertTrue(galician.getValue("total_amount_fee").contains("comisión"))
        assertTrue(galician.getValue("about_disclaimer_nexatrode").contains("consultas de sincronización da carteira"))
        assertTrue(galician.getValue("about_disclaimer_nexatrode").contains("a aplicación obtén as taxas de rates.nexawal.com"))
        assertFalse(galician.getValue("about_disclaimer_nexatrode").contains("póñase en contacto"))
        assertTrue(galician.getValue("biometric_send_max_subtitle").contains("despois de descontar a comisión da rede"))
        assertTrue(galician.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(guarani.getValue("network_policy_help").contains("Modo híbrido"))
        assertTrue(guarani.getValue("biometric_send_max_subtitle").contains("comisión de red"))
        assertTrue(guarani.getValue("about_disclaimer_nexatrode").contains("ogueru tasa de cambio rates.nexawal.com-gui"))
        assertFalse(guarani.getValue("about_disclaimer_nexatrode").contains("eñe’ẽ rates.nexawal.com ndive"))
        assertTrue(guarani.getValue("send_fee_increased_unapproved").contains("No se difundió ninguna transacción"))
        assertTrue(guarani.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun hausaAndHebrewWalletCriticalCopyIsClear() {
        val hausa = loadStringValues(File("src/main/res/values-ha/strings.xml"))
        val hebrew = loadStringValues(File("src/main/res/values-he/strings.xml"))

        for (key in listOf("replace_wallet_message", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "terms_body_custody", "error_mnemonic_empty", "storage_missing_mnemonic")) {
            assertTrue("Hausa should consistently say dawowa: $key", hausa.getValue(key).contains("dawowa", ignoreCase = true))
            assertTrue("Hebrew should consistently say ביטוי השחזור: $key", hebrew.getValue(key).contains("ביטוי השחזור"))
        }
        assertTrue(hausa.getValue("generate_new_seed").contains("kalmomin dawowa"))
        assertTrue(hebrew.getValue("generate_new_seed").contains("ביטוי שחזור"))
        assertTrue(hausa.getValue("mnemonic_paste_placeholder").contains("jumlar dawowa"))
        assertTrue(hebrew.getValue("mnemonic_paste_placeholder").contains("ביטוי שחזור בן 25"))
        assertTrue(hausa.getValue("terms_body_custody").contains("jumlar dawowa"))
        assertTrue(hausa.getValue("about_disclaimer_nexatrode").contains("binciken tsaro mai zaman kansa"))
        assertTrue(hausa.getValue("about_disclaimer_nexatrode").contains("manhajar tana neman ƙimar kuɗi daga rates.nexawal.com"))
        assertFalse(hausa.getValue("about_disclaimer_nexatrode").contains("tuntuɓi rates.nexawal.com"))
        assertTrue(hausa.getValue("biometric_send_max_subtitle").contains("bayan cire kuɗin hanyar sadarwa"))
        assertTrue(hausa.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(hebrew.getValue("about_disclaimer_nexatrode").contains("ביקורת אבטחה עצמאית"))
        assertTrue(hebrew.getValue("about_disclaimer_nexatrode").contains("האפליקציה תבקש שערי חליפין מ-rates.nexawal.com"))
        assertFalse(hebrew.getValue("about_disclaimer_nexatrode").contains("צור קשר"))
        assertTrue(hebrew.getValue("biometric_send_max_subtitle").contains("לאחר ניכוי עמלת הרשת"))
        assertTrue(hebrew.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertFalse(hebrew.getValue("biometric_send_max_subtitle").contains("לרוקן את יתרת הארנק"))
    }

    @Test
    fun hindiAndCroatianWalletCriticalCopyIsClear() {
        val hindi = loadStringValues(File("src/main/res/values-hi/strings.xml"))
        val croatian = loadStringValues(File("src/main/res/values-hr/strings.xml"))

        assertTrue(hindi.getValue("terms_body_custody").contains("पुनर्प्राप्ति वाक्यांश"))
        assertTrue(hindi.getValue("replace_wallet_message").contains("पुनर्प्राप्ति वाक्यांश का बैकअप"))
        assertTrue(hindi.getValue("about_disclaimer_nexatrode").contains("ऐप rates.nexawal.com से विनिमय दर माँगता है"))
        assertFalse(hindi.getValue("about_disclaimer_nexatrode").contains("contact rates.nexawal.com"))
        assertTrue(hindi.getValue("biometric_send_max_subtitle").contains("नेटवर्क शुल्क घटाने के बाद"))
        assertTrue(hindi.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(croatian.getValue("terms_body_custody").contains("neskrbničko je sučelje"))
        assertTrue(croatian.getValue("terms_body_custody").contains("frazu za oporavak od 25 riječi"))
        assertTrue(croatian.getValue("replace_wallet_message").contains("sigurnosnu kopiju fraze za oporavak"))
        assertTrue(croatian.getValue("about_disclaimer_nexatrode").contains("aplikacija dohvaća tečajeve s rates.nexawal.com"))
        assertFalse(croatian.getValue("about_disclaimer_nexatrode").contains("kontaktirajte rates.nexawal.com"))
        assertTrue(croatian.getValue("biometric_send_max_subtitle").contains("nakon odbitka mrežne naknade"))
        assertTrue(croatian.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(croatian.getValue("error_mnemonic_empty").contains("Fraza za oporavak"))
    }

    @Test
    fun hungarianAndArmenianWalletCriticalCopyIsClear() {
        val hungarian = loadStringValues(File("src/main/res/values-hu/strings.xml"))
        val armenian = loadStringValues(File("src/main/res/values-hy/strings.xml"))

        assertTrue(hungarian.getValue("terms_body_custody").contains("25 szavas helyreállítási kifejezés"))
        assertTrue(hungarian.getValue("replace_wallet_message").contains("helyreállítási kifejezés"))
        assertFalse(hungarian.getValue("replace_wallet_message").contains("mnemonik"))
        assertTrue(hungarian.getValue("about_disclaimer_nexatrode").contains("az alkalmazás a rates.nexawal.com oldalról kéri le az árfolyamokat"))
        assertFalse(hungarian.getValue("about_disclaimer_nexatrode").contains("forduljon a rates.nexawal.com"))
        assertTrue(hungarian.getValue("biometric_send_max_subtitle").contains("hálózati díj levonása után"))
        assertTrue(hungarian.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(armenian.getValue("terms_body_custody").contains("25 բառից բաղկացած վերականգնման արտահայտության"))
        assertTrue(armenian.getValue("replace_wallet_message").contains("վերականգնման բառերը"))
        assertTrue(armenian.getValue("about_disclaimer_nexatrode").contains("հավելվածը փոխարժեքները հարցնում է rates.nexawal.com-ից"))
        assertFalse(armenian.getValue("about_disclaimer_nexatrode").contains("կապվեք rates.nexawal.com"))
        assertTrue(armenian.getValue("biometric_send_max_subtitle").contains("ցանցի վճարը հանելուց հետո"))
        assertTrue(armenian.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun indonesianAndKoreanWalletCriticalCopyIsClear() {
        val indonesian = loadStringValues(File("src/main/res/values-in/strings.xml"))
        val korean = loadStringValues(File("src/main/res/values-ko/strings.xml"))

        assertTrue(indonesian.getValue("terms_body_custody").contains("frasa pemulihan 25 kata"))
        assertTrue(indonesian.getValue("replace_wallet_message").contains("frasa pemulihan"))
        assertFalse(indonesian.getValue("replace_wallet_message").contains("mnemonic"))
        assertTrue(indonesian.getValue("about_disclaimer_nexatrode").contains("aplikasi mengambil kurs dari rates.nexawal.com"))
        assertFalse(indonesian.getValue("about_disclaimer_nexatrode").contains("hubungi rates.nexawal.com"))
        assertTrue(indonesian.getValue("biometric_send_max_subtitle").contains("setelah biaya jaringan dipotong"))
        assertTrue(indonesian.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(korean.getValue("terms_body_custody").contains("25단어 복구 문구"))
        assertTrue(korean.getValue("replace_wallet_message").contains("저장된 복구 문구"))
        assertFalse(korean.getValue("replace_wallet_message").contains("니모닉"))
        assertTrue(korean.getValue("about_disclaimer_nexatrode").contains("앱이 rates.nexawal.com에서 환율을 가져옵니다"))
        assertFalse(korean.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com에 문의"))
        assertTrue(korean.getValue("biometric_send_max_subtitle").contains("네트워크 수수료를 차감한 뒤"))
        assertTrue(korean.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun kyrgyzAndLithuanianWalletCriticalCopyIsClear() {
        val kyrgyz = loadStringValues(File("src/main/res/values-ky/strings.xml"))
        val lithuanian = loadStringValues(File("src/main/res/values-lt/strings.xml"))

        assertTrue(kyrgyz.getValue("terms_body_custody").contains("25 сөздөн турган калыбына келтирүү фраза"))
        assertTrue(kyrgyz.getValue("replace_wallet_message").contains("калыбына келтирүү сөздөрү"))
        assertTrue(kyrgyz.getValue("about_disclaimer_nexatrode").contains("колдонмо курстарды rates.nexawal.com сайтынан алат"))
        assertFalse(kyrgyz.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com менен байланышыңыз"))
        assertTrue(kyrgyz.getValue("biometric_send_max_subtitle").contains("комиссия алынгандан кийин"))
        assertTrue(kyrgyz.getValue("send_max_amount_fmt").contains("%1\$s"))

        assertTrue(lithuanian.getValue("terms_body_custody").contains("25 žodžių atkūrimo frazę"))
        assertTrue(lithuanian.getValue("replace_wallet_message").contains("atkūrimo frazė"))
        assertFalse(lithuanian.getValue("replace_wallet_message").contains("mnemoninė frazė"))
        assertTrue(lithuanian.getValue("about_disclaimer_nexatrode").contains("programėlė kursus gauna iš rates.nexawal.com"))
        assertFalse(lithuanian.getValue("about_disclaimer_nexatrode").contains("susisiekite su rates.nexawal.com"))
        assertTrue(lithuanian.getValue("biometric_send_max_subtitle").contains("atskaičius tinklo mokestį"))
        assertTrue(lithuanian.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun latvianAndMalayWalletCriticalCopyIsClear() {
        val latvian = loadStringValues(File("src/main/res/values-lv/strings.xml"))
        val malay = loadStringValues(File("src/main/res/values-ms/strings.xml"))

        assertTrue(latvian.getValue("terms_body_custody").contains("25 vārdu atkopšanas frāzi"))
        assertTrue(latvian.getValue("replace_wallet_message").contains("atkopšanas frāze"))
        assertTrue(latvian.getValue("about_disclaimer_nexatrode").contains("lietotne iegūst kursus no rates.nexawal.com"))
        assertFalse(latvian.getValue("about_disclaimer_nexatrode").contains("sazinieties ar rates.nexawal.com"))
        assertTrue(latvian.getValue("biometric_send_max_subtitle").contains("pēc tīkla maksas atskaitīšanas"))
        assertTrue(latvian.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(latvian.getValue("label_progress").contains("Norise"))

        assertTrue(malay.getValue("terms_body_custody").contains("frasa pemulihan 25 perkataan"))
        assertTrue(malay.getValue("replace_wallet_message").contains("frasa pemulihan"))
        assertTrue(malay.getValue("about_disclaimer_nexatrode").contains("aplikasi mendapatkan kadar daripada rates.nexawal.com"))
        assertFalse(malay.getValue("about_disclaimer_nexatrode").contains("hubungi rates.nexawal.com"))
        assertTrue(malay.getValue("biometric_send_max_subtitle").contains("selepas yuran rangkaian ditolak"))
        assertTrue(malay.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(malay.getValue("label_node").contains("Nod"))
    }

    @Test
    fun burmeseAndNorwegianWalletCriticalCopyIsClear() {
        val burmese = loadStringValues(File("src/main/res/values-my/strings.xml"))
        val norwegian = loadStringValues(File("src/main/res/values-nb/strings.xml"))

        assertTrue(burmese.getValue("terms_body_custody").contains("စကားလုံး ၂၅ လုံးပါ ပြန်လည်ရယူရေး စကားစု"))
        assertTrue(burmese.getValue("replace_wallet_message").contains("ပြန်လည်ရယူရေး စကားစု"))
        assertFalse(burmese.getValue("replace_wallet_message").contains("mnemonic"))
        assertTrue(burmese.getValue("about_disclaimer_nexatrode").contains("အက်ပ်သည် rates.nexawal.com မှ ဈေးနှုန်းများကို ရယူသည်"))
        assertFalse(burmese.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com သို့ ဆက်သွယ်ပါ"))
        assertTrue(burmese.getValue("biometric_send_max_subtitle").contains("ကွန်ရက်အခကြေးငွေနုတ်ပြီးနောက်"))
        assertTrue(burmese.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(burmese.getValue("label_node").contains("နိုဒ်"))
        assertTrue(burmese.getValue("section_clearnet_node").contains("Clearnet နိုဒ်"))
        assertTrue(burmese.getValue("i2p_proxy_placeholder").contains("ကြားခံဆာဗာ"))

        assertTrue(norwegian.getValue("terms_body_custody").contains("gjenopprettingsfrasen på 25 ord"))
        assertTrue(norwegian.getValue("replace_wallet_message").contains("gjenopprettingsfrasen"))
        assertFalse(norwegian.getValue("replace_wallet_message").contains("mnemonic"))
        assertTrue(norwegian.getValue("about_disclaimer_nexatrode").contains("henter appen kurs fra rates.nexawal.com"))
        assertFalse(norwegian.getValue("about_disclaimer_nexatrode").contains("kontakt rates.nexawal.com"))
        assertTrue(norwegian.getValue("biometric_send_max_subtitle").contains("etter at nettverksgebyret er trukket fra"))
        assertTrue(norwegian.getValue("send_max_amount_fmt").contains("%1\$s"))
    }

    @Test
    fun romanianAndSlovakWalletCriticalCopyIsClear() {
        val romanian = loadStringValues(File("src/main/res/values-ro/strings.xml"))
        val slovak = loadStringValues(File("src/main/res/values-sk/strings.xml"))

        assertTrue(romanian.getValue("terms_body_custody").contains("fraza de recuperare de 25 de cuvinte"))
        assertTrue(romanian.getValue("replace_wallet_message").contains("fraza de recuperare"))
        assertTrue(romanian.getValue("wrote_seed_down").contains("fraza mea de recuperare"))
        assertTrue(romanian.getValue("recovery_seed_label").contains("Fraza ta de recuperare"))
        for (key in listOf("replace_wallet_message", "mnemonic_paste_label", "mnemonic_paste_placeholder", "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic")) {
            assertFalse("Romanian $key should avoid the technical term", romanian.getValue(key).contains("mnemonic", ignoreCase = true))
        }
        assertTrue(romanian.getValue("about_disclaimer_nexatrode").contains("adresa ta IP și cererile de sincronizare ale portofelului"))
        assertTrue(romanian.getValue("about_disclaimer_nexatrode").contains("aplicația preia cursurile de la rates.nexawal.com"))
        assertTrue(romanian.getValue("fiat_help_nexatrode").contains("solicită prin HTTPS"))
        assertTrue(romanian.getValue("biometric_send_max_subtitle").contains("întregul sold deblocat după deducerea comisionului de rețea"))
        assertTrue(romanian.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(romanian.getValue("total_with_fee_fmt").contains("%1\$s"))

        assertTrue(slovak.getValue("terms_body_custody").contains("25-slovnú frázu na obnovenie"))
        assertTrue(slovak.getValue("replace_wallet_message").contains("frázu na obnovenie"))
        assertTrue(slovak.getValue("wrote_seed_down").contains("frázu na obnovenie"))
        for (key in listOf("replace_wallet_message", "mnemonic_paste_label", "mnemonic_paste_placeholder", "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic")) {
            assertFalse("Slovak $key should avoid the technical term", slovak.getValue(key).contains("mnemonic", ignoreCase = true))
        }
        assertTrue(slovak.getValue("about_disclaimer_nexatrode").contains("vašu IP adresu a požiadavky na synchronizáciu peňaženky"))
        assertTrue(slovak.getValue("about_disclaimer_nexatrode").contains("aplikácia načítava kurzy zo servera rates.nexawal.com"))
        assertTrue(slovak.getValue("fiat_help_nexatrode").contains("vyžiada kurz XMR"))
        assertTrue(slovak.getValue("biometric_send_max_subtitle").contains("celý odomknutý zostatok po odpočítaní sieťového poplatku"))
        assertTrue(slovak.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(slovak.getValue("total_with_fee_fmt").contains("%1\$s"))

        for ((locale, values) in listOf("ro" to romanian, "sk" to slovak)) {
            for (key in listOf("history_from_date", "history_through_date", "history_search_txid", "history_no_matches", "history_loading_transaction", "toggle_mainnet")) {
                assertTrue("$locale is missing or untranslated for $key", !values[key].isNullOrBlank())
                assertNotEquals("$locale falls back to English for $key", loadStringValues(File(valuesDir, "strings.xml"))[key], values[key])
            }
        }
    }

    @Test
    fun slovenianAndAlbanianWalletCriticalCopyIsClear() {
        val slovenian = loadStringValues(File("src/main/res/values-sl/strings.xml"))
        val albanian = loadStringValues(File("src/main/res/values-sq/strings.xml"))
        val english = loadStringValues(File(valuesDir, "strings.xml"))

        for ((locale, values, feePhrase) in listOf(
                Triple("Slovenian", slovenian, "omrežnine"),
                Triple("Albanian", albanian, "rrjetit"),
            )) {
            assertTrue(values.getValue("terms_body_custody").contains("obnovitveno frazo") || values.getValue("terms_body_custody").contains("frazën e rikuperimit"))
            assertTrue(values.getValue("replace_wallet_message").contains("obnovitvena fraza") || values.getValue("replace_wallet_message").contains("e rikuperimit"))
            assertTrue(values.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com"))
            assertFalse("$locale should say the app fetches rates, not contact the host", values.getValue("about_disclaimer_nexatrode").contains("contact", ignoreCase = true))
            assertTrue("$locale must explain the fee deduction", values.getValue("biometric_send_max_subtitle").contains(feePhrase))
            assertTrue(values.getValue("send_max_amount_fmt").contains("%1\$s"))
            assertTrue(values.getValue("total_with_fee_fmt").contains("%1\$s"))

            for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic", "terms_body_custody")) {
                val value = values.getValue(key)
                assertFalse("$locale $key contains technical seed wording", value.contains("seed", ignoreCase = true))
                assertFalse("$locale $key contains technical mnemonic wording", value.contains("mnemonic", ignoreCase = true))
            }

            for (key in listOf("history_from_date", "history_through_date", "history_search_txid", "history_no_matches", "history_loading_transaction", "toggle_mainnet")) {
                assertNotEquals("$locale is untranslated for $key", english[key], values[key])
            }
        }
    }

    @Test
    fun swedishAndThaiWalletCriticalCopyIsClear() {
        val swedish = loadStringValues(File("src/main/res/values-sv/strings.xml"))
        val thai = loadStringValues(File("src/main/res/values-th/strings.xml"))
        val english = loadStringValues(File(valuesDir, "strings.xml"))

        for ((locale, values) in listOf("Swedish" to swedish, "Thai" to thai)) {
            val recoveryPhrase = if (locale == "Swedish") "återställningsfras" else "วลีกู้คืน"
            val feePhrase = if (locale == "Swedish") "nätverksavgiften" else "ค่าธรรมเนียมเครือข่าย"
            val terms = values.getValue("terms_body_custody")
            assertTrue("$locale terms must explain the recovery phrase", terms.contains(recoveryPhrase))
            assertTrue("$locale replacement warning must mention the recovery phrase", values.getValue("replace_wallet_message").contains(recoveryPhrase))
            assertTrue(values.getValue("wrote_seed_down").contains(recoveryPhrase))
            assertTrue(values.getValue("recovery_seed_label").contains(recoveryPhrase))
            assertTrue(values.getValue("about_disclaimer_nexatrode").contains("rates.nexawal.com"))
            assertFalse("$locale should say the app fetches rates, not contact the host", values.getValue("about_disclaimer_nexatrode").contains("contact", ignoreCase = true))
            assertTrue("$locale send-max confirmation must disclose the network fee", values.getValue("biometric_send_max_subtitle").contains(feePhrase))
            assertTrue(values.getValue("send_max_amount_fmt").contains("%1\$s"))
            assertTrue(values.getValue("total_with_fee_fmt").contains("%1\$s"))

            for (key in listOf("replace_wallet_message", "generate_new_seed", "wrote_seed_down", "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder", "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic", "terms_body_custody")) {
                val value = values.getValue(key)
                assertFalse("$locale $key contains technical seed wording", value.contains("seed", ignoreCase = true))
                assertFalse("$locale $key contains technical mnemonic wording", value.contains("mnemonic", ignoreCase = true))
                if (locale == "Swedish") assertFalse("$key contains a seed-word calque", value.contains("frö", ignoreCase = true))
                if (locale == "Thai") assertFalse("$key contains an untranslated seed rendering", value.contains("ซีด") || value.contains("ช่วยจำ"))
            }

            for (key in listOf("history_from_date", "history_through_date", "history_search_txid", "history_no_matches", "history_loading_transaction", "toggle_mainnet")) {
                assertNotEquals("$locale is untranslated for $key", english[key], values[key])
            }
        }
    }

    @Test
    fun ukrainianAndUrduWalletCriticalCopyIsClear() {
        val ukrainian = loadStringValues(File("src/main/res/values-uk/strings.xml"))
        val urdu = loadStringValues(File("src/main/res/values-ur/strings.xml"))
        val english = loadStringValues(File(valuesDir, "strings.xml"))

        for ((locale, values) in listOf("Ukrainian" to ukrainian, "Urdu" to urdu)) {
            val recoveryTerm = if (locale == "Ukrainian") "відновлення" else "بحالی"
            val feeTerm = if (locale == "Ukrainian") "мережевої комісії" else "نیٹ ورک فیس"
            val recoveryStrings = listOf(
                "terms_body_custody", "replace_wallet_message", "generate_new_seed", "wrote_seed_down",
                "recovery_seed_label", "mnemonic_paste_label", "mnemonic_paste_placeholder",
                "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic",
            )
            for (key in recoveryStrings) {
                val value = values.getValue(key)
                assertTrue("$locale $key should use recovery phrase wording", value.contains(recoveryTerm, ignoreCase = true))
                assertFalse("$locale $key contains English seed wording", value.contains("seed", ignoreCase = true))
                assertFalse("$locale $key contains English mnemonic wording", value.contains("mnemonic", ignoreCase = true))
                if (locale == "Ukrainian") assertFalse("$key contains technical mnemonic transliteration", value.contains("мнемон", ignoreCase = true))
                if (locale == "Urdu") {
                    assertFalse("$key contains technical seed transliteration", value.contains("سیڈ"))
                    assertFalse("$key contains technical mnemonic transliteration", value.contains("منیمونک"))
                }
            }

            val about = values.getValue("about_disclaimer_nexatrode")
            assertTrue("$locale About copy must identify the rates host", about.contains("rates.nexawal.com"))
            assertTrue("$locale About copy must disclose node IP visibility", about.contains("IP"))
            assertFalse("$locale About copy must say rates are fetched, not tell users to contact the host", about.contains("contact", ignoreCase = true))
            assertTrue("$locale Send Max must say the network fee is deducted", values.getValue("biometric_send_max_subtitle").contains(feeTerm))
            assertTrue(values.getValue("send_max_amount_fmt").contains("%1\$s"))
            assertTrue(values.getValue("total_with_fee_fmt").contains("%1\$s"))

            for (key in listOf("history_from_date", "history_through_date", "history_search_txid", "history_no_matches", "history_loading_transaction", "toggle_mainnet")) {
                assertNotEquals("$locale falls back to English for $key", english[key], values[key])
            }
        }
    }

    @Test
    fun yorubaWalletCriticalCopyIsClear() {
        val yoruba = loadStringValues(File("src/main/res/values-yo/strings.xml"))
        val english = loadStringValues(File(valuesDir, "strings.xml"))
        val recoveryTerm = "ọ̀rọ̀ ìpadàbọ̀"
        val recoveryStrings = listOf(
            "terms_body_custody", "replace_wallet_message", "generate_new_seed", "wrote_seed_down",
            "recovery_seed_label", "recovery_seed_instructions", "mnemonic_paste_label", "mnemonic_paste_placeholder",
            "error_mnemonic_empty", "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic",
        )

        for (key in recoveryStrings) {
            val value = yoruba.getValue(key)
            assertTrue("Yoruba $key should use recovery-word wording", value.contains(recoveryTerm, ignoreCase = true))
            assertFalse("Yoruba $key still says mnemonic", value.contains("mnemonic", ignoreCase = true))
            assertFalse("Yoruba $key still says seed", value.contains("seed", ignoreCase = true))
            assertFalse("Yoruba $key still has a literal seed translation", value.contains("irugbin", ignoreCase = true))
        }

        val about = yoruba.getValue("about_disclaimer_nexatrode")
        assertTrue(about.contains("MIT"))
        assertTrue(about.contains("IP"))
        assertTrue(about.contains("rates.nexawal.com"))
        assertTrue(about.contains("gba oṣùwọ̀n"))
        assertFalse("Yoruba About copy must not ask users to contact the rate host", about.contains("kan si rates.nexawal.com", ignoreCase = true))

        val rates = yoruba.getValue("fiat_help_nexatrode")
        assertTrue(rates.contains("rates.nexawal.com"))
        assertTrue(rates.contains("I2P"))
        assertTrue(rates.contains("àwọn ọ̀rọ̀ ìpadàbọ̀"))
        assertTrue(rates.contains("a kì í fi"))

        assertTrue(yoruba.getValue("biometric_send_max_subtitle").contains("ọya nẹ́tíwọ́ọ̀kì"))
        assertTrue(yoruba.getValue("biometric_send_max_subtitle").contains("owó tí a ti ṣí sílẹ̀"))
        assertTrue(yoruba.getValue("send_max_amount_fmt").contains("%1\$s"))
        assertTrue(yoruba.getValue("total_with_fee_fmt").contains("%1\$s"))
        assertTrue(yoruba.getValue("total_amount_fee").contains("ọya nẹ́tíwọ́ọ̀kì"))
        assertTrue(yoruba.getValue("total_with_fee_fmt").contains("ọya nẹ́tíwọ́ọ̀kì"))
        assertEquals("Àdírẹ́sì olùgbà", yoruba.getValue("destination"))

        for (key in listOf("history_from_date", "history_through_date", "history_search_txid", "history_no_matches", "history_loading_transaction", "toggle_mainnet")) {
            assertNotEquals("Yoruba falls back to English for $key", english[key], yoruba[key])
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
    fun vietnameseAndChineseWalletCriticalCopyIsClear() {
        data class LocaleCopy(
            val locale: String,
            val recoveryTerm: String,
            val feeTerm: String,
            val debitTerm: String,
            val syncTerm: String,
            val confirmationTerm: String,
            val confirmationTitleTerm: String,
        )
        val locales = listOf(
            LocaleCopy("vi", "cụm từ khôi phục", "phí mạng", "số tiền + phí", "đồng bộ", "Số lần xác nhận", "Xác nhận"),
            LocaleCopy("zh-rCN", "恢复短语", "网络手续费", "金额 + 手续费", "同步", "确认次数", "确认数"),
            LocaleCopy("zh-rTW", "復原詞組", "網路手續費", "金額 + 手續費", "同步", "確認次數", "確認次數"),
        )
        val recoveryKeys = listOf(
            "terms_body_custody", "replace_wallet_message", "generate_new_seed", "wrote_seed_down",
            "seed_challenge_intro", "recovery_seed_label", "recovery_seed_instructions",
            "mnemonic_paste_label", "mnemonic_paste_placeholder", "error_mnemonic_empty",
            "error_rescan_no_mnemonic", "storage_missing_mnemonic", "stored_metadata_no_mnemonic",
        )

        for ((locale, recoveryTerm, feeTerm, debitTerm, syncTerm, confirmationTerm, confirmationTitleTerm) in locales) {
            val values = loadStringValues(File("src/main/res/values-$locale/strings.xml"))
            val forbidden = when (locale) {
                "vi" -> listOf("mnemonic", "hạt giống")
                "zh-rCN" -> listOf("助记词", "种子")
                else -> listOf("助記詞", "種子")
            }
            for (key in recoveryKeys) {
                val value = values.getValue(key)
                assertTrue("$locale $key should clearly identify the recovery phrase", value.contains(recoveryTerm, ignoreCase = true))
                for (term in forbidden) assertFalse("$locale $key contains technical or ambiguous wording: $term", value.contains(term, ignoreCase = true))
            }

            val custody = values.getValue("terms_body_custody")
            assertTrue("$locale custody wording should say the user manages the keys", custody.contains(when (locale) {
                "vi" -> "do bạn tự quản lý"
                "zh-rCN" -> "自行管理私钥"
                else -> "自託管介面"
            }))
            assertTrue("$locale Send Max must say the network fee is deducted", values.getValue("biometric_send_max_subtitle").contains(feeTerm, ignoreCase = true))
            assertTrue(values.getValue("total_amount_fee").contains(debitTerm))
            assertTrue(values.getValue("send_max_amount_fmt").contains("%1\$s"))
            if (locale == "zh-rTW") {
                assertTrue(values.getValue("enter_valid_restore_height").contains("還原高度"))
                assertTrue(values.getValue("suggested_height_fetch_failed").contains("快速還原高度"))
            }

            val confirmations = values.getValue("confirmations_fmt")
            assertTrue("$locale confirmation count should have a neutral label", confirmations.contains(confirmationTerm))
            assertTrue(confirmations.contains("%1\$s"))
            assertTrue(values.getValue("label_confirmations").contains(confirmationTitleTerm))

            val hybrid = values.getValue("network_policy_hybrid")
            assertTrue(hybrid.contains("Clearnet") && hybrid.contains("I2P"))
            assertTrue(hybrid.contains(syncTerm))
            assertTrue(values.getValue("network_policy_help").contains("I2P") && values.getValue("network_policy_help").contains(syncTerm))
            assertNotEquals("$locale mainnet label should be localized", "Mainnet", values.getValue("toggle_mainnet"))

            val about = values.getValue("about_disclaimer_nexatrode")
            assertTrue(about.contains("rates.nexawal.com"))
            assertTrue(about.contains("IP"))
            val rates = values.getValue("fiat_help_nexatrode")
            assertTrue(rates.contains("rates.nexawal.com") && rates.contains("I2P"))
            assertTrue(rates.contains(recoveryTerm))
            for (term in forbidden) assertFalse("$locale rates disclosure contains technical recovery wording: $term", rates.contains(term, ignoreCase = true))
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
            assertTrue("duplicate string in ${file.path}", names.add(el.getAttribute("name")))
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

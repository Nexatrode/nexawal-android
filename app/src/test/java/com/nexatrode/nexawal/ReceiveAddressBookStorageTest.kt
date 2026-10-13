package com.nexatrode.nexawal

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReceiveAddressBookStorageTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test fun completeBookRoundTripsAndReadsDoNotRewriteIt() {
        val directory = Files.createTempDirectory("receive-book-test-").toFile()
        try {
            val file = File(directory, "book.json")
            assertNull(ReceiveAddressBookStorage.read(file, json))
            val book = WalletManager.ReceiveSubaddressBook(nextSubaddressIndex = 52,
                entries = listOf(WalletManager.ReceiveSubaddressEntry(subaddressIndex = 51, label = "Shop", createdAtMs = 1)))
            ReceiveAddressBookStorage.write(file, book, json)
            val bytes = file.readBytes()
            assertEquals(book, ReceiveAddressBookStorage.read(file, json))
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(directory.listFiles()!!.none { it.name.endsWith(".tmp") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun corruptOrUnwritableBookThrowsRatherThanIssuingOrReplacingIt() {
        val directory = Files.createTempDirectory("receive-book-test-").toFile()
        try {
            val corrupt = File(directory, "corrupt.json")
            corrupt.writeText("broken existing book")
            assertTrue(runCatching { ReceiveAddressBookStorage.read(corrupt, json) }.isFailure)
            assertEquals("broken existing book", corrupt.readText())
            val blocked = File(directory, "blocked.json").apply { mkdir() }
            File(blocked, "keep").writeText("old data")
            val result = runCatching {
                ReceiveAddressBookStorage.write(blocked, WalletManager.ReceiveSubaddressBook(nextSubaddressIndex = 52), json)
            }
            assertTrue(result.isFailure)
            assertEquals("old data", File(blocked, "keep").readText())
            assertTrue(directory.listFiles()!!.none { it.name.endsWith(".tmp") })
        } finally { directory.deleteRecursively() }
    }
}

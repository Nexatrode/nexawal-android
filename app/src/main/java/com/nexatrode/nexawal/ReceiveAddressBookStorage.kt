package com.nexatrode.nexawal

import com.nexatrode.nexawal.logic.CacheFileIO
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An unreadable book is not an empty book. Never overwrite it on a read. */
internal object ReceiveAddressBookStorage {
    fun read(file: File, json: Json): WalletManager.ReceiveSubaddressBook? =
        CacheFileIO.readTextIfPresent(file)?.let { json.decodeFromString(it) }

    fun write(file: File, book: WalletManager.ReceiveSubaddressBook, json: Json) {
        CacheFileIO.writeAtomically(file, json.encodeToString(book).toByteArray(Charsets.UTF_8))
    }
}

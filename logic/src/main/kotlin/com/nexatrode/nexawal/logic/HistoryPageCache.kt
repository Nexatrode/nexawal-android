package com.nexatrode.nexawal.logic

/** Four pages, independent of the total ledger size. Missing slots mean not loaded, never no history. */
class HistoryPageCache<T>(val pageSize: Int = 50, val capacity: Int = 4) {
    private val pages = LinkedHashMap<Int, List<T>>()
    init { require(pageSize > 0 && capacity > 0) }
    val storedRowCount get() = pages.values.sumOf { it.size }
    fun row(index: Int): T? = if (index < 0) null else pages[index / pageSize]?.getOrNull(index % pageSize)
    fun containsPage(offset: Int) = pages.containsKey(offset / pageSize)
    fun touch(index: Int) { val key = index / pageSize; pages.remove(key)?.let { pages[key] = it } }
    fun insert(offset: Int, rows: List<T>) {
        require(offset >= 0 && offset % pageSize == 0 && rows.size <= pageSize)
        val key = offset / pageSize
        pages.remove(key); pages[key] = rows
        while (pages.size > capacity) pages.remove(pages.keys.first())
    }
    fun clear() = pages.clear()
}

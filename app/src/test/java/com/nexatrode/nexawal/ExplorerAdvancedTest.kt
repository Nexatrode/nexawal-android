package com.nexatrode.nexawal

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExplorerAdvancedTest {
    private val id = "a".repeat(64)
    private fun body() = JSONObject(javaClass.classLoader!!.getResourceAsStream("explorer_advanced_page0.json")!!.bufferedReader().use { it.readText() })
    private fun parse(j: JSONObject, page: Int = 0) = ExplorerObservation.parse(j.toString(),id,1000,page)
    @Test fun sharedFixtureExactAmountsConflictAndPages() {
        val b = body(); val data = parse(b)
        assertEquals("18446744073709551615",data.feePiconero); assertTrue(data.doubleSpendSeen)
        assertEquals(10,data.details!!.inputs.size); assertEquals(10,data.details.outputs.size)
        assertNull(data.details.outputs[0].amount); assertNull(data.details.weight)
        assertTrue(runCatching { parse(b,1) }.isFailure)
        assertTrue(runCatching { ExplorerObservation.parse(b.toString(),id,1000) }.isFailure)
        val d = b.getJSONObject("details")
        val inputs = org.json.JSONArray().put(d.getJSONArray("inputs").getJSONObject(0).put("index",10))
        val outputs = org.json.JSONArray().put(d.getJSONArray("outputs").getJSONObject(0).put("index",10)).put(d.getJSONArray("outputs").getJSONObject(1).put("index",11))
        d.put("page",1).put("inputs",inputs).put("outputs",outputs)
        assertEquals(1,parse(b,1).details!!.inputs.size)
    }
    @Test fun legacyCoinbaseAndMarkedPreviewsAreNotConfusedWithHiddenAmounts() {
        val b = body(); val d = b.getJSONObject("details")
        d.put("version",1)
        for (i in 0 until 10) d.getJSONArray("outputs").getJSONObject(i).put("public_amount_piconero","18446744073709551615")
        assertEquals("18446744073709551615",parse(b).details!!.outputs[0].amount)
        d.put("coinbase",true).put("input_count",1).put("inputs",org.json.JSONArray()); b.put("fee_piconero","0")
        assertTrue(parse(b).details!!.inputs.isEmpty())
        val preview = body(); val details = preview.getJSONObject("details")
        details.getJSONArray("inputs").getJSONObject(0).put("ring_size",200).put("indices_truncated",true)
            .put("absolute_indices",org.json.JSONArray((0 until 128).map { it.toString() }))
        details.put("extra_hex","aa".repeat(2048)).put("extra_truncated",true)
        assertTrue(parse(preview).details!!.inputs[0].truncated)
    }
    @Test fun malformedFieldsRowsAndResponseLimitsFailClosed() {
        for ((key,value) in mapOf("page_count" to 1,"page_size" to 25,"input_count" to 4097,"inputs" to org.json.JSONArray(),"unlock_kind" to "other","extra_hex" to "GG","weight" to -1)) {
            val b = body(); b.getJSONObject("details").put(key,value)
            assertTrue(key,runCatching { parse(b) }.isFailure)
        }
        val b = body(); b.put("double_spend_seen","false")
        assertTrue(runCatching { parse(b) }.isFailure)
        assertTrue(runCatching { ExplorerObservation.parse(" ".repeat(65_537),id,1000,0) }.isFailure)
        val nested = "{\"ignored\":" + "[".repeat(32) + "0" + "]".repeat(32) + "}"
        assertTrue(runCatching { checkExplorerJson(nested,65_536) }.isFailure)
        checkExplorerJson("{\"quoted\":\"[{}]\\\"\",\"ok\":[]}",65_536)
    }
}

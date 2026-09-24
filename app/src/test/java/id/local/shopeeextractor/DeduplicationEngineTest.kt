package id.local.shopeeextractor

import id.local.shopeeextractor.dedupe.DeduplicationEngine
import id.local.shopeeextractor.parser.ParsedTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class DeduplicationEngineTest {
    @Test
    fun skipsScrollOverlapAndReverseScrollDuplicates() {
        val engine = DeduplicationEngine()
        val window1 = listOf("A", "B", "C", "D").map { tx(it) }
        val window2 = listOf("C", "D", "E", "F").map { tx(it) }
        val window3 = listOf("A", "B", "C", "D").map { tx(it) }

        val accepted = engine.acceptVisibleWindow(window1) +
            engine.acceptVisibleWindow(window2) +
            engine.acceptVisibleWindow(window3)

        assertEquals(listOf("A", "B", "C", "D", "E", "F"), accepted.map { it.transactionType })
        assertEquals(6, accepted.size)
    }

    private fun tx(label: String) = ParsedTransaction(
        transactionType = label,
        description = "Desc $label",
        displayDate = "09 September 2026",
        normalizedDate = "2026-09-09",
        displayAmount = "-Rp1.000",
        numericAmount = -1000,
        transactionStatus = "",
        fullBlockText = label,
        contextFingerprint = label,
    )
}

package id.local.shopeeextractor

import id.local.shopeeextractor.parser.AmountParser
import id.local.shopeeextractor.parser.IndonesianDateParser
import id.local.shopeeextractor.parser.RawTransactionBlock
import id.local.shopeeextractor.parser.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParserTest {
    @Test
    fun parsesAmounts() {
        assertEquals(-87378, AmountParser.parse("-Rp87.378"))
        assertEquals(-1250000, AmountParser.parse("-Rp1.250.000"))
        assertEquals(87378, AmountParser.parse("+Rp87.378"))
        assertEquals(257883, AmountParser.parse("Rp257.883"))
    }

    @Test
    fun parsesIndonesianDates() {
        assertEquals("2026-09-24", IndonesianDateParser.normalize("24 September 2026"))
        assertEquals("2026-09-09", IndonesianDateParser.normalize("09 September 2026"))
        assertEquals("2026-09-08", IndonesianDateParser.normalize("8 September 2026"))
    }

    @Test
    fun parsesPaymentWithFailedStatus() {
        val parsed = parse(
            "Pembayaran",
            "Shopee Marketplace",
            "09 September 2026",
            "-Rp257.883",
            "Gagal",
        )
        assertNotNull(parsed)
        assertEquals("Pembayaran", parsed!!.transactionType)
        assertEquals("Shopee Marketplace", parsed.description)
        assertEquals(-257883, parsed.numericAmount)
        assertEquals("Gagal", parsed.transactionStatus)
    }

    @Test
    fun parsesTransferWithoutStatus() {
        val parsed = parse(
            "Kirim ke Bank",
            "Ke BCA 7315249317",
            "09 September 2026",
            "-Rp73.000",
        )
        assertNotNull(parsed)
        assertEquals("Kirim ke Bank", parsed!!.transactionType)
        assertEquals("Ke BCA 7315249317", parsed.description)
        assertEquals(-73000, parsed.numericAmount)
        assertEquals("", parsed.transactionStatus)
    }

    @Test
    fun includesUnknownValidTransactionType() {
        val parsed = parse(
            "Bonus Promosi",
            "Dari Shopee",
            "08 September 2026",
            "+Rp73.000",
        )
        assertNotNull(parsed)
        assertEquals("Bonus Promosi", parsed!!.transactionType)
        assertEquals(73000, parsed.numericAmount)
    }

    private fun parse(vararg lines: String) =
        TransactionParser.parse(RawTransactionBlock(lines.toList(), lines.joinToString("\n")))
}

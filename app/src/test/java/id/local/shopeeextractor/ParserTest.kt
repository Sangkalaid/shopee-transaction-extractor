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
        assertEquals(-87378L, AmountParser.parse("-Rp87.378"))
        assertEquals(-1250000L, AmountParser.parse("-Rp1.250.000"))
        assertEquals(87378L, AmountParser.parse("+Rp87.378"))
        assertEquals(257883L, AmountParser.parse("Rp257.883"))
        assertEquals(50000L, AmountParser.parse("Rp50.000,00"))
        assertEquals(-50000L, AmountParser.parse("Rp -50.000"))
    }

    @Test
    fun parsesIndonesianDates() {
        assertEquals("2026-09-24", IndonesianDateParser.normalize("24 September 2026"))
        assertEquals("2026-09-09", IndonesianDateParser.normalize("09 September 2026"))
        assertEquals("2026-09-08", IndonesianDateParser.normalize("8 September 2026"))
        assertEquals("2026-09-24", IndonesianDateParser.normalize("24 Sep 2026"))
        assertEquals("2026-09-24", IndonesianDateParser.normalize("24 Sep 2026, 14:30"))
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
        assertEquals(-257883L, parsed.numericAmount)
        assertEquals("Gagal", parsed.transactionStatus)
    }

    @Test
    fun parsesPaymentWithSuccessStatus() {
        val parsed = parse(
            "Isi Saldo",
            "Dari Rekening Bank",
            "09 September 2026",
            "+Rp100.000",
            "Berhasil",
        )
        assertNotNull(parsed)
        assertEquals("Isi Saldo", parsed!!.transactionType)
        assertEquals(100000L, parsed.numericAmount)
        assertEquals("Berhasil", parsed.transactionStatus)
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
        assertEquals(-73000L, parsed.numericAmount)
        assertEquals("", parsed.transactionStatus)
    }

    @Test
    fun parsesTransferWithRewardBadge() {
        val parsed = parse(
            "Kirim ke Bank",
            "Ke BCA 7315249317",
            "09 September 2026",
            "-Rp73.000",
            "Ambil Hadiah >",
        )
        assertNotNull(parsed)
        assertEquals("Kirim ke Bank", parsed!!.transactionType)
        assertEquals("Ke BCA 7315249317", parsed.description)
        assertEquals(-73000L, parsed.numericAmount)
        assertEquals("", parsed.transactionStatus)
    }

    @Test
    fun parsesShopeeMarketplaceInvertedLayout() {
        val parsed = parse(
            "Pembayaran",
            "-Rp257.883",
            "Shopee Marketplace",
            "Gagal",
            "09 September 2026",
        )
        assertNotNull(parsed)
        assertEquals("Pembayaran", parsed!!.transactionType)
        assertEquals("Shopee Marketplace", parsed.description)
        assertEquals(-257883L, parsed.numericAmount)
        assertEquals("Gagal", parsed.transactionStatus)
        assertEquals("2026-09-09", parsed.normalizedDate)
    }

    @Test
    fun parsesTransferInvertedLayoutWithNoise() {
        val parsed = parse(
            "Kirim ke Bank",
            "-Rp73.000",
            "Ke BCA 7315249317",
            "Ambil Hadiah >",
            "09 September 2026",
        )
        assertNotNull(parsed)
        assertEquals("Kirim ke Bank", parsed!!.transactionType)
        assertEquals("Ke BCA 7315249317", parsed.description)
        assertEquals(-73000L, parsed.numericAmount)
    }

    @Test
    fun parsesRefundFromShopee() {
        val parsed = parse(
            "Dana Dikembalikan",
            "Dari Shopee",
            "08 September 2026",
            "+Rp73.000",
        )
        assertNotNull(parsed)
        assertEquals("Dana Dikembalikan", parsed!!.transactionType)
        assertEquals("Dari Shopee", parsed.description)
        assertEquals(73000L, parsed.numericAmount)
    }

    @Test
    fun parsesRefundFromShopeeMarketplace() {
        val parsed = parse(
            "Dana Dikembalikan",
            "Dari Shopee Marketplace",
            "07 September 2026",
            "+Rp334.400",
        )
        assertNotNull(parsed)
        assertEquals("Dana Dikembalikan", parsed!!.transactionType)
        assertEquals("Dari Shopee Marketplace", parsed.description)
        assertEquals(334400L, parsed.numericAmount)
    }

    @Test
    fun parsesCombinedSingleLineTypeAndDescription() {
        val parsed = parse(
            "Pembayaran Shopee Marketplace",
            "09 September 2026",
            "-Rp257.883",
            "Gagal",
        )
        assertNotNull(parsed)
        assertEquals("Pembayaran", parsed!!.transactionType)
        assertEquals("Shopee Marketplace", parsed.description)
        assertEquals(-257883L, parsed.numericAmount)
        assertEquals("Gagal", parsed.transactionStatus)
    }

    @Test
    fun parsesCombinedTransferLine() {
        val parsed = parse(
            "Kirim ke Bank Ke BCA 7315249317",
            "09 September 2026",
            "-Rp73.000",
            "Ambil Hadiah >",
        )
        assertNotNull(parsed)
        assertEquals("Kirim ke Bank", parsed!!.transactionType)
        assertEquals("Ke BCA 7315249317", parsed.description)
        assertEquals(-73000L, parsed.numericAmount)
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
        assertEquals(73000L, parsed.numericAmount)
    }

    private fun parse(vararg lines: String) =
        TransactionParser.parse(RawTransactionBlock(lines.toList(), lines.joinToString("\n")))
}

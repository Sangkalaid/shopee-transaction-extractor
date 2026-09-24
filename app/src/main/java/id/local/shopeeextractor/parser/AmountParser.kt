package id.local.shopeeextractor.parser

object AmountParser {
    private val amountRegex = Regex("""^[+\-]?\s*Rp\s*[\d.\s\u00A0]+(,\d{2})?$""", RegexOption.IGNORE_CASE)

    fun isAmount(text: String): Boolean = amountRegex.matches(text.trim())

    fun parse(text: String): Long? {
        val cleaned = text
            .trim()
            .replace("\u00A0", "")
            .replace("\\s".toRegex(), "")
        val sign = when {
            cleaned.startsWith("-") -> -1L
            else -> 1L
        }
        val withoutCents = cleaned.substringBefore(",")
        val digits = withoutCents
            .replace("+", "")
            .replace("-", "")
            .replace("Rp", "", ignoreCase = true)
            .replace(".", "")
        return digits.toLongOrNull()?.let { it * sign }
    }
}

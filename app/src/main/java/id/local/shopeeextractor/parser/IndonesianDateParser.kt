package id.local.shopeeextractor.parser

object IndonesianDateParser {
    private val months = mapOf(
        "januari" to "01",
        "februari" to "02",
        "maret" to "03",
        "april" to "04",
        "mei" to "05",
        "juni" to "06",
        "juli" to "07",
        "agustus" to "08",
        "september" to "09",
        "oktober" to "10",
        "november" to "11",
        "desember" to "12",
        "jan" to "01",
        "feb" to "02",
        "mar" to "03",
        "apr" to "04",
        "jun" to "06",
        "jul" to "07",
        "agu" to "08",
        "agt" to "08",
        "agst" to "08",
        "sep" to "09",
        "okt" to "10",
        "nov" to "11",
        "des" to "12",
    )
    private val dateRegex = Regex("""^(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})(?:[,\s]+.*)?$""")

    fun isDate(text: String): Boolean = normalize(text) != null

    fun normalize(text: String): String? {
        val match = dateRegex.matchEntire(text.trim()) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null
        if (day !in 1..31) return null
        val month = months[match.groupValues[2].lowercase()] ?: return null
        val year = match.groupValues[3]
        return "$year-$month-${day.toString().padStart(2, '0')}"
    }
}

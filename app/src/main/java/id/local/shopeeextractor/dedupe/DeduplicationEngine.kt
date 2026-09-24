package id.local.shopeeextractor.dedupe

import id.local.shopeeextractor.parser.ParsedTransaction
import id.local.shopeeextractor.parser.TransactionParser

class DeduplicationEngine {
    private val acceptedKeys = linkedSetOf<String>()
    private var previousWindow: List<String> = emptyList()
    var duplicateSkippedCount: Int = 0
        private set

    fun acceptVisibleWindow(items: List<ParsedTransaction>): List<ParsedTransaction> {
        val result = mutableListOf<ParsedTransaction>()
        val currentKeys = items.map { stableWindowKey(it) }

        items.forEachIndexed { index, item ->
            val key = currentKeys[index]
            if (acceptedKeys.contains(key) || isOverlapFromPreviousWindow(key, index, currentKeys)) {
                duplicateSkippedCount += 1
            } else {
                acceptedKeys += key
                result += item
            }
        }

        previousWindow = currentKeys
        return result
    }

    fun reset() {
        acceptedKeys.clear()
        previousWindow = emptyList()
        duplicateSkippedCount = 0
    }

    private fun isOverlapFromPreviousWindow(
        key: String,
        index: Int,
        currentWindow: List<String>,
    ): Boolean {
        if (!previousWindow.contains(key)) return false
        val remainingCurrent = currentWindow.drop(index)
        val previousStart = previousWindow.indexOf(key)
        val remainingPrevious = previousWindow.drop(previousStart)
        return remainingCurrent.zip(remainingPrevious).take(2).all { it.first == it.second }
    }

    private fun stableWindowKey(item: ParsedTransaction): String =
        TransactionParser.sha256(
            listOf(
                item.transactionType,
                item.description,
                item.displayDate,
                item.displayAmount,
                item.transactionStatus,
                item.fullBlockText,
            ).joinToString("|")
        )
}

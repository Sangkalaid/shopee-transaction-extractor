package id.local.shopeeextractor.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import id.local.shopeeextractor.parser.RawTransactionBlock

object AccessibilityNodeReader {
    private val dateRegex = Regex("""\d{1,2}\s+[A-Za-z]+\s+\d{4}""", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex("""[+\-]?\s*Rp\s*[+\-]?\s*[\d.\s\u00A0]+(,\d{2})?""", RegexOption.IGNORE_CASE)

    fun extractCandidateBlocks(root: AccessibilityNodeInfo?): List<RawTransactionBlock> {
        if (root == null) return emptyList()
        val containers = mutableListOf<AccessibilityNodeInfo>()
        collectContainers(root, containers)

        return containers
            .mapIndexedNotNull { index, node ->
                val texts = collectText(node)
                    .flatMap { it.split("\r\n", "\n", "\r") }
                    .map { it.replace('\u00A0', ' ').trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                val full = texts.joinToString("\n")
                if (!dateRegex.containsMatchIn(full) || !amountRegex.containsMatchIn(full)) {
                    null
                } else {
                    val bounds = Rect().also { node.getBoundsInScreen(it) }
                    RawTransactionBlock(
                        lines = texts,
                        fullText = full,
                        hierarchyHint = node.className?.toString().orEmpty(),
                        boundsHint = bounds.flattenToString(),
                        visibleIndex = index,
                    )
                }
            }
            .distinctBy { it.fullText + it.boundsHint }
    }

    private fun collectContainers(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val text = collectText(node).joinToString("\n")
        if (dateRegex.containsMatchIn(text) && amountRegex.containsMatchIn(text)) {
            var hasChildWithBoth = false
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val childText = collectText(child).joinToString("\n")
                if (dateRegex.containsMatchIn(childText) && amountRegex.containsMatchIn(childText)) {
                    hasChildWithBoth = true
                    collectContainers(child, out)
                }
            }
            if (!hasChildWithBoth) {
                out += node
            }
            return
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectContainers(it, out) }
        }
    }

    private fun collectText(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        fun walk(current: AccessibilityNodeInfo?) {
            if (current == null) return
            current.text?.toString()?.takeIf { it.isNotBlank() }?.let { result += it }
            current.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result += it }
            for (i in 0 until current.childCount) {
                walk(current.getChild(i))
            }
        }
        walk(node)
        return result
    }
}

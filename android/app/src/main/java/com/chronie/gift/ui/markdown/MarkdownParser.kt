package com.chronie.gift.ui.markdown

sealed interface MarkdownNode

class Paragraph(val children: List<MarkdownNode>) : MarkdownNode

class Heading(val level: Int, val children: List<MarkdownNode>) : MarkdownNode

class TextNode(val text: String, val style: TextStyle = TextStyle.NORMAL) : MarkdownNode

enum class TextStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC,
    CODE,
    STRIKETHROUGH,
    UNDERLINE
}

class Link(val url: String, val children: List<MarkdownNode>) : MarkdownNode

class Image(val url: String, val altText: String) : MarkdownNode

class Audio(val url: String) : MarkdownNode

class Video(val url: String) : MarkdownNode

class ListItem(val children: List<MarkdownNode>, val checked: Boolean? = null) : MarkdownNode

class UnorderedList(val items: List<ListItem>) : MarkdownNode

class OrderedList(val items: List<ListItem>, val startIndex: Int = 1) : MarkdownNode

class CodeBlock(val code: String, val language: String = "") : MarkdownNode

class Blockquote(val children: List<MarkdownNode>) : MarkdownNode

object HorizontalRule : MarkdownNode

class Table(val headers: List<MarkdownNode>, val rows: List<List<MarkdownNode>>) : MarkdownNode

class MathFormula(val formula: String, val isBlock: Boolean = false) : MarkdownNode

class ChemicalFormula(val formula: String) : MarkdownNode

class MarkdownParser {

    fun parse(markdown: String): List<MarkdownNode> {
        if (markdown.isEmpty()) {
            return emptyList()
        }

        val lines = markdown.lines()
        return parseLines(lines)
    }

    private fun parseLines(lines: List<String>): List<MarkdownNode> {
        val nodes = ArrayList<MarkdownNode>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.isEmpty() -> {
                    i++
                }
                line.startsWith("#") -> {
                    val level = minOf(line.takeWhile { it == '#' }.length, 6)
                    val text = line.substring(level).trim()
                    if (text.isNotEmpty()) {
                        nodes.add(Heading(level, parseInline(text)))
                    }
                    i++
                }
                line.matches(Regex("^-{3,}$")) || line.matches(Regex("^\\*{3,}$")) || line.matches(Regex("^_{3,}$")) -> {
                    nodes.add(HorizontalRule)
                    i++
                }
                line.startsWith("```") -> {
                    val endIndex = findCodeBlockEnd(lines, i + 1)
                    val code = lines.subList(i + 1, endIndex).joinToString("\n")
                    val lang = line.substring(3).trim()
                    nodes.add(CodeBlock(code, lang))
                    i = endIndex + 1
                }
                line.startsWith("$$") -> {
                    val endIndex = findMathBlockEnd(lines, i + 1)
                    val formula = lines.subList(i + 1, endIndex).joinToString("\n").trim()
                    if (formula.isNotEmpty()) {
                        nodes.add(MathFormula(formula, isBlock = true))
                    }
                    i = endIndex + 1
                }
                line.startsWith(">") -> {
                    val endIndex = findBlockquoteEnd(lines, i)
                    val quoteLines = lines.subList(i, endIndex)
                    val quoteText = quoteLines.joinToString("\n") { it.removePrefix(">").trim() }
                    nodes.add(Blockquote(parse(quoteText)))
                    i = endIndex
                }
                line.matches(Regex("^\\d+\\.\\s")) -> {
                    val (list, endIndex) = parseOrderedList(lines, i)
                    nodes.add(list)
                    i = endIndex
                }
                line.matches(Regex("^[*+-]\\s")) -> {
                    val (list, endIndex) = parseUnorderedList(lines, i)
                    nodes.add(list)
                    i = endIndex
                }
                line.matches(Regex("^\\[[ xX]]\\s")) -> {
                    val (list, endIndex) = parseTaskList(lines, i)
                    nodes.add(list)
                    i = endIndex
                }
                line.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().startsWith("|") -> {
                    val (table, endIndex) = parseTable(lines, i)
                    nodes.add(table)
                    i = endIndex
                }
                line.startsWith("<audio") -> {
                    val src = extractSrc(line)
                    if (src != null) nodes.add(Audio(src))
                    i++
                }
                line.startsWith("<video") -> {
                    val src = extractSrc(line)
                    if (src != null) nodes.add(Video(src))
                    i++
                }
                else -> {
                    val endIndex = findParagraphEnd(lines, i)
                    val paraText = lines.subList(i, endIndex).joinToString("\n")
                    nodes.add(Paragraph(parseInline(paraText)))
                    i = endIndex
                }
            }
        }

        return nodes
    }

    private fun extractSrc(line: String): String? {
        val match = Regex("src=['\"]([^'\"]+)['\"]").find(line)
        return match?.groupValues?.get(1)
    }

    private fun findCodeBlockEnd(lines: List<String>, start: Int): Int {
        for (i in start until lines.size) {
            if (lines[i].trim().startsWith("```")) {
                return i
            }
        }
        return lines.size
    }

    private fun findMathBlockEnd(lines: List<String>, start: Int): Int {
        for (i in start until lines.size) {
            if (lines[i].trim().startsWith("$$")) {
                return i
            }
        }
        return lines.size
    }

    private fun findBlockquoteEnd(lines: List<String>, start: Int): Int {
        var i = start
        while (i < lines.size && lines[i].trim().startsWith(">")) {
            i++
        }
        return i
    }

    private fun findParagraphEnd(lines: List<String>, start: Int): Int {
        var i = start
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            if (line.startsWith("#")) break
            if (line.startsWith(">")) break
            if (line.startsWith("```")) break
            if (line.startsWith("$$")) break
            if (line.matches(Regex("^\\d+\\.\\s"))) break
            if (line.matches(Regex("^[*+-]\\s"))) break
            if (line.matches(Regex("^\\[[ xX]]\\s"))) break
            if (line.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().startsWith("|")) break
            i++
        }
        return i
    }

    private fun parseOrderedList(lines: List<String>, start: Int): Pair<OrderedList, Int> {
        val items = ArrayList<ListItem>()
        var i = start

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            if (!line.matches(Regex("^\\d+\\.\\s"))) break

            val content = line.substring(line.indexOf(".") + 1).trim()
            items.add(ListItem(parseInline(content)))
            i++
        }

        return OrderedList(items) to i
    }

    private fun parseUnorderedList(lines: List<String>, start: Int): Pair<UnorderedList, Int> {
        val items = ArrayList<ListItem>()
        var i = start

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            if (!line.matches(Regex("^[*+-]\\s"))) break

            val content = line.substring(1).trim()
            items.add(ListItem(parseInline(content)))
            i++
        }

        return UnorderedList(items) to i
    }

    private fun parseTaskList(lines: List<String>, start: Int): Pair<UnorderedList, Int> {
        val items = ArrayList<ListItem>()
        var i = start

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            if (!line.matches(Regex("^\\[[ xX]]\\s"))) break

            val checked = line[1] == 'x' || line[1] == 'X'
            val content = line.substring(3).trim()
            items.add(ListItem(parseInline(content), checked))
            i++
        }

        return UnorderedList(items) to i
    }

    private fun parseTable(lines: List<String>, start: Int): Pair<Table, Int> {
        val tableLines = ArrayList<String>()
        tableLines.add(lines[start])
        var i = start + 1

        if (i < lines.size) {
            tableLines.add(lines[i])
            i++
        }

        while (i < lines.size && lines[i].trim().startsWith("|")) {
            tableLines.add(lines[i])
            i++
        }

        val headers = parseTableRow(tableLines[0])
        val rows = tableLines.drop(2).map { parseTableRow(it) }

        return Table(headers, rows) to i
    }

    private fun parseTableRow(line: String): List<MarkdownNode> {
        return line.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parseInline(it) }
            .map { if (it.size == 1) it[0] else Paragraph(it) }
            .toList()
    }

    private fun parseInline(text: String): List<MarkdownNode> {
        val nodes = ArrayList<MarkdownNode>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val handled = parseInlineElement(remaining, nodes)
            if (handled == 0) {
                nodes.add(TextNode(remaining))
                break
            }
            remaining = remaining.substring(handled)
        }

        return nodes
    }

    private fun parseInlineElement(text: String, nodes: ArrayList<MarkdownNode>): Int {
        when {
            text.startsWith("***") -> {
                val end = text.indexOf("***", 3)
                if (end != -1) {
                    val content = text.substring(3, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.BOLD_ITALIC))
                    return end + 3
                }
            }
            text.startsWith("___") -> {
                val end = text.indexOf("___", 3)
                if (end != -1) {
                    val content = text.substring(3, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.BOLD_ITALIC))
                    return end + 3
                }
            }
            text.startsWith("**") -> {
                val end = text.indexOf("**", 2)
                if (end != -1) {
                    val content = text.substring(2, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.BOLD))
                    return end + 2
                }
            }
            text.startsWith("__") -> {
                val end = text.indexOf("__", 2)
                if (end != -1) {
                    val content = text.substring(2, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.UNDERLINE))
                    return end + 2
                }
            }
            text.startsWith("*") && text.length > 1 && text[1] != '*' -> {
                val end = text.indexOf('*', 1)
                if (end != -1) {
                    val content = text.substring(1, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.ITALIC))
                    return end + 1
                }
            }
            text.startsWith("_") && text.length > 1 && text[1] != '_' -> {
                val end = text.indexOf('_', 1)
                if (end != -1) {
                    val content = text.substring(1, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.ITALIC))
                    return end + 1
                }
            }
            text.startsWith("~~") -> {
                val end = text.indexOf("~~", 2)
                if (end != -1) {
                    val content = text.substring(2, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.STRIKETHROUGH))
                    return end + 2
                }
            }
            text.startsWith("`") -> {
                val end = text.indexOf('`', 1)
                if (end != -1) {
                    val content = text.substring(1, end).trim()
                    if (content.isNotEmpty()) nodes.add(TextNode(content, TextStyle.CODE))
                    return end + 1
                }
            }
            text.startsWith("![") -> {
                val endAlt = text.indexOf(']', 2)
                if (endAlt != -1 && text.length > endAlt + 1 && text[endAlt + 1] == '(') {
                    val endUrl = text.indexOf(')', endAlt + 2)
                    if (endUrl != -1) {
                        val alt = text.substring(2, endAlt)
                        val urlWithTitle = text.substring(endAlt + 2, endUrl).trim()
                        val url = if (urlWithTitle.contains('"')) {
                            urlWithTitle.substring(0, urlWithTitle.indexOf('"')).trim()
                        } else {
                            urlWithTitle
                        }
                        nodes.add(Image(url, alt))
                        return endUrl + 1
                    }
                }
            }
            text.startsWith("[") -> {
                val endText = text.indexOf(']', 1)
                if (endText != -1 && text.length > endText + 1 && text[endText + 1] == '(') {
                    val endUrl = text.indexOf(')', endText + 2)
                    if (endUrl != -1) {
                        val linkText = text.substring(1, endText)
                        val urlWithTitle = text.substring(endText + 2, endUrl).trim()
                        val url = if (urlWithTitle.contains('"')) {
                            urlWithTitle.substring(0, urlWithTitle.indexOf('"')).trim()
                        } else {
                            urlWithTitle
                        }
                        nodes.add(Link(url, parseInline(linkText)))
                        return endUrl + 1
                    }
                }
            }
            text.startsWith("$") && text.length > 1 -> {
                val end = text.indexOf('$', 1)
                if (end != -1) {
                    val formula = text.substring(1, end).trim()
                    if (formula.isNotEmpty() && !formula.matches(Regex("^\\d+(\\.\\d+)?$"))) {
                        nodes.add(MathFormula(formula, isBlock = false))
                        return end + 1
                    }
                }
            }
            text.startsWith("\\") && text.length > 1 -> {
                nodes.add(TextNode(text[1].toString()))
                return 2
            }
        }

        val nextSpecial = text.indexOfAny(charArrayOf('[', '*', '_', '`', '~', '$', '\\'))
        if (nextSpecial == -1) {
            nodes.add(TextNode(text))
            return text.length
        }
        nodes.add(TextNode(text.substring(0, nextSpecial)))
        return nextSpecial
    }
}
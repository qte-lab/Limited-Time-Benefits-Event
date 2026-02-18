package com.chronie.gift.ui.markdown

import kotlinx.serialization.Serializable

// Base interface for Markdown AST nodes
sealed interface MarkdownNode

// Paragraph node
class Paragraph(val children: List<MarkdownNode>) : MarkdownNode

// Heading node
class Heading(val level: Int, val children: List<MarkdownNode>) : MarkdownNode

// Text node
class TextNode(val text: String, val style: TextStyle = TextStyle.NORMAL) : MarkdownNode

// Text style enum
enum class TextStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC,
    CODE,
    STRIKETHROUGH,
    UNDERLINE
}

// Link node
class Link(val url: String, val children: List<MarkdownNode>) : MarkdownNode

// Image node
class Image(val url: String, val altText: String) : MarkdownNode

// Audio node
class Audio(val url: String) : MarkdownNode

// Video node
class Video(val url: String) : MarkdownNode

// List item node
class ListItem(val children: List<MarkdownNode>, val checked: Boolean? = null) : MarkdownNode

// Unordered list node
class UnorderedList(val items: List<ListItem>) : MarkdownNode

// Ordered list node
class OrderedList(val items: List<ListItem>, val startIndex: Int = 1) : MarkdownNode

// Code block node
class CodeBlock(val code: String, val language: String = "") : MarkdownNode

// Blockquote node
class Blockquote(val children: List<MarkdownNode>) : MarkdownNode

// Horizontal rule node
object HorizontalRule : MarkdownNode

// Table node
class Table(val headers: List<MarkdownNode>, val rows: List<List<MarkdownNode>>) : MarkdownNode

// Math formula node (inline: \( ... \) or $ ... $, block: \[ ... \] or $$ ... $$)
class MathFormula(val formula: String, val isBlock: Boolean = false) : MarkdownNode

// Chemical formula node
class ChemicalFormula(val formula: String) : MarkdownNode

// Markdown parser class
class MarkdownParser {
    // Parse full Markdown document (block-level elements)
    fun parse(markdown: String): List<MarkdownNode> {
        val lines = markdown.lines()
        val nodes = mutableListOf<MarkdownNode>()
        var currentLineIndex = 0

        while (currentLineIndex < lines.size) {
            val line = lines[currentLineIndex].trim()
            
            when {
                // Empty line skip
                line.isEmpty() -> {
                    currentLineIndex++
                }
                // Parse heading
                line.startsWith("#") -> {
                    val headingLevel = line.takeWhile { it == '#' }.length
                    val headingText = line.drop(headingLevel).trim()
                    nodes.add(Heading(headingLevel, parseInline(headingText)))
                    currentLineIndex++
                }
                // Parse horizontal rule
                line.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> {
                    nodes.add(HorizontalRule)
                    currentLineIndex++
                }
                // Parse audio and video tags
                line.trim().startsWith("<audio") || line.trim().startsWith("<video") -> {
                    if (line.contains("src=")) {
                        val srcMatch = Regex("""src=['"]([^'"]+)['"]""").find(line)
                        if (srcMatch != null) {
                            val src = srcMatch.groupValues[1]
                            if (line.startsWith("<audio")) {
                                nodes.add(Audio(src))
                            } else {
                                nodes.add(Video(src))
                            }
                        }
                    }
                    currentLineIndex++
                }
                // Parse code block (starts with ```)
                line.startsWith("```") -> {
                    val language = line.drop(3).trim()
                    val codeLines = mutableListOf<String>()
                    currentLineIndex++
                    
                    while (currentLineIndex < lines.size && !lines[currentLineIndex].trim().startsWith("```")) {
                        codeLines.add(lines[currentLineIndex])
                        currentLineIndex++
                    }
                    
                    if (currentLineIndex < lines.size) { // Skip end ```
                        currentLineIndex++
                    }
                    
                    nodes.add(CodeBlock(codeLines.joinToString("\n"), language))
                }
                // Parse blockquote (starts with >)
                line.startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (currentLineIndex < lines.size && lines[currentLineIndex].startsWith(">")) {
                        quoteLines.add(lines[currentLineIndex].drop(1).trim())
                        currentLineIndex++
                    }
                    nodes.add(Blockquote(parse(quoteLines.joinToString("\n"))))
                }
                // Parse list item (unordered list, ordered list, task list)
                line.matches(Regex("^([*+-]|\\d+\\.)\\s+")) || line.matches(Regex("^\\[[ x]]\\s+")) -> {
                    val isOrdered = line.matches(Regex("^\\d+\\.\\s+"))
                    val listItems = mutableListOf<ListItem>()
                    while (currentLineIndex < lines.size && (lines[currentLineIndex].matches(Regex("^([*+-]|\\d+\\.)\\s+")) || lines[currentLineIndex].trim().matches(Regex("^\\[[ x]]\\s+")))) {
                        val itemLine = lines[currentLineIndex].trim()
                        val checked = when {
                            itemLine.startsWith("[x]") -> true
                            itemLine.startsWith("[X]") -> true
                            itemLine.startsWith("[ ]") -> false
                            itemLine.startsWith("[-]") -> false
                            else -> null
                        }
                        // Extract list item content (remove list marker)
                        val content = if (checked != null) {
                            itemLine.drop(3).trim() // Remove [x]、[X]、[ ] or [-] and spaces
                        } else {
                            itemLine.dropWhile { it in setOf('*', '+', '-', ' ', '.') || it.isDigit() }.trim()
                        }
                        listItems.add(ListItem(parseInline(content), checked))
                        currentLineIndex++
                    }
                    if (isOrdered) {
                        nodes.add(OrderedList(listItems))
                    } else {
                        nodes.add(UnorderedList(listItems))
                    }
                }
                // Parse table (starts with | and has a separator row)
                line.matches(Regex("^\\|.*\\|$")) && currentLineIndex + 1 < lines.size && lines[currentLineIndex + 1].matches(Regex("^\\|[-: ]+\\|.*$")) -> {
                    val tableLines = mutableListOf<String>()
                    tableLines.add(line)
                    currentLineIndex++
                    
                    // Add separator row
                    tableLines.add(lines[currentLineIndex])
                    currentLineIndex++
                    
                    // Add all rows
                    while (currentLineIndex < lines.size && lines[currentLineIndex].matches(Regex("^\\|.*\\|$"))) {
                        tableLines.add(lines[currentLineIndex])
                        currentLineIndex++
                    }
                    
                    // Parse table
                    nodes.add(parseTable(tableLines))
                }
                // Parse paragraph (default)
                else -> {
                    // Collect consecutive paragraph lines
                    val paragraphLines = mutableListOf<String>()
                    while (currentLineIndex < lines.size && lines[currentLineIndex].trim().isNotEmpty() && 
                           !lines[currentLineIndex].trim().startsWith("#") &&
                           !lines[currentLineIndex].trim().startsWith(">") &&
                           !lines[currentLineIndex].trim().matches(Regex("^([*+-]|\\d+\\.)\\s+")) &&
                           !lines[currentLineIndex].trim().startsWith("```") &&
                           !lines[currentLineIndex].trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) &&
                           !lines[currentLineIndex].trim().matches(Regex("^\\|.*\\|$")) &&
                           !lines[currentLineIndex].trim().matches(Regex("^\\|[-: ]+\\|.*$"))) {
                        paragraphLines.add(lines[currentLineIndex].trim())
                        currentLineIndex++
                    }
                    // Check if last line ends with a backslash, if so, keep the line break
                    val joinedText = paragraphLines.joinToString("\n")
                    nodes.add(Paragraph(parseInline(joinedText)))
                }
            }
        }
        return nodes
    }
    
    // Parse inline elements (e.g., links, bold, italic, etc.)
    private fun parseInline(text: String): List<MarkdownNode> {
        val nodes = mutableListOf<MarkdownNode>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            when {
                // Parse inline math formula \( ... \)
                remaining.startsWith("\\(") -> {
                    val end = remaining.indexOf("\\)", 2)
                    if (end != -1) {
                        val formula = remaining.substring(2, end)
                        nodes.add(MathFormula(formula.trim(), isBlock = false))
                        remaining = remaining.substring(end + 2)
                    } else {
                        nodes.add(TextNode("\\("))
                        remaining = remaining.substring(2)
                    }
                }
                // Parse block math formula \[ ... \]
                remaining.startsWith("\\[") -> {
                    val end = remaining.indexOf("\\]", 2)
                    if (end != -1) {
                        val formula = remaining.substring(2, end)
                        nodes.add(MathFormula(formula.trim(), isBlock = true))
                        remaining = remaining.substring(end + 2)
                    } else {
                        nodes.add(TextNode("\\["))
                        remaining = remaining.substring(2)
                    }
                }
                // Parse block math formula $$...$$
                remaining.startsWith("$$") -> {
                    val end = remaining.indexOf("$$", 2)
                    if (end != -1) {
                        val formula = remaining.substring(2, end)
                        nodes.add(MathFormula(formula.trim(), isBlock = true))
                        remaining = remaining.substring(end + 2)
                    } else {
                        nodes.add(TextNode("$$"))
                        remaining = remaining.substring(2)
                    }
                }
                // Parse inline math formula $...$ (must have content between $ and not be a number)
                remaining.startsWith("$") -> {
                    val end = remaining.indexOf('$', 1)
                    if (end != -1 && end > 1) {
                        val formula = remaining.substring(1, end).trim()
                        // Only treat as formula if it looks like math (contains letters, operators, etc.)
                        // Skip if it's just a number or empty
                        if (formula.isNotEmpty() && !formula.matches(Regex("^\\d+(\\.\\d+)?$"))) {
                            nodes.add(MathFormula(formula, isBlock = false))
                            remaining = remaining.substring(end + 1)
                        } else {
                            // Treat as plain text
                            nodes.add(TextNode(remaining.substring(0, end + 1)))
                            remaining = remaining.substring(end + 1)
                        }
                    } else {
                        nodes.add(TextNode("$"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse image ![alt](url) or ![alt](url "title")
                remaining.startsWith("!") && remaining.length > 1 && remaining[1] == '[' -> {
                    val endAlt = remaining.indexOf(']', 2)
                    val startUrl = remaining.indexOf('(', endAlt)
                    val endUrl = remaining.indexOf(')', startUrl)
                    if (endAlt != -1 && startUrl == endAlt + 1 && endUrl != -1) {
                        val altText = remaining.substring(2, endAlt)
                        val urlWithTitle = remaining.substring(startUrl + 1, endUrl).trim()
                        
                        // Separate URL and title
                        val url: String
                        if (urlWithTitle.contains('"')) {
                            // Case with title: url "title"
                            val firstQuoteIndex = urlWithTitle.indexOf('"')
                            url = urlWithTitle.substring(0, firstQuoteIndex).trim()
                            // Title is currently ignored since Image class only stores url and altText
                        } else {
                            // Case without title
                            url = urlWithTitle
                        }
                        
                        nodes.add(Image(url, altText))
                        remaining = remaining.substring(endUrl + 1)
                    } else {
                        // Case without matching closing parenthesis, treat as plain text
                        nodes.add(TextNode("!"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse link [text](url) or [text](url "title")
                remaining.startsWith("[") -> {
                    val endText = remaining.indexOf(']', 1)
                    val startUrl = remaining.indexOf('(', endText)
                    val endUrl = remaining.indexOf(')', startUrl)
                    if (endText != -1 && startUrl == endText + 1 && endUrl != -1) {
                        val linkText = remaining.substring(1, endText)
                        val urlWithTitle = remaining.substring(startUrl + 1, endUrl).trim()
                        
                        // Separate URL and title
                        val url: String
                        if (urlWithTitle.contains('"')) {
                            // Case with title: url "title"
                            val firstQuoteIndex = urlWithTitle.indexOf('"')
                            url = urlWithTitle.substring(0, firstQuoteIndex).trim()
                            // Title is currently ignored since Link class only stores url and children
                        } else {
                            // Case without title
                            url = urlWithTitle
                        }
                        
                        nodes.add(Link(url, parseInline(linkText)))
                        remaining = remaining.substring(endUrl + 1)
                    } else {
                        // Case without matching closing parenthesis, treat as plain text
                        nodes.add(TextNode("["))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse bold italic ***text***
                remaining.startsWith("***") -> {
                    val end = remaining.indexOf("***", 3)
                    if (end != -1) {
                        val boldItalicText = remaining.substring(3, end)
                        nodes.add(TextNode(boldItalicText, TextStyle.BOLD_ITALIC))
                        remaining = remaining.substring(end + 3)
                    } else {
                        // Case without matching closing delimiter, treat as plain text
                        nodes.add(TextNode("*"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse bold italic ___text___
                remaining.startsWith("___") -> {
                    val end = remaining.indexOf("___", 3)
                    if (end != -1) {
                        val boldItalicText = remaining.substring(3, end)
                        nodes.add(TextNode(boldItalicText, TextStyle.BOLD_ITALIC))
                        remaining = remaining.substring(end + 3)
                    } else {
                        // Case without matching closing delimiter, treat as plain text
                        nodes.add(TextNode("_"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse bold **text**
                remaining.startsWith("**") -> {
                    val end = remaining.indexOf("**", 2)
                    if (end != -1) {
                        val boldText = remaining.substring(2, end)
                        nodes.add(TextNode(boldText, TextStyle.BOLD))
                        remaining = remaining.substring(end + 2)
                    } else {
                        // Case without matching closing delimiter, treat as plain text
                        nodes.add(TextNode("*"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse italic *text*
                remaining.startsWith("*") && !remaining.startsWith("**") -> {
                    val end = remaining.indexOf('*', 1)
                    if (end != -1) {
                        val italicText = remaining.substring(1, end)
                        nodes.add(TextNode(italicText, TextStyle.ITALIC))
                        remaining = remaining.substring(end + 1)
                    } else {
                        // Case without matching closing delimiter, treat as plain text     
                        nodes.add(TextNode("*"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse strikethrough ~~text~~
                remaining.startsWith("~~") -> {
                    val end = remaining.indexOf("~~", 2)
                    if (end != -1) {
                        val strikethroughText = remaining.substring(2, end)
                        nodes.add(TextNode(strikethroughText, TextStyle.STRIKETHROUGH))
                        remaining = remaining.substring(end + 2)
                    } else {
                        // Case without matching closing delimiter, treat as plain text
                        nodes.add(TextNode("~"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse underline __text__
                remaining.startsWith("__") -> {
                    val end = remaining.indexOf("__", 2)
                    if (end != -1) {
                        val underlineText = remaining.substring(2, end)
                        nodes.add(TextNode(underlineText, TextStyle.UNDERLINE))
                        remaining = remaining.substring(end + 2)
                    } else {
                        // Case without matching closing delimiter, treat as plain text
                        nodes.add(TextNode("_"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse inline code `code`
                remaining.startsWith("`") -> {
                    val end = remaining.indexOf('`', 1)
                    if (end != -1) {
                        val codeText = remaining.substring(1, end)
                        nodes.add(TextNode(codeText, TextStyle.CODE))
                        remaining = remaining.substring(end + 1)
                    } else {
                        // Case without matching closing delimiter, treat as plain text
                        nodes.add(TextNode("`"))
                        remaining = remaining.substring(1)
                    }
                }
                // Parse escape character \char
                remaining.startsWith("\\") && remaining.length > 1 -> {
                    // Escape next character, treat as plain text
                    val escapedChar = remaining[1]
                    nodes.add(TextNode(escapedChar.toString()))
                    remaining = remaining.substring(2)
                }
                // Parse plain text, collect until next special character
                else -> {
                    val nextSpecial = remaining.indexOfAny(charArrayOf('[', '*', '`', '<', '$', '\\'))
                    if (nextSpecial == -1) {
                        nodes.add(TextNode(remaining))
                        remaining = ""
                    } else {
                        nodes.add(TextNode(remaining.substring(0, nextSpecial)))
                        remaining = remaining.substring(nextSpecial)
                    }
                }
            }
        }
        return nodes
    }
        
        // Parse table
    private fun parseTable(tableLines: List<String>): Table {
        if (tableLines.size < 2) {
            // Case with less than 2 lines, treat as plain text
            return Table(emptyList(), emptyList())
        }
        
        // Parse table header
        val headerLine = tableLines[0]
        val headers = headerLine.split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parseInline(it) }
            .map { if (it.size == 1) it[0] else Paragraph(it) }
        
        // Parse table rows (skip header and separator line)
        val rows = tableLines.drop(2)
            .map { line ->
                line.split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { parseInline(it) }
                    .map { if (it.size == 1) it[0] else Paragraph(it) }
            }
        
        return Table(headers, rows)
    }
}

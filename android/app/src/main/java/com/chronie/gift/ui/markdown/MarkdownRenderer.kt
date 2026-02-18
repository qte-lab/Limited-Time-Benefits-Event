package com.chronie.gift.ui.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.chronie.gift.R
import com.chronie.gift.ui.markdown.LatinModernMathFontFamily

// Markdown renderer component
@Composable
fun MarkdownRenderer(markdown: String) {
    val parser = remember { MarkdownParser() }
    val nodes by remember(markdown) { mutableStateOf(parser.parse(markdown)) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        nodes.forEach { node ->
            item {
                RenderNode(
                    node = node,
                    onLinkClick = { url -> uriHandler.openUri(url) },
                    context = context
                )
            }
        }
    }
}

// Render a single Markdown node
@Composable
private fun RenderNode(
    node: MarkdownNode,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    when (node) {
        is Paragraph -> RenderParagraph(node, onLinkClick, context)
        is Heading -> RenderHeading(node, onLinkClick, context)
        is TextNode -> RenderTextNode(node)
        is Link -> RenderLink(node, onLinkClick, context)
        is Image -> RenderImage(node, context)
        is Audio -> RenderAudio(node, context)
        is Video -> RenderVideo(node, context)
        is UnorderedList -> RenderUnorderedList(node, onLinkClick, context)
        is OrderedList -> RenderOrderedList(node, onLinkClick, context)
        is CodeBlock -> RenderCodeBlock(node)
        is Blockquote -> RenderBlockquote(node, onLinkClick, context)
        HorizontalRule -> RenderHorizontalRule()
        is Table -> RenderTable(node, onLinkClick, context)
        is ListItem -> RenderListItem(node, onLinkClick, context)
        is MathFormula -> RenderMathFormula(node.formula, node.isBlock)
        is ChemicalFormula -> RenderChemicalFormula(node.formula)
    }
}

// Render list item (includes task list items)
@Composable
private fun RenderListItem(
    listItem: ListItem,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (listItem.checked != null) {
            // Task list item, show checkbox
            Checkbox(
                checked = listItem.checked,
                onCheckedChange = null, // Read-only
                modifier = Modifier.width(24.dp).padding(top = 4.dp),
                enabled = false
            )
        }

        // Check if list item contains any block-level formulas
        val hasBlockFormula = listItem.children.any { it is MathFormula && it.isBlock }

        if (hasBlockFormula) {
            // If there are block formulas, render them on separate lines
            Column(modifier = Modifier.weight(1f)) {
                var currentTextNodes by remember { mutableStateOf<List<MarkdownNode>>(emptyList()) }

                // Helper composable to render accumulated text nodes
                @Composable
                fun RenderAccumulatedNodes(nodes: List<MarkdownNode>) {
                    if (nodes.isNotEmpty()) {
                        val annotatedString = buildAnnotatedString {
                            nodes.forEach { node ->
                                when (node) {
                                    is TextNode -> {
                                        val spanStyle = when (node.style) {
                                            TextStyle.BOLD -> SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            TextStyle.ITALIC -> SpanStyle(
                                                fontStyle = FontStyle.Italic,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            TextStyle.BOLD_ITALIC -> SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontStyle = FontStyle.Italic,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            TextStyle.CODE -> SpanStyle(
                                                fontFamily = FontFamily.Monospace,
                                                background = MiuixTheme.colorScheme.surfaceVariant,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            TextStyle.STRIKETHROUGH -> SpanStyle(
                                                textDecoration = TextDecoration.LineThrough,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            TextStyle.UNDERLINE -> SpanStyle(
                                                textDecoration = TextDecoration.Underline,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                            else -> SpanStyle(
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                        withStyle(style = spanStyle) {
                                            append(node.text)
                                        }
                                    }
                                    is Link -> {
                                        withLink(
                                            LinkAnnotation.Url(
                                                node.url,
                                                TextLinkStyles(style = SpanStyle(
                                                    color = MiuixTheme.colorScheme.primary,
                                                    textDecoration = TextDecoration.Underline
                                                )),
                                                linkInteractionListener = {
                                                    onLinkClick(node.url)
                                                }
                                            )
                                        ) {
                                            node.children.forEach { linkChild ->
                                                if (linkChild is TextNode) {
                                                    append(linkChild.text)
                                                }
                                            }
                                        }
                                    }
                                    is MathFormula -> {
                                        if (!node.isBlock) {
                                            withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                                                append(preprocessMathFormula(node.formula))
                                            }
                                        }
                                    }
                                    is ChemicalFormula -> {
                                        withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                                            append(preprocessChemicalFormula(node.formula))
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                        Text(
                            text = annotatedString,
                            style = MiuixTheme.textStyles.main,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Process children and render
                listItem.children.forEach { child ->
                    when (child) {
                        is MathFormula -> {
                            if (child.isBlock) {
                                RenderAccumulatedNodes(currentTextNodes)
                                currentTextNodes = emptyList()
                                RenderMathFormula(child.formula, true)
                            } else {
                                currentTextNodes = currentTextNodes + child
                            }
                        }
                        is ChemicalFormula -> {
                            currentTextNodes = currentTextNodes + child
                        }
                        is TextNode, is Link -> {
                            currentTextNodes = currentTextNodes + child
                        }
                        is Image -> {
                            RenderAccumulatedNodes(currentTextNodes)
                            currentTextNodes = emptyList()
                            RenderImage(child, context)
                        }
                        else -> {
                            RenderAccumulatedNodes(currentTextNodes)
                            currentTextNodes = emptyList()
                            RenderNode(child, onLinkClick, context)
                        }
                    }
                }
                RenderAccumulatedNodes(currentTextNodes)
            }
        } else {
            // No block formulas - render everything in a single Text with inline formulas
            val annotatedString = buildAnnotatedString {
                listItem.children.forEach { child ->
                    when (child) {
                        is TextNode -> {
                            val spanStyle = when (child.style) {
                                TextStyle.BOLD -> SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                TextStyle.ITALIC -> SpanStyle(
                                    fontStyle = FontStyle.Italic,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                TextStyle.BOLD_ITALIC -> SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                TextStyle.CODE -> SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = MiuixTheme.colorScheme.surfaceVariant,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                TextStyle.STRIKETHROUGH -> SpanStyle(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                TextStyle.UNDERLINE -> SpanStyle(
                                    textDecoration = TextDecoration.Underline,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                else -> SpanStyle(
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                            withStyle(style = spanStyle) {
                                append(child.text)
                            }
                        }
                        is Link -> {
                            withLink(
                                LinkAnnotation.Url(
                                    child.url,
                                    TextLinkStyles(style = SpanStyle(
                                        color = MiuixTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )),
                                    linkInteractionListener = {
                                        onLinkClick(child.url)
                                    }
                                )
                            ) {
                                child.children.forEach { linkChild ->
                                    if (linkChild is TextNode) {
                                        append(linkChild.text)
                                    }
                                }
                            }
                        }
                        is MathFormula -> {
                            withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                                append(preprocessMathFormula(child.formula))
                            }
                        }
                        is ChemicalFormula -> {
                            withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                                append(preprocessChemicalFormula(child.formula))
                            }
                        }
                        else -> {}
                    }
                }
            }

            Text(
                text = annotatedString,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier.weight(1f)
            )

            // Render images separately if any
            listItem.children.filterIsInstance<Image>().forEach { image ->
                RenderImage(image, context)
            }
        }
    }
}

// Render paragraph
@Composable
private fun RenderParagraph(
    paragraph: Paragraph,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    // Check if paragraph contains any block-level formulas
    val hasBlockFormula = paragraph.children.any { it is MathFormula && it.isBlock }

    if (hasBlockFormula) {
        // If there are block formulas, render them on separate lines
        Column(modifier = Modifier.fillMaxWidth()) {
            var currentTextNodes by remember { mutableStateOf<List<MarkdownNode>>(emptyList()) }

            // Helper composable to render accumulated text nodes
            @Composable
            fun RenderAccumulatedNodes(nodes: List<MarkdownNode>) {
                if (nodes.isNotEmpty()) {
                    val annotatedString = buildAnnotatedString {
                        nodes.forEach { node ->
                            when (node) {
                                is TextNode -> {
                                    val spanStyle = when (node.style) {
                                        TextStyle.BOLD -> SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        TextStyle.ITALIC -> SpanStyle(
                                            fontStyle = FontStyle.Italic,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        TextStyle.BOLD_ITALIC -> SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        TextStyle.CODE -> SpanStyle(
                                            fontFamily = FontFamily.Monospace,
                                            background = MiuixTheme.colorScheme.surfaceVariant,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        TextStyle.STRIKETHROUGH -> SpanStyle(
                                            textDecoration = TextDecoration.LineThrough,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        TextStyle.UNDERLINE -> SpanStyle(
                                            textDecoration = TextDecoration.Underline,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        else -> SpanStyle(
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                    withStyle(style = spanStyle) {
                                        append(node.text)
                                    }
                                }
                                is Link -> {
                                    withLink(
                                        LinkAnnotation.Url(
                                            node.url,
                                            TextLinkStyles(style = SpanStyle(
                                                color = MiuixTheme.colorScheme.primary,
                                                textDecoration = TextDecoration.Underline
                                            )),
                                            linkInteractionListener = {
                                                onLinkClick(node.url)
                                            }
                                        )
                                    ) {
                                        node.children.forEach { linkChild ->
                                            if (linkChild is TextNode) {
                                                append(linkChild.text)
                                            }
                                        }
                                    }
                                }
                                is MathFormula -> {
                                    if (!node.isBlock) {
                                        withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                                            append(preprocessMathFormula(node.formula))
                                        }
                                    }
                                }
                                is ChemicalFormula -> {
                                    withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                                        append(preprocessChemicalFormula(node.formula))
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    Text(
                        text = annotatedString,
                        style = MiuixTheme.textStyles.main,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Process children and render
            paragraph.children.forEach { child ->
                when (child) {
                    is MathFormula -> {
                        if (child.isBlock) {
                            RenderAccumulatedNodes(currentTextNodes)
                            currentTextNodes = emptyList()
                            RenderMathFormula(child.formula, true)
                        } else {
                            currentTextNodes = currentTextNodes + child
                        }
                    }
                    is ChemicalFormula -> {
                        currentTextNodes = currentTextNodes + child
                    }
                    is TextNode, is Link -> {
                        currentTextNodes = currentTextNodes + child
                    }
                    is Image -> {
                        RenderAccumulatedNodes(currentTextNodes)
                        currentTextNodes = emptyList()
                        RenderImage(child, context)
                    }
                    else -> {
                        RenderAccumulatedNodes(currentTextNodes)
                        currentTextNodes = emptyList()
                        RenderNode(child, onLinkClick, context)
                    }
                }
            }
            RenderAccumulatedNodes(currentTextNodes)
        }
    } else {
        // No block formulas - render everything in a single Text with inline formulas
        val annotatedString = buildAnnotatedString {
            paragraph.children.forEach { child ->
                when (child) {
                    is TextNode -> {
                        val spanStyle = when (child.style) {
                            TextStyle.BOLD -> SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            TextStyle.ITALIC -> SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            TextStyle.BOLD_ITALIC -> SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            TextStyle.CODE -> SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = MiuixTheme.colorScheme.surfaceVariant,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            TextStyle.STRIKETHROUGH -> SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            TextStyle.UNDERLINE -> SpanStyle(
                                textDecoration = TextDecoration.Underline,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            else -> SpanStyle(
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                        withStyle(style = spanStyle) {
                            append(child.text)
                        }
                    }
                    is Link -> {
                        withLink(
                            LinkAnnotation.Url(
                                child.url,
                                TextLinkStyles(style = SpanStyle(
                                    color = MiuixTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )),
                                linkInteractionListener = {
                                    onLinkClick(child.url)
                                }
                            )
                        ) {
                            child.children.forEach { linkChild ->
                                if (linkChild is TextNode) {
                                    append(linkChild.text)
                                }
                            }
                        }
                    }
                    is MathFormula -> {
                        // Inline formula - embed directly in text
                        withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                            append(preprocessMathFormula(child.formula))
                        }
                    }
                    is ChemicalFormula -> {
                        withStyle(style = SpanStyle(fontFamily = LatinModernMathFontFamily)) {
                            append(preprocessChemicalFormula(child.formula))
                        }
                    }
                    is Image -> {
                        // Images can't be embedded in AnnotatedString, skip here
                        // They should be handled separately
                    }
                    else -> {
                        // Handle other node types
                    }
                }
            }
        }

        Text(
            text = annotatedString,
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.fillMaxWidth()
        )

        // Render images separately if any
        paragraph.children.filterIsInstance<Image>().forEach { image ->
            RenderImage(image, context)
        }
    }
}

// Sealed class for inline segments
private sealed class InlineSegment {
    data class Text(val nodes: List<MarkdownNode>) : InlineSegment()
    data class Math(val formula: String, val isBlock: Boolean) : InlineSegment()
    data class Chemical(val formula: String) : InlineSegment()
    data class Image(val image: com.chronie.gift.ui.markdown.Image) : InlineSegment()
    data class Other(val node: MarkdownNode) : InlineSegment()
}

// Build inline segments from paragraph children
private fun buildInlineSegments(children: List<MarkdownNode>): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    var currentTextNodes = mutableListOf<MarkdownNode>()

    fun flushTextNodes() {
        if (currentTextNodes.isNotEmpty()) {
            segments.add(InlineSegment.Text(currentTextNodes.toList()))
            currentTextNodes.clear()
        }
    }

    children.forEach { child ->
        when (child) {
            is TextNode, is Link -> {
                currentTextNodes.add(child)
            }
            is MathFormula -> {
                flushTextNodes()
                segments.add(InlineSegment.Math(child.formula, child.isBlock))
            }
            is ChemicalFormula -> {
                flushTextNodes()
                segments.add(InlineSegment.Chemical(child.formula))
            }
            is com.chronie.gift.ui.markdown.Image -> {
                flushTextNodes()
                segments.add(InlineSegment.Image(child))
            }
            else -> {
                flushTextNodes()
                segments.add(InlineSegment.Other(child))
            }
        }
    }

    flushTextNodes()
    return segments
}

// Render heading
@Composable
private fun RenderHeading(
    heading: Heading,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    val fontSize = when (heading.level) {
        1 -> 28.sp
        2 -> 24.sp
        3 -> 20.sp
        4 -> 18.sp
        5 -> 16.sp
        6 -> 14.sp
        else -> 16.sp
    }

    val fontWeight = FontWeight.Bold

    // Check if heading contains math or chemical formulas
    val hasFormula = heading.children.any { it is MathFormula || it is ChemicalFormula }

    if (hasFormula) {
        // Use Row-based layout for headings with formulas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            heading.children.forEach { child ->
                when (child) {
                    is TextNode -> {
                        Text(
                            text = child.text,
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            color = MiuixTheme.colorScheme.onSurface,
                            textDecoration = when (child.style) {
                                TextStyle.STRIKETHROUGH -> TextDecoration.LineThrough
                                TextStyle.UNDERLINE -> TextDecoration.Underline
                                else -> TextDecoration.None
                            }
                        )
                    }
                    is Link -> {
                        val linkText = child.children.filterIsInstance<TextNode>().joinToString("") { it.text }
                        Text(
                            text = linkText,
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            color = MiuixTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onLinkClick(child.url) }
                        )
                    }
                    is MathFormula -> {
                        if (!child.isBlock) {
                            RenderMathFormula(child.formula, false)
                        }
                    }
                    is ChemicalFormula -> {
                        RenderChemicalFormula(child.formula)
                    }
                    else -> {}
                }
            }
        }
    } else {
        // Original implementation for headings without formulas
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            val annotatedString = buildAnnotatedString {
                heading.children.forEach { child ->
                    when (child) {
                        is TextNode -> {
                            withStyle(style = SpanStyle(
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                color = MiuixTheme.colorScheme.onSurface,
                                textDecoration = when (child.style) {
                                    TextStyle.STRIKETHROUGH -> TextDecoration.LineThrough
                                    TextStyle.UNDERLINE -> TextDecoration.Underline
                                    else -> TextDecoration.None
                                }
                            )) {
                                append(child.text)
                            }
                        }
                        is Link -> {
                            withLink(
                                LinkAnnotation.Url(
                                    child.url,
                                    TextLinkStyles(style = SpanStyle(
                                        fontSize = fontSize,
                                        fontWeight = fontWeight,
                                        color = MiuixTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )),
                                    linkInteractionListener = {
                                        onLinkClick(child.url)
                                    }
                                )
                            ) {
                                child.children.forEach { linkChild ->
                                    if (linkChild is TextNode) {
                                        append(linkChild.text)
                                    }
                                }
                            }
                        }
                        else -> {
                            // Handle other types of nodes
                        }
                    }
                }
            }

            val headingStyle = when (heading.level) {
                1 -> MiuixTheme.textStyles.headline1
                2 -> MiuixTheme.textStyles.headline1
                3 -> MiuixTheme.textStyles.subtitle
                4 -> MiuixTheme.textStyles.main
                5 -> MiuixTheme.textStyles.main
                6 -> MiuixTheme.textStyles.body2
                else -> MiuixTheme.textStyles.main
            }

            Text(
                text = annotatedString,
                style = headingStyle
            )
        }
    }
}

// Render text node
@Composable
private fun RenderTextNode(textNode: TextNode) {
    val spanStyle = when (textNode.style) {
        TextStyle.BOLD -> SpanStyle(
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        TextStyle.ITALIC -> SpanStyle(
            fontStyle = FontStyle.Italic,
            color = MiuixTheme.colorScheme.onSurface
        )
        TextStyle.BOLD_ITALIC -> SpanStyle(
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = MiuixTheme.colorScheme.onSurface
        )
        TextStyle.CODE -> SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = MiuixTheme.colorScheme.surfaceVariant,
            color = MiuixTheme.colorScheme.onSurface
        )
        TextStyle.STRIKETHROUGH -> SpanStyle(
            textDecoration = TextDecoration.LineThrough,
            color = MiuixTheme.colorScheme.onSurface
        )
        TextStyle.UNDERLINE -> SpanStyle(
            textDecoration = TextDecoration.Underline,
            color = MiuixTheme.colorScheme.onSurface
        )
        else -> SpanStyle(
            color = MiuixTheme.colorScheme.onSurface
        )
    }
    
    Text(
        buildAnnotatedString { 
            withStyle(style = spanStyle) {
                append(textNode.text)
            }
        },
        style = MiuixTheme.textStyles.main,
        overflow = TextOverflow.Visible,
        softWrap = true,
        modifier = Modifier.fillMaxWidth()
    )
}

// Render link
@Composable
private fun RenderLink(
    link: Link,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    val hasImage = link.children.any { it is Image }
    val hasText = link.children.any { it is TextNode }
    
    if (hasImage) {
        // If the link contains an image, use a Box to wrap all content, making the entire area clickable
        Box(
            modifier = Modifier
                .clickable { onLinkClick(link.url) }
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                link.children.forEach { child ->
                    when (child) {
                        is Image -> RenderImage(child, context)
                        is TextNode -> Text(
                            text = child.text,
                            style = MiuixTheme.textStyles.main.copy(
                                color = MiuixTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        else -> RenderNode(child, onLinkClick, context)
                    }
                }
            }
        }
    } else if (hasText) {
        // If the link only contains text, use the traditional Text component
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(
                    color = MiuixTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )) {
                    link.children.forEach { child ->
                        if (child is TextNode) {
                            append(child.text)
                        }
                    }
                }
            },
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.clickable { onLinkClick(link.url) }
        )
    } else {
        // If the link does not contain any text or image, use empty text
        Text(
            text = link.url,
            style = MiuixTheme.textStyles.main.copy(
                color = MiuixTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            ),
            modifier = Modifier.clickable { onLinkClick(link.url) }
        )
    }
}

// Render image
@Composable
private fun RenderImage(image: Image, context: Context) {
    var imageError by remember { mutableStateOf(false) }
    
    Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 12.dp
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.url)
                    .scale(Scale.FIT)
                    .build(),
                contentDescription = image.altText,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                error = painterResource(android.R.drawable.ic_menu_report_image),
                onError = { imageError = true },
                onSuccess = { imageError = false }
            )
            
            // If there is alt text, display it at the bottom of the image
            if (image.altText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = image.altText,
                        style = MiuixTheme.textStyles.body2,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // If the image fails to load, display an error message
            if (imageError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(MiuixTheme.colorScheme.errorContainer)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Failed to load image: ${image.url}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Render audio
@Composable
private fun RenderAudio(audio: Audio, context: Context) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Use DisposableEffect to manage MediaPlayer lifecycle
    DisposableEffect(audio.url) {
        val player = MediaPlayer()
        mediaPlayer = player
        isPrepared = false
        error = null
        
        try {
            // Use setDataSource instead of create, support streaming large files
            player.setDataSource(context, Uri.parse(audio.url))
            player.setOnPreparedListener {
                isPrepared = true
            }
            player.setOnCompletionListener {
                isPlaying = false
            }
            player.setOnErrorListener { _, what, extra ->
                error = "Error: $what, $extra"
                true
            }
            // Asynchronously prepare the player
            player.prepareAsync()
        } catch (e: Exception) {
            error = "Failed to load audio: ${e.message}"
        }
        
        onDispose {
            player.release()
            mediaPlayer = null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        cornerRadius = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Music,
                contentDescription = "Audio",
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.onSurfaceContainerVariant
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.url.substringAfterLast("/"),
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!isPrepared) {
                    Text(
                        text = context.getString(R.string.loading_text),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }
            
            IconButton(
                onClick = {
                    mediaPlayer?.let { mp ->
                        if (isPlaying) {
                            mp.pause()
                            isPlaying = false
                        } else if (isPrepared) {
                            mp.start()
                            isPlaying = true
                        }
                    }
                },
                enabled = isPrepared && error == null
            ) {
                Icon(
                    imageVector = if (isPlaying) {
                        MiuixIcons.Pause
                    } else {
                        MiuixIcons.Play
                    },
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }
    }
}

// Render video
@Composable
private fun RenderVideo(video: Video, context: Context) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 12.dp
        ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            factory = {
                VideoView(it).apply {
                    setVideoURI(Uri.parse(video.url))
                    val mediaController = MediaController(it)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                }
            },
            update = { videoView ->
                // Only re-set video when URL changes
                val newUri = Uri.parse(video.url)
                // VideoView doesn't have a direct method to get current URI, so we compare URL strings
                // Here we simply re-set the video URI, Android system will handle optimizations
                videoView.setVideoURI(newUri)
            }
        )
    }
}

// Render unordered list
@Composable
private fun RenderUnorderedList(
    list: UnorderedList,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier.padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp) // Add spacing between list items
    ) {
        list.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    modifier = Modifier.width(24.dp).padding(top = 4.dp),
                    style = MiuixTheme.textStyles.main
                )
                Column(modifier = Modifier.weight(1f)) {
                    RenderNode(item, onLinkClick, context)
                }
            }
        }
    }
}

// Render ordered list
@Composable
private fun RenderOrderedList(
    list: OrderedList,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier.padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp) // Add spacing between list items
    ) {
        list.items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${list.startIndex + index}.",
                    modifier = Modifier.width(32.dp).padding(top = 4.dp),
                    style = MiuixTheme.textStyles.main
                )
                Column(modifier = Modifier.weight(1f)) {
                    RenderNode(item, onLinkClick, context)
                }
            }
        }
    }
}

// Render code block
@Composable
private fun RenderCodeBlock(codeBlock: CodeBlock) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        cornerRadius = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (codeBlock.language.isNotEmpty()) {
                Text(
                    text = codeBlock.language,
                    style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = codeBlock.code,
                style = MiuixTheme.textStyles.body2.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Render blockquote
@Composable
private fun RenderBlockquote(
    blockquote: Blockquote,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        cornerRadius = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            blockquote.children.forEach { child ->
                RenderNode(child, onLinkClick, context)
            }
        }
    }
}

// Render horizontal rule
@Composable
private fun RenderHorizontalRule() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        thickness = 2.dp,
        color = MiuixTheme.colorScheme.outline
    )
}

// Render table
@Composable
private fun RenderTable(
    table: Table,
    onLinkClick: (String) -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp
    ) {
        Column {
            // Table header
            Row(
                modifier = Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                table.headers.forEach {header ->
                    Box(
                        modifier = Modifier.weight(1f).padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RenderNode(header, onLinkClick, context)
                    }
                }
            }
            
            // Table rows
            table.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(thickness = 1.dp, color = MiuixTheme.colorScheme.outline)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEachIndexed { cellIndex, cell ->
                        Box(
                            modifier = Modifier.weight(1f).padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            RenderNode(cell, onLinkClick, context)
                        }
                    }
                }
            }
        }
    }
}

package com.chronie.gift.ui.markdown

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.chronie.gift.R

// Latin Modern Math font family for formula rendering
val LatinModernMathFontFamily = FontFamily(
    Font(R.font.latinmodern_math, FontWeight.Normal)
)

/**
 * Render a math formula using Latin Modern Math font
 * Supports common LaTeX-style math notation
 */
@Composable
fun RenderMathFormula(formula: String, isBlock: Boolean = false) {
    val textColor = MiuixTheme.colorScheme.onSurface
    val fontSize = if (isBlock) 18.sp else 16.sp

    // Use simple Text rendering with formula pre-processing
    val processedFormula = preprocessMathFormula(formula)

    if (isBlock) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = processedFormula,
                fontSize = fontSize,
                color = textColor,
                fontFamily = LatinModernMathFontFamily,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                softWrap = false
            )
        }
    } else {
        // Inline formula - align with text baseline
        Box(
            modifier = Modifier.wrapContentHeight(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = processedFormula,
                fontSize = fontSize,
                color = textColor,
                fontFamily = LatinModernMathFontFamily,
                softWrap = false,
                modifier = Modifier.wrapContentWidth()
            )
        }
    }
}

/**
 * Render a chemical formula using Latin Modern Math font
 */
@Composable
fun RenderChemicalFormula(formula: String) {
    val textColor = MiuixTheme.colorScheme.onSurface
    val processedFormula = preprocessChemicalFormula(formula)

    Box(
        modifier = Modifier.wrapContentHeight(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = processedFormula,
            fontSize = 16.sp,
            color = textColor,
            fontFamily = LatinModernMathFontFamily,
            softWrap = false,
            modifier = Modifier.wrapContentWidth()
        )
    }
}

/**
 * Preprocess math formula: convert LaTeX commands to Unicode characters
 * and handle subscripts/superscripts
 */
private fun preprocessMathFormula(formula: String): String {
    var result = formula

    // Replace Greek letters and symbols
    val replacements = mapOf(
        // Greek letters - lowercase
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\iota" to "ι",
        "\\kappa" to "κ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\upsilon" to "υ",
        "\\phi" to "φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        // Greek letters - uppercase
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Xi" to "Ξ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Upsilon" to "Υ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        // Math operators and symbols
        "\\infty" to "∞",
        "\\infinity" to "∞",
        "\\pm" to "±",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\approx" to "≈",
        "\\sim" to "∼",
        "\\rightarrow" to "→",
        "\\to" to "→",
        "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\subseteq" to "⊆",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\emptyset" to "∅",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\nabla" to "∇",
        "\\partial" to "∂",
        "\\int" to "∫",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\sqrt" to "√",
        "\\ldots" to "…",
        "\\cdots" to "⋯",
        "\\vdots" to "⋮",
        "\\ddots" to "⋱",
        // Arrows and accents
        "\\vec" to "→",
        "\\hat" to "^",
        "\\bar" to "‾",
        "\\overline" to "‾",
        "\\underline" to "_",
        // Comparison
        "\\equiv" to "≡",
        "\\cong" to "≅",
        "\\propto" to "∝",
        "\\perp" to "⊥",
        "\\parallel" to "∥",
        "\\angle" to "∠",
        "\\triangle" to "△",
        // Logic
        "\\land" to "∧",
        "\\lor" to "∨",
        "\\neg" to "¬",
        "\\implies" to "⇒",
        "\\iff" to "⇔",
        // Set theory
        "\\setminus" to "\\",
        "\\complement" to "∁",
        "\\subset" to "⊂",
        "\\subseteq" to "⊆",
        "\\supset" to "⊃",
        "\\supseteq" to "⊇",
        "\\nsubseteq" to "⊈",
        "\\nsupseteq" to "⊉",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\ni" to "∋",
        "\\emptyset" to "∅",
        "\\varnothing" to "∅",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\bigcup" to "⋃",
        "\\bigcap" to "⋂",
        "\\setminus" to "∖",
        // Calculus
        "\\oint" to "∮",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        "\\lim" to "lim",
        "\\sup" to "sup",
        "\\inf" to "inf",
        "\\max" to "max",
        "\\min" to "min",
        "\\limsup" to "lim sup",
        "\\liminf" to "lim inf",
        "\\infty" to "∞",
        "\\infinity" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\int" to "∫",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\oint" to "∮",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        // Arrows
        "\\rightarrow" to "→",
        "\\to" to "→",
        "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\leftrightarrow" to "↔",
        "\\Leftrightarrow" to "⇔",
        "\\mapsto" to "↦",
        "\\longrightarrow" to "⟶",
        "\\longleftarrow" to "⟵",
        "\\uparrow" to "↑",
        "\\downarrow" to "↓",
        "\\updownarrow" to "↕",
        // Harpoons
        "\\leftharpoonup" to "↼",
        "\\leftharpoondown" to "↽",
        "\\rightharpoonup" to "⇀",
        "\\rightharpoondown" to "⇁",
        "\\rightleftharpoons" to "⇌",
        "\\leftrightharpoons" to "⇋",
        "\\upharpoonleft" to "↿",
        "\\upharpoonright" to "↾",
        "\\downharpoonleft" to "⇃",
        "\\downharpoonright" to "⇂",
        // Long arrows
        "\\longleftrightarrow" to "⟷",
        "\\Longleftrightarrow" to "⟺",
        "\\Longrightarrow" to "⟹",
        "\\Longleftarrow" to "⟸",
        // Double arrows
        "\\leftrightarrows" to "⇆",
        "\\rightleftarrows" to "⇄",
        "\\updownarrows" to "⇅",
        "\\downuparrows" to "⇵",
        // Relations
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\approx" to "≈",
        "\\sim" to "∼",
        "\\simeq" to "≃",
        "\\cong" to "≅",
        "\\equiv" to "≡",
        "\\propto" to "∝",
        "\\perp" to "⊥",
        "\\parallel" to "∥",
        "\\asymp" to "≍",
        "\\doteq" to "≐",
        // Operators
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\oplus" to "⊕",
        "\\ominus" to "⊖",
        "\\otimes" to "⊗",
        "\\oslash" to "⊘",
        "\\odot" to "⊙",
        "\\circ" to "∘",
        "\\bullet" to "•",
        "\\dagger" to "†",
        "\\ddagger" to "‡",
        "\\amalg" to "⨿",
        // Brackets
        "\\langle" to "⟨",
        "\\rangle" to "⟩",
        "\\lfloor" to "⌊",
        "\\rfloor" to "⌋",
        "\\lceil" to "⌈",
        "\\rceil" to "⌉",
        "\\vert" to "|",
        "\\Vert" to "‖",
        // Other useful symbols
        "\\degree" to "°",
        "\\circ" to "°",
        "\\prime" to "′",
        "\\dots" to "…",
        "\\vdots" to "⋮",
        "\\cdots" to "⋯",
        "\\ddots" to "⋱",
        "\\ldots" to "…",
        "\\angle" to "∠",
        "\\triangle" to "△",
        "\\square" to "□",
        "\\diamond" to "◇",
        "\\star" to "★",
        "\\ast" to "∗",
        "\\bullet" to "•",
        "\\wedge" to "∧",
        "\\vee" to "∨",
        "\\neg" to "¬",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\nexists" to "∄",
        "\\top" to "⊤",
        "\\bot" to "⊥",
        "\\vdash" to "⊢",
        "\\models" to "⊨",
        "\\aleph" to "ℵ",
        "\\hbar" to "ℏ",
        "\\ell" to "ℓ",
        "\\wp" to "℘",
        "\\Re" to "ℜ",
        "\\Im" to "ℑ",
        "\\mho" to "℧",
        "\\prime" to "′",
        "\\emptyset" to "∅",
        "\\nabla" to "∇",
        "\\surd" to "√",
        "\\neg" to "¬",
        "\\flat" to "♭",
        "\\natural" to "♮",
        "\\sharp" to "♯",
        "\\clubsuit" to "♣",
        "\\diamondsuit" to "♢",
        "\\heartsuit" to "♡",
        "\\spadesuit" to "♠"
    )

    // Apply replacements (longest first to avoid partial matches)
    replacements.toList()
        .sortedByDescending { it.first.length }
        .forEach { (latex, unicode) ->
            result = result.replace(latex, unicode)
        }

    // Handle nested structures first (most nested to least)
    // Handle \mathbf, \mathit, etc. first
    result = handleMathFontCommands(result)

    // Handle \text{...}
    result = handleTextCommand(result)

    // Handle matrices and arrays
    result = handleMatrices(result)

    // Handle cases (piecewise functions)
    result = handleCases(result)

    // Handle fractions
    result = handleFractions(result)

    // Handle roots
    result = handleSqrt(result)

    // Handle limits and operators with subscripts/superscripts
    result = handleLimitsAndOperators(result)

    // Handle subscripts and superscripts
    result = handleSubscripts(result)
    result = handleSuperscripts(result)

    // Handle brackets and parentheses sizing
    result = handleBrackets(result)

    // Handle accents
    result = handleAccents(result)

    // Clean up remaining braces
    result = result.replace("{", "").replace("}", "")

    // Handle spacing commands
    result = result.replace("\\,", " ")
    result = result.replace("\\;", " ")
    result = result.replace("\\:", " ")
    result = result.replace("\\!", "")
    result = result.replace("\\quad", "  ")
    result = result.replace("\\qquad", "    ")

    // Remove remaining unknown commands but keep their arguments if possible
    result = result.replace(Regex("\\\\([a-zA-Z]+)")) { match ->
        ""  // Remove unknown commands
    }

    // Clean up multiple spaces
    result = result.replace(Regex("\\s+"), " ")

    return result.trim()
}

/**
 * Preprocess chemical formula: handle subscripts
 */
private fun preprocessChemicalFormula(formula: String): String {
    var result = formula

    // Handle subscripts: _{...} or _x
    result = handleSubscripts(result)

    // Handle superscripts for charges: ^{...} or ^x
    result = handleSuperscripts(result)

    // Clean up braces
    result = result.replace("{", "").replace("}", "")

    return result.trim()
}

private fun handleFractions(formula: String): String {
    var result = formula

    // Handle nested fractions by repeatedly processing from innermost to outermost
    var continueProcessing = true
    while (continueProcessing) {
        continueProcessing = false

        // Find \frac{...}{...} with balanced braces
        val fracPattern = Regex("\\\\frac\\{")
        val match = fracPattern.find(result)

        if (match != null) {
            val startIndex = match.range.first
            val afterFrac = match.range.last + 1

            // Extract first argument (numerator) with balanced braces
            val (numerator, afterNumerator) = extractBalancedBraces(result, afterFrac)
            if (numerator != null) {
                // Extract second argument (denominator) with balanced braces
                val (denominator, afterDenominator) = extractBalancedBraces(result, afterNumerator)
                if (denominator != null) {
                    // Replace this fraction
                    val before = result.substring(0, startIndex)
                    val after = result.substring(afterDenominator)
                    result = before + "($numerator/$denominator)" + after
                    continueProcessing = true
                }
            }
        }
    }

    return result
}

/**
 * Extract content within balanced braces starting at given index
 * Returns Pair of (content inside braces, index after closing brace) or (null, -1) on failure
 */
private fun extractBalancedBraces(text: String, startIndex: Int): Pair<String?, Int> {
    if (startIndex >= text.length || text[startIndex] != '{') {
        return Pair(null, -1)
    }

    var braceCount = 1
    var index = startIndex + 1

    while (index < text.length && braceCount > 0) {
        when (text[index]) {
            '{' -> braceCount++
            '}' -> braceCount--
        }
        index++
    }

    return if (braceCount == 0) {
        Pair(text.substring(startIndex + 1, index - 1), index)
    } else {
        Pair(null, -1)
    }
}

private fun handleSqrt(formula: String): String {
    var result = formula
    val sqrtRegex = Regex("\\\\sqrt\\{([^}]+)\\}")

    while (sqrtRegex.containsMatchIn(result)) {
        result = sqrtRegex.replace(result) { match ->
            val content = match.groupValues[1]
            "√($content)"
        }
    }

    // Handle \sqrt[root]{...}
    val sqrtWithRootRegex = Regex("\\\\sqrt\\[(\\d+)\\]\\{([^}]+)\\}")
    result = sqrtWithRootRegex.replace(result) { match ->
        val root = match.groupValues[1]
        val content = match.groupValues[2]
        "${root}√($content)"
    }

    return result
}

private fun handleSubscripts(formula: String): String {
    var result = formula

    // Handle _{...}
    val subscriptRegex = Regex("_\\{([^}]+)\\}")
    result = subscriptRegex.replace(result) { match ->
        val content = match.groupValues[1]
        content.map { char ->
            when (char) {
                '0' -> '₀'
                '1' -> '₁'
                '2' -> '₂'
                '3' -> '₃'
                '4' -> '₄'
                '5' -> '₅'
                '6' -> '₆'
                '7' -> '₇'
                '8' -> '₈'
                '9' -> '₉'
                '+' -> '₊'
                '-' -> '₋'
                '=' -> '₌'
                '(' -> '₍'
                ')' -> '₎'
                'a' -> 'ₐ'
                'e' -> 'ₑ'
                'i' -> 'ᵢ'
                'o' -> 'ₒ'
                'u' -> 'ᵤ'
                'v' -> 'ᵥ'
                'x' -> 'ₓ'
                'h' -> 'ₕ'
                'k' -> 'ₖ'
                'l' -> 'ₗ'
                'm' -> 'ₘ'
                'n' -> 'ₙ'
                'p' -> 'ₚ'
                's' -> 'ₛ'
                't' -> 'ₜ'
                else -> char
            }
        }.joinToString("")
    }

    // Handle single character subscripts: _x
    val singleSubscriptRegex = Regex("_([0-9a-zA-Z])")
    result = singleSubscriptRegex.replace(result) { match ->
        val char = match.groupValues[1][0]
        val subscript = when (char) {
            '0' -> '₀'
            '1' -> '₁'
            '2' -> '₂'
            '3' -> '₃'
            '4' -> '₄'
            '5' -> '₅'
            '6' -> '₆'
            '7' -> '₇'
            '8' -> '₈'
            '9' -> '₉'
            '+' -> '₊'
            '-' -> '₋'
            '=' -> '₌'
            '(' -> '₍'
            ')' -> '₎'
            'a' -> 'ₐ'
            'e' -> 'ₑ'
            'i' -> 'ᵢ'
            'o' -> 'ₒ'
            'u' -> 'ᵤ'
            'v' -> 'ᵥ'
            'x' -> 'ₓ'
            'h' -> 'ₕ'
            'k' -> 'ₖ'
            'l' -> 'ₗ'
            'm' -> 'ₘ'
            'n' -> 'ₙ'
            'p' -> 'ₚ'
            's' -> 'ₛ'
            't' -> 'ₜ'
            else -> char
        }
        subscript.toString()
    }

    return result
}

private fun handleSuperscripts(formula: String): String {
    var result = formula

    // Handle ^{...}
    val superscriptRegex = Regex("\\^\\{([^}]+)\\}")
    result = superscriptRegex.replace(result) { match ->
        val content = match.groupValues[1]
        content.map { char ->
            when (char) {
                '0' -> '⁰'
                '1' -> '¹'
                '2' -> '²'
                '3' -> '³'
                '4' -> '⁴'
                '5' -> '⁵'
                '6' -> '⁶'
                '7' -> '⁷'
                '8' -> '⁸'
                '9' -> '⁹'
                '+' -> '⁺'
                '-' -> '⁻'
                '=' -> '⁼'
                '(' -> '⁽'
                ')' -> '⁾'
                'a' -> 'ᵃ'
                'b' -> 'ᵇ'
                'c' -> 'ᶜ'
                'd' -> 'ᵈ'
                'e' -> 'ᵉ'
                'f' -> 'ᶠ'
                'g' -> 'ᵍ'
                'h' -> 'ʰ'
                'i' -> 'ⁱ'
                'j' -> 'ʲ'
                'k' -> 'ᵏ'
                'l' -> 'ˡ'
                'm' -> 'ᵐ'
                'n' -> 'ⁿ'
                'o' -> 'ᵒ'
                'p' -> 'ᵖ'
                'r' -> 'ʳ'
                's' -> 'ˢ'
                't' -> 'ᵗ'
                'u' -> 'ᵘ'
                'v' -> 'ᵛ'
                'w' -> 'ʷ'
                'x' -> 'ˣ'
                'y' -> 'ʸ'
                'z' -> 'ᶻ'
                'A' -> 'ᴬ'
                'B' -> 'ᴮ'
                'C' -> 'ᶜ'
                'D' -> 'ᴰ'
                'E' -> 'ᴱ'
                'F' -> 'ᶠ'
                'G' -> 'ᴳ'
                'H' -> 'ᴴ'
                'I' -> 'ᴵ'
                'J' -> 'ᴶ'
                'K' -> 'ᴷ'
                'L' -> 'ᴸ'
                'M' -> 'ᴹ'
                'N' -> 'ᴺ'
                'O' -> 'ᴼ'
                'P' -> 'ᴾ'
                'R' -> 'ᴿ'
                'T' -> 'ᵀ'
                'U' -> 'ᵁ'
                'V' -> 'ⱽ'
                'W' -> 'ᵂ'
                else -> char
            }
        }.joinToString("")
    }

    // Handle single character superscripts: ^x
    val singleSuperscriptRegex = Regex("\\^([0-9a-zA-Z+-=()])")
    result = singleSuperscriptRegex.replace(result) { match ->
        val char = match.groupValues[1][0]
        val superscript = when (char) {
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            '+' -> '⁺'
            '-' -> '⁻'
            '=' -> '⁼'
            '(' -> '⁽'
            ')' -> '⁾'
            'a' -> 'ᵃ'
            'b' -> 'ᵇ'
            'c' -> 'ᶜ'
            'd' -> 'ᵈ'
            'e' -> 'ᵉ'
            'f' -> 'ᶠ'
            'g' -> 'ᵍ'
            'h' -> 'ʰ'
            'i' -> 'ⁱ'
            'j' -> 'ʲ'
            'k' -> 'ᵏ'
            'l' -> 'ˡ'
            'm' -> 'ᵐ'
            'n' -> 'ⁿ'
            'o' -> 'ᵒ'
            'p' -> 'ᵖ'
            'r' -> 'ʳ'
            's' -> 'ˢ'
            't' -> 'ᵗ'
            'u' -> 'ᵘ'
            'v' -> 'ᵛ'
            'w' -> 'ʷ'
            'x' -> 'ˣ'
            'y' -> 'ʸ'
            'z' -> 'ᶻ'
            'A' -> 'ᴬ'
            'B' -> 'ᴮ'
            'C' -> 'ᶜ'
            'D' -> 'ᴰ'
            'E' -> 'ᴱ'
            'F' -> 'ᶠ'
            'G' -> 'ᴳ'
            'H' -> 'ᴴ'
            'I' -> 'ᴵ'
            'J' -> 'ᴶ'
            'K' -> 'ᴷ'
            'L' -> 'ᴸ'
            'M' -> 'ᴹ'
            'N' -> 'ᴺ'
            'O' -> 'ᴼ'
            'P' -> 'ᴾ'
            'R' -> 'ᴿ'
            'T' -> 'ᵀ'
            'U' -> 'ᵁ'
            'V' -> 'ⱽ'
            'W' -> 'ᵂ'
            else -> char
        }
        superscript.toString()
    }

    return result
}

private fun handleTextCommand(formula: String): String {
    var result = formula
    val textRegex = Regex("\\\\text\\{([^}]+)\\}")

    while (textRegex.containsMatchIn(result)) {
        result = textRegex.replace(result) { match ->
            match.groupValues[1]
        }
    }

    return result
}

private fun handleMathFontCommands(formula: String): String {
    var result = formula

    // \mathbf{...} - bold (just remove the command for now)
    val mathbfRegex = Regex("\\\\mathbf\\{([^}]+)\\}")
    result = mathbfRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathit{...} - italic (just remove the command for now)
    val mathitRegex = Regex("\\\\mathit\\{([^}]+)\\}")
    result = mathitRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathrm{...} - roman (just remove the command)
    val mathrmRegex = Regex("\\\\mathrm\\{([^}]+)\\}")
    result = mathrmRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathsf{...} - sans-serif (just remove the command)
    val mathsfRegex = Regex("\\\\mathsf\\{([^}]+)\\}")
    result = mathsfRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathtt{...} - typewriter (just remove the command)
    val mathttRegex = Regex("\\\\mathtt\\{([^}]+)\\}")
    result = mathttRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathcal{...} - calligraphic (convert to regular for now)
    val mathcalRegex = Regex("\\\\mathcal\\{([^}]+)\\}")
    result = mathcalRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathfrak{...} - fraktur (convert to regular for now)
    val mathfrakRegex = Regex("\\\\mathfrak\\{([^}]+)\\}")
    result = mathfrakRegex.replace(result) { match ->
        match.groupValues[1]
    }

    // \mathbb{...} - blackboard bold (convert to regular for now)
    val mathbbRegex = Regex("\\\\mathbb\\{([^}]+)\\}")
    result = mathbbRegex.replace(result) { match ->
        match.groupValues[1]
    }

    return result
}

/**
 * Handle limits and operators like \lim_{x \to 0}, \sum_{i=1}^{n}, etc.
 */
private fun handleLimitsAndOperators(formula: String): String {
    var result = formula

    // Handle \lim_{...}
    val limRegex = Regex("\\\\lim_\\{([^}]+)\\}")
    result = limRegex.replace(result) { match ->
        val limit = match.groupValues[1]
        "lim${limit.toSubscript()}"
    }

    // Handle \sum_{...}^{...}
    val sumRegex = Regex("\\\\sum_\\{([^}]+)\\}\\^\\{([^}]+)\\}")
    result = sumRegex.replace(result) { match ->
        val lower = match.groupValues[1]
        val upper = match.groupValues[2]
        "∑${lower.toSubscript()}${upper.toSuperscript()}"
    }

    // Handle \sum_{...}
    val sumLowerRegex = Regex("\\\\sum_\\{([^}]+)\\}")
    result = sumLowerRegex.replace(result) { match ->
        val lower = match.groupValues[1]
        "∑${lower.toSubscript()}"
    }

    // Handle \prod_{...}^{...}
    val prodRegex = Regex("\\\\prod_\\{([^}]+)\\}\\^\\{([^}]+)\\}")
    result = prodRegex.replace(result) { match ->
        val lower = match.groupValues[1]
        val upper = match.groupValues[2]
        "∏${lower.toSubscript()}${upper.toSuperscript()}"
    }

    // Handle \prod_{...}
    val prodLowerRegex = Regex("\\\\prod_\\{([^}]+)\\}")
    result = prodLowerRegex.replace(result) { match ->
        val lower = match.groupValues[1]
        "∏${lower.toSubscript()}"
    }

    // Handle \int_{...}^{...}
    val intRegex = Regex("\\\\int_\\{([^}]+)\\}\\^\\{([^}]+)\\}")
    result = intRegex.replace(result) { match ->
        val lower = match.groupValues[1]
        val upper = match.groupValues[2]
        "∫${lower.toSubscript()}${upper.toSuperscript()}"
    }

    // Handle \int_{...}
    val intLowerRegex = Regex("\\\\int_\\{([^}]+)\\}")
    result = intLowerRegex.replace(result) { match ->
        val lower = match.groupValues[1]
        "∫${lower.toSubscript()}"
    }

    return result
}

/**
 * Handle brackets and parentheses sizing (\left, \right, \big, etc.)
 */
private fun handleBrackets(formula: String): String {
    var result = formula

    // Remove \left and \right
    result = result.replace("\\left", "")
    result = result.replace("\\right", "")

    // Remove sizing commands
    result = result.replace("\\big", "")
    result = result.replace("\\Big", "")
    result = result.replace("\\bigg", "")
    result = result.replace("\\Bigg", "")

    // Handle \langle and \rangle
    result = result.replace("\\langle", "⟨")
    result = result.replace("\\rangle", "⟩")

    // Handle \lfloor and \rfloor
    result = result.replace("\\lfloor", "⌊")
    result = result.replace("\\rfloor", "⌋")

    // Handle \lceil and \rceil
    result = result.replace("\\lceil", "⌈")
    result = result.replace("\\rceil", "⌉")

    // Handle \{ and \}
    result = result.replace("\\{", "{")
    result = result.replace("\\}", "}")

    return result
}

/**
 * Handle accents like \dot, \ddot, \tilde, etc.
 */
private fun handleAccents(formula: String): String {
    var result = formula

    // \dot{x} -> x with dot above (simplified)
    val dotRegex = Regex("\\\\dot\\{([^}]+)\\}")
    result = dotRegex.replace(result) { match ->
        match.groupValues[1] + "˙"
    }

    // \ddot{x} -> x with double dot above
    val ddotRegex = Regex("\\\\ddot\\{([^}]+)\\}")
    result = ddotRegex.replace(result) { match ->
        match.groupValues[1] + "¨"
    }

    // \tilde{x}
    val tildeRegex = Regex("\\\\tilde\\{([^}]+)\\}")
    result = tildeRegex.replace(result) { match ->
        match.groupValues[1] + "˜"
    }

    // \widehat{x}
    val widehatRegex = Regex("\\\\widehat\\{([^}]+)\\}")
    result = widehatRegex.replace(result) { match ->
        match.groupValues[1] + "^"
    }

    // \widetilde{x}
    val widetildeRegex = Regex("\\\\widetilde\\{([^}]+)\\}")
    result = widetildeRegex.replace(result) { match ->
        match.groupValues[1] + "˜"
    }

    return result
}

/**
 * Handle matrices: \begin{bmatrix} ... \end{bmatrix}, etc.
 */
private fun handleMatrices(formula: String): String {
    var result = formula

    // Handle bmatrix (square brackets)
    val bmatrixRegex = Regex("\\\\begin\\{bmatrix\\}([^\\}]+)\\\\end\\{bmatrix\\}")
    result = bmatrixRegex.replace(result) { match ->
        val content = match.groupValues[1]
        formatMatrix(content, "[", "]")
    }

    // Handle pmatrix (parentheses)
    val pmatrixRegex = Regex("\\\\begin\\{pmatrix\\}([^\\}]+)\\\\end\\{pmatrix\\}")
    result = pmatrixRegex.replace(result) { match ->
        val content = match.groupValues[1]
        formatMatrix(content, "(", ")")
    }

    // Handle vmatrix (vertical bars, for determinants)
    val vmatrixRegex = Regex("\\\\begin\\{vmatrix\\}([^\\}]+)\\\\end\\{vmatrix\\}")
    result = vmatrixRegex.replace(result) { match ->
        val content = match.groupValues[1]
        formatMatrix(content, "|", "|")
    }

    // Handle Bmatrix (curly braces)
    val BmatrixRegex = Regex("\\\\begin\\{Bmatrix\\}([^\\}]+)\\\\end\\{Bmatrix\\}")
    result = BmatrixRegex.replace(result) { match ->
        val content = match.groupValues[1]
        formatMatrix(content, "{", "}")
    }

    // Handle simple matrix without environment
    val simpleMatrixRegex = Regex("\\\\begin\\{matrix\\}([^\\}]+)\\\\end\\{matrix\\}")
    result = simpleMatrixRegex.replace(result) { match ->
        val content = match.groupValues[1]
        formatMatrix(content, "", "")
    }

    return result
}

/**
 * Format matrix content into a readable string
 */
private fun formatMatrix(content: String, leftDelim: String, rightDelim: String): String {
    // Replace \\ with row separator
    val rows = content.split("\\\\").map { it.trim() }

    // Process each row
    val processedRows = rows.map { row ->
        // Replace & with space
        row.replace("&", " ").trim()
    }

    // Join rows with newlines and wrap in delimiters
    return "$leftDelim${processedRows.joinToString("; ")}$rightDelim"
}

/**
 * Handle cases environment (piecewise functions)
 */
private fun handleCases(formula: String): String {
    var result = formula

    // Handle \begin{cases} ... \end{cases}
    val casesRegex = Regex("\\\\begin\\{cases\\}([^\\}]+)\\\\end\\{cases\\}")
    result = casesRegex.replace(result) { match ->
        val content = match.groupValues[1]
        formatCases(content)
    }

    return result
}

/**
 * Format cases content into a readable string
 */
private fun formatCases(content: String): String {
    // Split by \\
    val cases = content.split("\\\\").map { it.trim() }

    // Process each case
    val processedCases = cases.map { case ->
        // Replace & with " if "
        case.replace("&", " if ").trim()
    }

    // Join with commas
    return processedCases.joinToString(", ")
}

/**
 * Convert string to subscript Unicode characters
 */
private fun String.toSubscript(): String {
    return this.map { char ->
        when (char) {
            '0' -> '₀'
            '1' -> '₁'
            '2' -> '₂'
            '3' -> '₃'
            '4' -> '₄'
            '5' -> '₅'
            '6' -> '₆'
            '7' -> '₇'
            '8' -> '₈'
            '9' -> '₉'
            '+' -> '₊'
            '-' -> '₋'
            '=' -> '₌'
            '(' -> '₍'
            ')' -> '₎'
            'a' -> 'ₐ'
            'e' -> 'ₑ'
            'i' -> 'ᵢ'
            'o' -> 'ₒ'
            'u' -> 'ᵤ'
            'v' -> 'ᵥ'
            'x' -> 'ₓ'
            'h' -> 'ₕ'
            'k' -> 'ₖ'
            'l' -> 'ₗ'
            'm' -> 'ₘ'
            'n' -> 'ₙ'
            'p' -> 'ₚ'
            's' -> 'ₛ'
            't' -> 'ₜ'
            'j' -> 'ⱼ'
            'r' -> 'ᵣ'
            'y' -> 'ᵧ'
            'z' -> '₂'
            'β' -> 'ᵦ'
            'γ' -> 'ᵧ'
            'ρ' -> 'ᵨ'
            'φ' -> 'ᵩ'
            'χ' -> 'ᵪ'
            else -> char
        }
    }.joinToString("")
}

/**
 * Convert string to superscript Unicode characters
 */
private fun String.toSuperscript(): String {
    return this.map { char ->
        when (char) {
            '0' -> '⁰'
            '1' -> '¹'
            '2' -> '²'
            '3' -> '³'
            '4' -> '⁴'
            '5' -> '⁵'
            '6' -> '⁶'
            '7' -> '⁷'
            '8' -> '⁸'
            '9' -> '⁹'
            '+' -> '⁺'
            '-' -> '⁻'
            '=' -> '⁼'
            '(' -> '⁽'
            ')' -> '⁾'
            'a' -> 'ᵃ'
            'b' -> 'ᵇ'
            'c' -> 'ᶜ'
            'd' -> 'ᵈ'
            'e' -> 'ᵉ'
            'f' -> 'ᶠ'
            'g' -> 'ᵍ'
            'h' -> 'ʰ'
            'i' -> 'ⁱ'
            'j' -> 'ʲ'
            'k' -> 'ᵏ'
            'l' -> 'ˡ'
            'm' -> 'ᵐ'
            'n' -> 'ⁿ'
            'o' -> 'ᵒ'
            'p' -> 'ᵖ'
            'r' -> 'ʳ'
            's' -> 'ˢ'
            't' -> 'ᵗ'
            'u' -> 'ᵘ'
            'v' -> 'ᵛ'
            'w' -> 'ʷ'
            'x' -> 'ˣ'
            'y' -> 'ʸ'
            'z' -> 'ᶻ'
            'A' -> 'ᴬ'
            'B' -> 'ᴮ'
            'C' -> 'ᶜ'
            'D' -> 'ᴰ'
            'E' -> 'ᴱ'
            'F' -> 'ᶠ'
            'G' -> 'ᴳ'
            'H' -> 'ᴴ'
            'I' -> 'ᴵ'
            'J' -> 'ᴶ'
            'K' -> 'ᴷ'
            'L' -> 'ᴸ'
            'M' -> 'ᴹ'
            'N' -> 'ᴺ'
            'O' -> 'ᴼ'
            'P' -> 'ᴾ'
            'R' -> 'ᴿ'
            'T' -> 'ᵀ'
            'U' -> 'ᵁ'
            'V' -> 'ⱽ'
            'W' -> 'ᵂ'
            'β' -> 'ᵝ'
            'γ' -> 'ᵞ'
            'δ' -> 'ᵟ'
            'θ' -> 'ᶿ'
            'ι' -> 'ᶥ'
            'φ' -> 'ᵠ'
            'χ' -> 'ᵡ'
            else -> char
        }
    }.joinToString("")
}

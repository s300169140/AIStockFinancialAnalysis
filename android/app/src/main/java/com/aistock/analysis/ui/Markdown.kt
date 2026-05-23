package com.aistock.analysis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Minimal markdown renderer covering what DeepSeek emits: H1-H3 headings, bold,
// italic, bullet/numbered lists, code spans, paragraphs. Skips images/tables/links
// on purpose — keeps things deterministic and dependency-free.
@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parseBlocks(source) }
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is Block.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    SelectionContainer {
                        Text(
                            text = renderInline(block.text),
                            style = style,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }
                }
                is Block.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = renderInline(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                is Block.ListItem -> {
                    SelectionContainer {
                        Text(
                            text = renderInline("${block.marker} ${block.text}"),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        )
                    }
                }
                is Block.Blank -> Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class ListItem(val marker: String, val text: String) : Block
    data object Blank : Block
}

private fun parseBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val pendingPara = StringBuilder()

    fun flushPara() {
        if (pendingPara.isNotBlank()) {
            out.add(Block.Paragraph(pendingPara.toString().trim()))
        }
        pendingPara.clear()
    }

    src.split('\n').forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.isBlank() -> { flushPara(); out.add(Block.Blank) }
            line.startsWith("### ") -> { flushPara(); out.add(Block.Heading(3, line.removePrefix("### "))) }
            line.startsWith("## ") -> { flushPara(); out.add(Block.Heading(2, line.removePrefix("## "))) }
            line.startsWith("# ") -> { flushPara(); out.add(Block.Heading(1, line.removePrefix("# "))) }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushPara(); out.add(Block.ListItem("•", line.drop(2)))
            }
            Regex("^\\d+\\.\\s").containsMatchIn(line) -> {
                flushPara()
                val match = Regex("^(\\d+\\.)\\s+(.*)").find(line)
                if (match != null) out.add(Block.ListItem(match.groupValues[1], match.groupValues[2]))
                else pendingPara.appendLine(line)
            }
            else -> {
                if (pendingPara.isNotEmpty()) pendingPara.append(' ')
                pendingPara.append(line)
            }
        }
    }
    flushPara()
    return out
}

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("**") -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append("**"); i += 2 }
            }
            rest.startsWith("*") && !rest.startsWith("**") -> {
                val end = text.indexOf("*", i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append("*"); i += 1 }
            }
            rest.startsWith("`") -> {
                val end = text.indexOf("`", i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(background = androidx.compose.ui.graphics.Color(0x22000000))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append('`'); i += 1 }
            }
            else -> { append(text[i]); i += 1 }
        }
    }
}

private fun AnnotatedString.Builder.withStyle(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style); try { block() } finally { pop() }
}

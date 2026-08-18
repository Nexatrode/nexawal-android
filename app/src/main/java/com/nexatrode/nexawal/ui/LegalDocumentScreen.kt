package com.nexatrode.nexawal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexatrode.nexawal.MoneroConfig
import com.nexatrode.nexawal.R

enum class LegalDocument {
    Terms,
    Privacy,
    License,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    document: LegalDocument,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val technoTheme = remember { MoneroConfig.isTechnoThemeEnabled(context) }
    val palette = rememberNexaPalette(technoTheme)
    val neon = palette.classic
    val assetPath = when (document) {
        LegalDocument.Terms -> "legal/terms.md"
        LegalDocument.Privacy -> "legal/privacy.md"
        LegalDocument.License -> "legal/license.md"
    }
    val title = when (document) {
        LegalDocument.Terms -> stringResource(R.string.terms_of_use)
        LegalDocument.Privacy -> stringResource(R.string.privacy_policy)
        LegalDocument.License -> stringResource(R.string.mit_license)
    }
    val markdown = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    val blocks = remember(markdown) { SimpleMarkdown.parse(markdown) }

    Scaffold(
        modifier = modifier,
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        color = palette.primaryText,
                        fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text(
                            text = stringResource(R.string.legal_close),
                            color = palette.accent,
                            fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.primaryText,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (blocks.isEmpty()) {
                Text(
                    text = stringResource(R.string.legal_load_error),
                    color = palette.secondaryText,
                    fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                )
            } else {
                blocks.forEach { block ->
                    when (block) {
                        is SimpleMarkdown.Block.Heading -> {
                            Text(
                                text = block.text,
                                color = palette.primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = when (block.level) {
                                    1 -> 22.sp
                                    2 -> 18.sp
                                    else -> 16.sp
                                },
                                fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (block.level <= 2) 8.dp else 4.dp),
                            )
                        }
                        is SimpleMarkdown.Block.Bullet -> {
                            Text(
                                text = "• ${block.text}",
                                color = palette.secondaryText,
                                fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        is SimpleMarkdown.Block.Paragraph -> {
                            Text(
                                text = block.text,
                                color = palette.secondaryText,
                                fontFamily = if (neon) FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Minimal Markdown subset: #/##/### headings, "- " bullets, paragraphs. */
internal object SimpleMarkdown {
    sealed class Block {
        data class Heading(val level: Int, val text: String) : Block()
        data class Bullet(val text: String) : Block()
        data class Paragraph(val text: String) : Block()
    }

    fun parse(source: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = StringBuilder()
        fun flushPara() {
            val text = para.toString().trim()
            if (text.isNotEmpty()) {
                out += Block.Paragraph(text)
            }
            para.clear()
        }
        for (raw in source.lineSequence()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushPara()
                trimmed.startsWith("### ") -> {
                    flushPara()
                    out += Block.Heading(3, trimmed.removePrefix("### ").trim())
                }
                trimmed.startsWith("## ") -> {
                    flushPara()
                    out += Block.Heading(2, trimmed.removePrefix("## ").trim())
                }
                trimmed.startsWith("# ") -> {
                    flushPara()
                    out += Block.Heading(1, trimmed.removePrefix("# ").trim())
                }
                trimmed.startsWith("- ") -> {
                    flushPara()
                    out += Block.Bullet(trimmed.removePrefix("- ").trim())
                }
                else -> {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(trimmed)
                }
            }
        }
        flushPara()
        return out
    }
}

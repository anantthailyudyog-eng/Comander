package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatMessage
import com.example.data.MessageRole
import com.example.data.ToolToast
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldNominal
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.ObsidianVoid

@Composable
fun HudBackgroundCanvas(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.08f),
                            ObsidianVoid
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.25f),
                        radius = size.maxDimension * 0.7f
                    )
                )
                val step = 48.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.045f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.045f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
            }
    ) {
        content()
    }
}

@Composable
fun HudGlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = ObsidianGlass,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}

@Composable
fun HudSectionHeader(
    title: String,
    badgeText: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun HudToastOverlay(
    toasts: List<ToolToast>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        toasts.forEach { toast ->
            val accent = if (toast.isAlert) AmberAlert else MaterialTheme.colorScheme.primary
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = ObsidianPanel.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (toast.isAlert) Icons.Default.WarningAmber else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = toast.toolName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                            Text(
                                text = toast.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = HudTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onDismiss(toast.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                tint = HudTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed interface TerminalSegment {
    data class Prose(val text: String) : TerminalSegment
    data class CodeBlock(val language: String, val code: String) : TerminalSegment
}

fun parseTerminalSegments(raw: String): List<TerminalSegment> {
    val regex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
    val segments = mutableListOf<TerminalSegment>()
    var cursor = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > cursor) {
            val prose = raw.substring(cursor, match.range.first).trim()
            if (prose.isNotEmpty()) segments += TerminalSegment.Prose(prose)
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        segments += TerminalSegment.CodeBlock(lang, code)
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = raw.substring(cursor).trim()
        if (tail.isNotEmpty()) segments += TerminalSegment.Prose(tail)
    }
    if (segments.isEmpty()) segments += TerminalSegment.Prose(raw)
    return segments
}

@Composable
fun RichTerminalMessageCard(
    message: ChatMessage,
    onCopySuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val accent = when (message.role) {
        MessageRole.USER -> MaterialTheme.colorScheme.secondary
        MessageRole.JARVIS -> MaterialTheme.colorScheme.primary
        MessageRole.TOOL -> EmeraldNominal
        MessageRole.ERROR -> CrimsonAlert
    }
    val roleLabel = when (message.role) {
        MessageRole.USER -> "OPERATOR"
        MessageRole.JARVIS -> "J.A.R.V.I.S."
        MessageRole.TOOL -> "AUTONOMOUS SUBSYSTEM"
        MessageRole.ERROR -> "SYSTEM FAULT"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = when (message.role) {
            MessageRole.USER -> ObsidianElevated.copy(alpha = 0.72f)
            MessageRole.TOOL -> ObsidianPanel.copy(alpha = 0.78f)
            MessageRole.ERROR -> CrimsonAlert.copy(alpha = 0.12f)
            MessageRole.JARVIS -> ObsidianPanel.copy(alpha = 0.92f)
        },
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$roleLabel  //  ${message.timestampLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_code),
                    tint = HudTextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(message.text))
                            onCopySuccess()
                        }
                )
            }

            if (message.attachmentNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.attachmentNames.forEach { name ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val segments = parseTerminalSegments(message.text)
            segments.forEachIndexed { index, segment ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                when (segment) {
                    is TerminalSegment.Prose -> {
                        Text(
                            text = segment.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.role == MessageRole.ERROR) CrimsonAlert else HudTextPrimary
                        )
                    }
                    is TerminalSegment.CodeBlock -> {
                        SyntaxHighlightedCodeBox(
                            language = segment.language,
                            code = segment.code,
                            accent = accent,
                            onCopy = {
                                clipboard.setText(AnnotatedString(segment.code))
                                onCopySuccess()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyntaxHighlightedCodeBox(
    language: String,
    code: String,
    accent: Color,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianVoid, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianElevated.copy(alpha = 0.8f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onCopy)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_code),
                    tint = HudTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "COPY",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
            }
        }

        val highlighted = buildSyntaxAnnotated(code, accent)
        Text(
            text = highlighted,
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
        )
    }
}

private fun buildSyntaxAnnotated(code: String, accent: Color): AnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when",
        "suspend", "private", "public", "import", "package", "def", "async", "await", "const",
        "let", "function", "for", "while", "try", "catch", "true", "false", "null"
    )
    return buildAnnotatedString {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                withStyle(SpanStyle(color = HudTextMuted)) { append(line) }
            } else {
                val tokens = Regex("(\"[^\"]*\"|'[^']*'|\\b[a-zA-Z_][a-zA-Z0-9_]*\\b|\\d+|\\s+|.)").findAll(line)
                for (t in tokens) {
                    val v = t.value
                    when {
                        v.startsWith("\"") || v.startsWith("'") ->
                            withStyle(SpanStyle(color = EmeraldNominal)) { append(v) }
                        v in keywords ->
                            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(v) }
                        v.all { it.isDigit() } && v.isNotEmpty() ->
                            withStyle(SpanStyle(color = AmberAlert)) { append(v) }
                        else ->
                            withStyle(SpanStyle(color = HudTextPrimary)) { append(v) }
                    }
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

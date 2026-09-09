package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText
import `fun`.kirari.hanako.feature.overlay.state.AnswerOverlayContent
import `fun`.kirari.hanako.feature.overlay.state.AnswerOverlayKind

private const val ChoiceBigTextMaxChars = 8
private const val AnswerBodyMaxLines = 6
private const val FallbackBodyLineHeightSp = 24f
private const val FallbackScreenWidthDp = 360f
private const val FallbackScreenHeightDp = 640f

/**
 * 自动模式答案浮层卡片。
 *
 * 需要外层提供 [MaterialTheme]（由 `AnswerOverlayWindowController` 的 ComposeView 包 `HanakoTheme`）。
 * 卡片自身负责拖动、复制、关闭与「解析」折叠，窗口定位由控制器完成。
 */
@Composable
internal fun AnswerOverlayCard(
    content: AnswerOverlayContent,
    onDrag: (Float, Float) -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    onOpenResultPanel: () -> Unit
) {
    val displayMetrics = LocalContext.current.resources.displayMetrics
    val density = displayMetrics.density
    val screenWidthDp = if (displayMetrics.widthPixels > 0 && density > 0f) {
        displayMetrics.widthPixels / density
    } else {
        FallbackScreenWidthDp
    }
    val screenHeightDp = if (displayMetrics.heightPixels > 0 && density > 0f) {
        displayMetrics.heightPixels / density
    } else {
        FallbackScreenHeightDp
    }
    val maxCardWidth = minOf(300f, screenWidthDp * 0.78f).dp
    val maxThoughtHeight = (screenHeightDp * 0.4f).dp
    var thoughtExpanded by remember(content.resultId) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = maxCardWidth)
            .pointerInput(content.resultId) {
                detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
            }
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = content.cardTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            AnswerOverlayBody(
                content = content,
                onCopy = onCopy
            )
            if (thoughtExpanded && content.thoughtText.isNotBlank()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxThoughtHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownLatexText(
                        content = content.thoughtText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("复制")
                }
                if (content.kind != AnswerOverlayKind.FALLBACK && content.thoughtText.isNotBlank()) {
                    TextButton(
                        onClick = { thoughtExpanded = !thoughtExpanded },
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(if (thoughtExpanded) "解析 ▴" else "解析 ▾")
                    }
                }
                if (content.kind == AnswerOverlayKind.FALLBACK) {
                    TextButton(
                        onClick = onOpenResultPanel,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("打开结果面板")
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerOverlayBody(
    content: AnswerOverlayContent,
    onCopy: () -> Unit
) {
    val useBigText = content.kind != AnswerOverlayKind.TEXT &&
        content.answerText.length <= ChoiceBigTextMaxChars
    if (useBigText) {
        Text(
            text = content.answerText,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp)
                .clickable(onClick = onCopy)
        )
        return
    }
    val lineHeightSp = MaterialTheme.typography.bodyLarge.lineHeight.value
        .takeIf { it > 0f }
        ?: FallbackBodyLineHeightSp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (lineHeightSp * AnswerBodyMaxLines).dp)
            .verticalScroll(rememberScrollState())
            .clickable(onClick = onCopy)
            .padding(horizontal = 2.dp, vertical = 8.dp)
    ) {
        MarkdownLatexText(
            content = content.answerText,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun AnswerOverlayContent.cardTitle(): String = when (kind) {
    AnswerOverlayKind.CHOICE -> "答案"
    AnswerOverlayKind.TEXT -> if (copiedToClipboard) "答案 · 已复制" else "答案"
    AnswerOverlayKind.FALLBACK -> "未给出结构化答案"
}

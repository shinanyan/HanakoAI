package `fun`.kirari.hanako.feature.overlay.state

import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.ProcessingResult

internal enum class AnswerOverlayKind {
    CHOICE,
    TEXT,
    FALLBACK
}

internal data class AnswerOverlayContent(
    val resultId: String,
    val kind: AnswerOverlayKind,
    val answerText: String,
    val thoughtText: String,
    val copiedToClipboard: Boolean,
    val createdAtMillis: Long
)

/** 从自动化结果推导浮层内容；无可展示内容时返回 null。 */
internal fun buildAnswerOverlayContent(result: ProcessingResult): AnswerOverlayContent? {
    val thought = result.automationThought.trim()
    val action = result.automationAction
    if (action == null) {
        val fallback = thought.takeIf(String::isNotBlank) ?: return null
        return AnswerOverlayContent(
            resultId = result.id,
            kind = AnswerOverlayKind.FALLBACK,
            answerText = fallback.take(200),
            thoughtText = fallback,
            copiedToClipboard = false,
            createdAtMillis = result.createdAtMillis
        )
    }
    val text = action.text.trim().takeIf(String::isNotEmpty) ?: return null
    return when (action.type) {
        AutomationActionType.SHOW_BUBBLE_LETTERS ->
            AnswerOverlayContent(result.id, AnswerOverlayKind.CHOICE, text, thought, false, result.createdAtMillis)

        AutomationActionType.SET_CLIPBOARD ->
            AnswerOverlayContent(result.id, AnswerOverlayKind.TEXT, text, thought, true, result.createdAtMillis)
    }
}

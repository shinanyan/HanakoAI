package `fun`.kirari.hanako.feature.overlay.state

import `fun`.kirari.hanako.core.model.AutomationActionRecord
import `fun`.kirari.hanako.core.model.AutomationActionType
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerOverlayModelsTest {

    @Test
    fun showBubbleLetters_producesChoiceContent() {
        val content = requireNotNull(
            buildAnswerOverlayContent(
                result(
                    action = AutomationActionRecord(AutomationActionType.SHOW_BUBBLE_LETTERS, "AB"),
                    thought = "先看题干"
                )
            )
        )

        assertEquals("result-1", content.resultId)
        assertEquals(AnswerOverlayKind.CHOICE, content.kind)
        assertEquals("AB", content.answerText)
        assertEquals("先看题干", content.thoughtText)
        assertFalse(content.copiedToClipboard)
        assertEquals(1234L, content.createdAtMillis)
    }

    @Test
    fun setClipboard_producesTextContentMarkedCopied() {
        val content = requireNotNull(
            buildAnswerOverlayContent(
                result(action = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "42"))
            )
        )

        assertEquals(AnswerOverlayKind.TEXT, content.kind)
        assertEquals("42", content.answerText)
        assertTrue(content.copiedToClipboard)
    }

    @Test
    fun missingAction_withThought_producesFallbackTruncatedTo200Chars() {
        val thought = "思".repeat(260)
        val content = requireNotNull(
            buildAnswerOverlayContent(result(action = null, thought = thought))
        )

        assertEquals(AnswerOverlayKind.FALLBACK, content.kind)
        assertEquals(200, content.answerText.length)
        assertEquals(thought, content.thoughtText)
        assertFalse(content.copiedToClipboard)
    }

    @Test
    fun missingAction_withBlankThought_returnsNull() {
        assertNull(buildAnswerOverlayContent(result(action = null, thought = "   \n")))
    }

    @Test
    fun blankActionText_returnsNull() {
        assertNull(
            buildAnswerOverlayContent(
                result(action = AutomationActionRecord(AutomationActionType.SHOW_BUBBLE_LETTERS, "   "))
            )
        )
        assertNull(
            buildAnswerOverlayContent(
                result(action = AutomationActionRecord(AutomationActionType.SET_CLIPBOARD, "\n"))
            )
        )
    }

    private fun result(
        action: AutomationActionRecord? = null,
        thought: String = ""
    ): ProcessingResult = ProcessingResult(
        id = "result-1",
        assistantName = "题目解答助手",
        route = ProcessingRoute.OCR_THEN_LLM,
        automationThought = thought,
        automationAction = action,
        createdAtMillis = 1234L
    )
}

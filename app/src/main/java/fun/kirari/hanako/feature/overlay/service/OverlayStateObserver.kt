package `fun`.kirari.hanako.feature.overlay.service

import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.feature.overlay.presentation.OverlayViewModel
import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import `fun`.kirari.hanako.feature.overlay.window.AnswerOverlayWindowController
import `fun`.kirari.hanako.feature.overlay.window.BubbleWindowController
import `fun`.kirari.hanako.feature.overlay.window.PanelWindowController
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class OverlayStateObserver(
    private val scope: CoroutineScope,
    private val viewModel: OverlayViewModel,
    private val bubbleWindowController: BubbleWindowController,
    private val panelWindowController: PanelWindowController,
    private val answerOverlayWindowController: AnswerOverlayWindowController
) {
    private val logTag = "HanakoOverlayState"
    private var completionResetJob: Job? = null
    private var lastHandledCompletionId: String? = null

    fun start(): Job {
        return scope.launch {
            viewModel.uiState.collect { state ->
                AppDebugLogStore.d(
                    logTag,
                    "uiState launchMode=${state.launchMode} autoRunState=${state.autoRunState} bubble=${state.bubbleState::class.simpleName} sheetVisible=${state.sheetVisible} working=${state.working} resultId=${state.result?.id} answerOverlayId=${state.answerOverlay?.resultId} overlayEnabled=${state.settings.automation.answerOverlayEnabled} error=${state.error}"
                )
                bubbleWindowController.update(state.bubbleState, state.launchMode)
                if (state.sheetVisible) {
                    panelWindowController.showOrUpdate(state.sheetMode)
                } else {
                    panelWindowController.hideWithAnimation()
                }
                val overlay = state.answerOverlay
                val overlayAllowed = state.settings.automation.answerOverlayEnabled
                if (overlay != null && overlayAllowed && state.launchMode == CaptureLaunchMode.AUTO) {
                    answerOverlayWindowController.showOrUpdate(overlay, bubbleWindowController.getBubbleCenter())
                } else {
                    // 顺带清状态：否则关掉开关后再打开，观察者会把上一轮的旧卡片重新放回来。
                    // 清状态只触发一次发射，下一轮 overlay == null 走到这里就停了，不会成环。
                    if (overlay != null) {
                        AppDebugLogStore.i(
                            logTag,
                            "answerOverlay suppressed launchMode=${state.launchMode} enabled=$overlayAllowed resultId=${overlay.resultId}"
                        )
                        viewModel.dismissAnswerOverlay()
                    }
                    answerOverlayWindowController.hideNow()
                }
                scheduleCompletionReset(state)
            }
        }
    }

    fun stop() {
        completionResetJob?.cancel()
        completionResetJob = null
    }

    private fun scheduleCompletionReset(state: OverlayUiState) {
        if (state.autoRunState != AutoRunState.COMPLETED) {
            completionResetJob?.cancel()
            completionResetJob = null
            return
        }
        val completionId = state.result?.id ?: return
        if (completionId == lastHandledCompletionId) return
        lastHandledCompletionId = completionId
        completionResetJob?.cancel()
        completionResetJob = scope.launch {
            delay(AUTO_COMPLETED_VISIBLE_MS)
            viewModel.consumeAutoCompletedState()
        }
    }

    private companion object {
        const val AUTO_COMPLETED_VISIBLE_MS = 2800L
    }
}

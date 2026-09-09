package `fun`.kirari.hanako.feature.overlay.presentation

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState

import android.content.Context
import `fun`.kirari.hanako.feature.overlay.state.BubbleEvent
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.platform.capture.ScreenCaptureManager
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MultiPageCaptureController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val bubbleStateMachine: BubbleStateMachine
) {
    private val tag = "HanakoMultiPageCapture"

    fun enter() {
        AppDebugLogStore.i(tag, "enter")
        // 清窗口必须和清状态成对：dispatch 会经 OverlayViewModel 的状态机 collect 间接发射 uiState，
        // 若 answerOverlay 仍非空，观察者会把卡片在 hideNow() 之后放回来（多图下每次截图都会闪回）。
        uiState.update { it.copy(answerOverlay = null) }
        bubbleStateMachine.dispatch(BubbleEvent.EnterMultiPageCapture)
    }

    fun capturePage() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "capturePage called state=${currentState::class.simpleName}")
        if (currentState !is BubbleState.MultiPageCapture) {
            AppDebugLogStore.i(tag, "capturePage called but not in MultiPageCapture state")
            return
        }

        bubbleStateMachine.dispatch(BubbleEvent.CaptureStart)
        AppDebugLogStore.i(tag, "capturePage dispatched CaptureStart, new state=${bubbleStateMachine.currentState::class.simpleName}")

        scope.launch {
            // 截图前同步隐藏答案浮层：清状态 + gate 都必须在主线程、且都在 withContext(IO) 之外。
            val hadOverlay = uiState.value.answerOverlay != null
            uiState.update { it.copy(answerOverlay = null) }
            AnswerOverlayGate.hideNow()
            if (hadOverlay) delay(AutoProcessingController.AnswerOverlayDismissSettleMs)
            runCatching {
                withContext(Dispatchers.IO) {
                    ScreenCaptureManager.captureLatestBitmap(appContext, uiState.value.settings.screenCaptureMethod)
                }
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "capturePage success width=${bitmap.width} height=${bitmap.height}")
                bubbleStateMachine.dispatch(BubbleEvent.CaptureTaken(bitmap))
                AppDebugLogStore.i(tag, "capturePage dispatched CaptureTaken, new state=${bubbleStateMachine.currentState::class.simpleName}")
                launch {
                    kotlinx.coroutines.delay(2000)
                    bubbleStateMachine.dispatch(BubbleEvent.CaptureSuccessAnimationDone)
                    AppDebugLogStore.i(tag, "capturePage dispatched CaptureSuccessAnimationDone, new state=${bubbleStateMachine.currentState::class.simpleName}")
                }
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "capturePage failed", error)
                uiState.update { it.copy(error = error.message ?: "截图失败") }
                bubbleStateMachine.dispatch(BubbleEvent.CaptureFailed)
            }
        }
    }

    fun capturedBitmaps() = bubbleStateMachine.getCapturedBitmaps()

    fun canSendCaptures(): Boolean = bubbleStateMachine.canSendCaptures()

    fun markSendCaptures() {
        bubbleStateMachine.dispatch(BubbleEvent.SendCaptures)
    }

    fun exit() {
        AppDebugLogStore.i(tag, "exit")
        bubbleStateMachine.dispatch(BubbleEvent.DoubleTap)
    }
}

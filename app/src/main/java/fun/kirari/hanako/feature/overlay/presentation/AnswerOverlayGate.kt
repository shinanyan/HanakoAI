package `fun`.kirari.hanako.feature.overlay.presentation

/**
 * 截图前同步隐藏答案浮层的统一入口。
 *
 * 两条截图路径（[AutoProcessingController.processFullScreen]、[MultiPageCaptureController.capturePage]）
 * 在进入截图前都必须先清 `uiState.answerOverlay`，再调用 [hideNow]。
 *
 * **必须在主线程调用**：[hideNow] 最终走到 `WindowManager.removeViewImmediate`，
 * 在 `withContext(Dispatchers.IO)` 里调用会抛 `CalledFromWrongThreadException`。
 *
 * 用 `object` 而不是 ViewModel 回调：两个控制器都在 `OverlayViewModel` 字段初始化期构造，
 * 早于 `OverlayService` 创建窗口控制器，晚绑定回调拿不到实例。
 */
internal object AnswerOverlayGate {
    @Volatile
    var hideNow: () -> Unit = {}
}

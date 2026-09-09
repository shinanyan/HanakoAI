package `fun`.kirari.hanako.feature.overlay.window

import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import `fun`.kirari.hanako.core.data.AutomationSettings
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.ui.theme.HanakoTheme
import `fun`.kirari.hanako.feature.overlay.state.AnswerOverlayContent
import `fun`.kirari.hanako.feature.overlay.ui.AnswerOverlayCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 自动模式答案浮层的窗口控制器。
 *
 * 生命周期比 [BubbleWindowController] / [PanelWindowController] 更简单：
 * 只有一张自由定位的 wrap-content 小卡片，没有动画，[hideNow] 供截图前同步隐藏。
 *
 * [showOrUpdate] 必须幂等：流式输出期间 uiState 每个 token 都会发射一次，
 * 同一 resultId 只更新文案，不重新锚定位置，避免用户拖动后的卡片弹回悬浮球上方。
 */
internal class AnswerOverlayWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val settingsProvider: () -> AutomationSettings,
    private val onCopy: (AnswerOverlayContent) -> Unit,
    private val onDismiss: () -> Unit,
    private val onOpenResultPanel: () -> Unit
) {
    private val logTag = "HanakoAnswerOverlay"
    private val contentState = mutableStateOf<AnswerOverlayContent?>(null)
    private var cardView: ComposeView? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var dismissJob: Job? = null
    private var positionRetries = 0

    fun showOrUpdate(content: AnswerOverlayContent, anchor: Pair<Int, Int>?) {
        val isNewCard = contentState.value?.resultId != content.resultId
        contentState.value = content
        val view = ensureView() ?: return
        if (!isNewCard) {
            // 同一张卡片：保留用户拖动后的位置，只更新文案与解析内容。
            return
        }
        AppDebugLogStore.i(logTag, "show resultId=${content.resultId} kind=${content.kind}")
        restartAutoDismissTimer()
        applyRoughPosition(anchor)
        positionRetries = 0
        view.post { positionWhenMeasured(anchor) }
    }

    /**
     * 无动画立即移除窗口，供截图前同步隐藏使用。
     *
     * 必须在主线程调用：调用点只有截图入口（`AnswerOverlayGate`）、
     * 状态观察者的 else 分支和 Compose 回调，都在主线程。
     */
    fun hideNow() {
        dismissJob?.cancel()
        dismissJob = null
        positionRetries = 0
        contentState.value = null
        val view = cardView
        cardView = null
        cardParams = null
        if (view == null) return
        AppDebugLogStore.i(logTag, "hideNow")
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { AppDebugLogStore.e(logTag, "failed to remove answer overlay window", it) }
    }

    fun moveBy(dx: Int, dy: Int) {
        val view = cardView ?: return
        val params = cardParams ?: return
        if (view.width <= 0 || view.height <= 0) return
        val metrics = screenMetrics()
        val margin = edgeMarginPx()
        val maxX = (metrics.widthPixels - view.width - margin).coerceAtLeast(margin)
        val maxY = (metrics.heightPixels - view.height - margin).coerceAtLeast(margin)
        params.x = (params.x + dx).coerceIn(margin, maxX)
        params.y = (params.y + dy).coerceIn(margin, maxY)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    fun destroy() {
        hideNow()
    }

    private fun ensureView(): ComposeView? {
        cardView?.let { return it }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            setContent {
                HanakoTheme {
                    val content = contentState.value
                    if (content != null) {
                        AnswerOverlayCard(
                            content = content,
                            onDrag = ::handleDrag,
                            onCopy = { onCopy(content) },
                            onDismiss = {
                                hideNow()
                                onDismiss()
                            },
                            onOpenResultPanel = {
                                hideNow()
                                onOpenResultPanel()
                            }
                        )
                    }
                }
            }
        }
        cardView = view
        cardParams = params
        runCatching {
            windowManager.addView(view, params)
            AntiScreenshotHelper.applyTo(view)
        }.onFailure { error ->
            AppDebugLogStore.e(logTag, "failed to add answer overlay window", error)
            cardView = null
            cardParams = null
            contentState.value = null
            return null
        }
        return view
    }

    private fun handleDrag(dx: Float, dy: Float) {
        moveBy(dx.roundToInt(), dy.roundToInt())
    }

    private fun restartAutoDismissTimer() {
        dismissJob?.cancel()
        dismissJob = null
        val seconds = settingsProvider().answerOverlayAutoDismissSeconds
        if (seconds <= 0) return
        dismissJob = scope.launch {
            delay(seconds * 1000L)
            hideNow()
            onDismiss()
        }
    }

    /** 首帧粗定位：卡片尚未测量，先用估算尺寸放在锚点附近，避免闪到 (0,0)。 */
    private fun applyRoughPosition(anchor: Pair<Int, Int>?) {
        val view = cardView ?: return
        val params = cardParams ?: return
        val density = context.resources.displayMetrics.density
        val metrics = screenMetrics()
        val margin = edgeMarginPx()
        val estimateWidth = (RoughEstimateWidthDp * density).roundToInt()
        val estimateHeight = (RoughEstimateHeightDp * density).roundToInt()
        val anchorX = anchor?.first ?: (metrics.widthPixels / 2)
        val anchorY = anchor?.second ?: margin
        params.x = (anchorX - estimateWidth / 2)
            .coerceIn(margin, (metrics.widthPixels - estimateWidth - margin).coerceAtLeast(margin))
        params.y = (anchorY - estimateHeight - margin)
            .coerceIn(margin, (metrics.heightPixels - estimateHeight - margin).coerceAtLeast(margin))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun positionWhenMeasured(anchor: Pair<Int, Int>?) {
        val view = cardView ?: return
        if (view.width > 0 && view.height > 0) {
            applyPosition(anchor, view.width, view.height)
            return
        }
        if (positionRetries >= MaxPositionRetries) return
        positionRetries++
        view.post { positionWhenMeasured(anchor) }
    }

    private fun applyPosition(anchor: Pair<Int, Int>?, width: Int, height: Int) {
        val view = cardView ?: return
        val params = cardParams ?: return
        val density = context.resources.displayMetrics.density
        val metrics = screenMetrics()
        val margin = edgeMarginPx()
        val gap = (AnchorGapDp * density).roundToInt()
        val bubbleRadius = visibleBubbleRadiusPx(density)
        val anchorX = anchor?.first ?: (metrics.widthPixels / 2)
        val anchorY = anchor?.second
        val targetY = if (anchorY == null) {
            margin
        } else {
            val above = anchorY - height - gap
            if (above >= margin) above else anchorY + bubbleRadius + gap
        }
        params.x = (anchorX - width / 2)
            .coerceIn(margin, (metrics.widthPixels - width - margin).coerceAtLeast(margin))
        params.y = targetY
            .coerceIn(margin, (metrics.heightPixels - height - margin).coerceAtLeast(margin))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /**
     * 悬浮球实际可见内容的半径。
     *
     * 取 `BubbleWindowController.computeRootSizePx()` 里 contentSizePx 的那一项
     * （球体 / 转圈 / 字母层的最大值），而不是 `bubbleDiameterDp / 2`：
     * 字母态下字母层是 `letterTextSizeDp * 1.8`，默认就比球体大，用球径当半径会让卡片压住字母。
     */
    private fun visibleBubbleRadiusPx(density: Float): Int {
        val appearance = settingsProvider().bubbleAppearance
        val contentDp = maxOf(
            appearance.bubbleDiameterDp,
            appearance.spinnerDiameterDp,
            appearance.letterTextSizeDp * 1.8f
        )
        return (contentDp / 2f * density).roundToInt()
    }

    private fun edgeMarginPx(): Int =
        (EdgeMarginDp * context.resources.displayMetrics.density).roundToInt()

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private companion object {
        const val EdgeMarginDp = 8f
        const val AnchorGapDp = 12f
        const val RoughEstimateWidthDp = 200f
        const val RoughEstimateHeightDp = 120f
        const val MaxPositionRetries = 8
    }
}

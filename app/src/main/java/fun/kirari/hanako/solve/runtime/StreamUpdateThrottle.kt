package `fun`.kirari.hanako.solve.runtime

/**
 * 流式回调的节流器。
 *
 * 模型每秒可能推送几十个 token，而每一个 token 都会连带触发一串代价随数据规模增长的
 * 工作：`WorkflowResultStore` 的全量 map 复制、状态观察者的悬浮窗几何更新、以及
 * Compose 侧整篇 markdown + LaTeX 的重新解析。这里按固定间隔放行中间帧，把频率压到
 * 眼睛跟得上的水平。
 *
 * 内容不会丢：调用方始终累积完整文本，任务完成路径会写入最终值。
 * 首次调用一定放行，所以首帧不会延迟；[minIntervalMillis] <= 0 表示不节流。
 */
internal class StreamUpdateThrottle(private val minIntervalMillis: Long) {
    private var lastAllowedAtMillis = 0L

    fun shouldPublish(): Boolean {
        if (minIntervalMillis <= 0L) return true
        val now = System.currentTimeMillis()
        if (now - lastAllowedAtMillis < minIntervalMillis) return false
        lastAllowedAtMillis = now
        return true
    }
}

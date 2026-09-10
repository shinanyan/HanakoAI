package `fun`.kirari.hanako.feature.history.ui

import `fun`.kirari.hanako.core.model.ProcessingStatus

internal fun ProcessingStatus.displayName(): String = when (this) {
    ProcessingStatus.RUNNING -> "进行中"
    ProcessingStatus.SUCCESS -> "成功"
    ProcessingStatus.ERROR -> "失败"
    ProcessingStatus.TIMEOUT -> "超时"
}

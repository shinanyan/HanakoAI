package `fun`.kirari.hanako.feature.settings.ui.automation

import `fun`.kirari.hanako.feature.settings.ui.settings.SwitchSettingRow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AutomationSettings

@Composable
internal fun AutoModeSettingsCard(
    automationSettings: AutomationSettings,
    timeoutInput: String,
    hasNotificationPermission: Boolean,
    onTimeoutInputChange: (String) -> Unit,
    onToggleCompletionNotification: (Boolean) -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onToggleStaticMode: (Boolean) -> Unit,
    onNavigateStaticVibrationSettings: () -> Unit,
    onUpdateTimeoutSeconds: (Int) -> Unit,
    onUpdateAutomationSettings: (AutomationSettings) -> Unit
) {
    var dismissSecondsInput by remember(automationSettings.answerOverlayAutoDismissSeconds) {
        mutableStateOf(automationSettings.answerOverlayAutoDismissSeconds.toString())
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SwitchSettingRow(
            title = "完成后发送通知",
            subtitle = if (hasNotificationPermission) {
                "处理完成后发送系统通知。"
            } else {
                "当前未授予 Hanako 通知权限"
            },
            checked = automationSettings.completionNotificationEnabled,
            onCheckedChange = onToggleCompletionNotification,
            onTextClick = onOpenNotificationPermission,
            subtitleColor = if (hasNotificationPermission) {
                null
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
        SwitchSettingRow(
            title = "静态模式",
            subtitle = "隐藏悬浮球动画，使用振动表示识别结果",
            checked = automationSettings.staticModeEnabled,
            onCheckedChange = onToggleStaticMode,
            onTextClick = onNavigateStaticVibrationSettings
        )
        SwitchSettingRow(
            title = "启动时进入自动模式",
            subtitle = "开启后单击主页启动按钮直接进入自动模式，长按改为普通模式；关闭则相反。",
            checked = automationSettings.startInAutoMode,
            onCheckedChange = { enabled ->
                onUpdateAutomationSettings(automationSettings.copy(startInAutoMode = enabled))
            }
        )
        SwitchSettingRow(
            title = "答案浮层",
            subtitle = "自动模式完成后在屏幕上显示答案卡片",
            checked = automationSettings.answerOverlayEnabled,
            onCheckedChange = { enabled ->
                onUpdateAutomationSettings(automationSettings.copy(answerOverlayEnabled = enabled))
            }
        )
        NumberField(
            icon = Icons.Default.CropFree,
            title = "浮层自动关闭",
            description = "浮层出现后多少秒自动关闭，0 表示不自动关闭。",
            label = "秒",
            footer = "默认 0 秒，即一直显示到手动关闭。",
            value = dismissSecondsInput,
            onValueChange = { value ->
                val digits = value.filter(Char::isDigit)
                dismissSecondsInput = digits
                digits.toIntOrNull()?.let { seconds ->
                    onUpdateAutomationSettings(
                        automationSettings.copy(answerOverlayAutoDismissSeconds = seconds)
                    )
                }
            }
        )
        NumberField(
            icon = Icons.Default.Timer,
            title = "自动模式超时时间",
            description = "首字延迟超时，当前用于流式请求在收到第一段输出前的等待时间。",
            label = "秒",
            footer = "默认 30 秒。",
            value = timeoutInput,
            onValueChange = { value ->
                val digits = value.filter(Char::isDigit)
                onTimeoutInputChange(digits)
                digits.toIntOrNull()?.takeIf { it > 0 }?.let(onUpdateTimeoutSeconds)
            }
        )
    }
}

@Composable
private fun NumberField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    label: String,
    footer: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp)
        )
        Text(
            footer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

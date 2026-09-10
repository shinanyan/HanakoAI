package `fun`.kirari.hanako.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.model.migrateBase64ToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "hanako_settings")

/**
 * 设置与历史记录的序列化配置。升级兼容测试必须复用这个实例，
 * 否则生产配置被改坏（例如删掉 coerceInputValues）时测试仍然会通过。
 *
 * coerceInputValues = true：枚举值被移除后，旧数据里的未知枚举值会退化为属性默认值，
 * 而不是抛异常、触发 SettingsStore 的「整份重置」兜底。
 */
internal val HanakoSettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

class SettingsStore(private val context: Context) {
    private val tag = "HanakoSettingsStore"

    val settings: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[SETTINGS_KEY]
            if (raw.isNullOrBlank()) {
                AppSettings().normalize()
            } else {
                runCatching { HanakoSettingsJson.decodeFromString<AppSettings>(raw).normalize() }
                    .getOrElse { AppSettings().normalize() }
            }
        }
        .flatMapConcat { appSettings ->
            val migrated = migrateHistoryImages(context, appSettings)
            if (migrated != appSettings) {
                flow {
                    persistMigrated(migrated)
                    emit(migrated)
                }
            } else {
                flow { emit(appSettings) }
            }
        }
        // 反序列化整份设置（含全部历史与 base64 截图）以及历史图片迁移都是重活，
        // 必须在 IO 线程执行：collector 大多是 viewModelScope（Main.immediate）。
        .flowOn(Dispatchers.IO)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { preferences ->
            val currentRaw = preferences[SETTINGS_KEY]
            val current = if (currentRaw.isNullOrBlank()) {
                AppSettings().normalize()
            } else {
                runCatching { HanakoSettingsJson.decodeFromString<AppSettings>(currentRaw).normalize() }
                    .getOrElse { AppSettings().normalize() }
            }
            val updated = transform(current).normalize()
            AppDebugLogStore.i(
                tag,
                "update lastResultId=${updated.lastResult?.id} historySize=${updated.history.size} latestHistoryId=${updated.history.firstOrNull()?.id}"
            )
            preferences[SETTINGS_KEY] = HanakoSettingsJson.encodeToString(AppSettings.serializer(), updated)
        }
    }

    suspend fun read(): AppSettings = settings.first()

    private suspend fun persistMigrated(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[SETTINGS_KEY] = HanakoSettingsJson.encodeToString(AppSettings.serializer(), settings)
        }
    }

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("app_settings")

        private fun migrateHistoryImages(context: Context, settings: AppSettings): AppSettings {
            val needsMigration = settings.history.any { it.screenshotBase64 != null && it.screenshotPath == null } ||
                (settings.lastResult?.let { it.screenshotBase64 != null && it.screenshotPath == null } == true)
            if (!needsMigration) return settings

            val migratedHistory = settings.history.map { result ->
                migrateBase64ToFile(context, result)
            }
            val migratedLastResult = settings.lastResult?.let { migrateBase64ToFile(context, it) }
            return settings.copy(history = migratedHistory, lastResult = migratedLastResult)
        }
    }
}

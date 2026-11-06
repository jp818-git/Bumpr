package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppTheme
import com.example.model.BackgroundStyle
import com.example.model.NoLyricsDisplay
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bumpr_settings")

class BumprPreferences(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_APP_THEME = stringPreferencesKey("app_theme")
        val KEY_BACKGROUND_STYLE = stringPreferencesKey("bg_style")
        val KEY_NO_LYRICS_DISPLAY = stringPreferencesKey("no_lyrics_display")
        val KEY_SHOW_LYRICS_IN_CAR = booleanPreferencesKey("show_lyrics_in_car")
        val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val KEY_LYRICS_FONT_SIZE = floatPreferencesKey("lyrics_font_size")
        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        val KEY_BASS_PERCENT = intPreferencesKey("bass_percent")
        val KEY_TREBLE_DB = intPreferencesKey("treble_db")
        val KEY_SELECTED_FOLDERS = stringSetPreferencesKey("selected_folders")
    }

    val appThemeFlow: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_APP_THEME]) {
            AppTheme.SOUNDWAVE.name -> AppTheme.SOUNDWAVE
            AppTheme.SUNSET.name -> AppTheme.SUNSET
            AppTheme.CYBERPUNK.name -> AppTheme.CYBERPUNK
            AppTheme.AMETHYST.name -> AppTheme.AMETHYST
            AppTheme.TURQUOISE.name -> AppTheme.TURQUOISE
            AppTheme.AMOLED.name -> AppTheme.AMOLED
            AppTheme.GLASS.name -> AppTheme.GLASS
            else -> AppTheme.NEON_CYAN
        }
    }

    val backgroundStyleFlow: Flow<BackgroundStyle> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_BACKGROUND_STYLE]) {
            BackgroundStyle.SOUNDWAVE.name -> BackgroundStyle.SOUNDWAVE
            BackgroundStyle.AURORA_GRADIENT.name -> BackgroundStyle.AURORA_GRADIENT
            BackgroundStyle.MINIMAL.name -> BackgroundStyle.MINIMAL
            else -> BackgroundStyle.MUSICAL_STAVE // Default to the glowing musical staves image
        }
    }

    val noLyricsDisplayFlow: Flow<NoLyricsDisplay> = context.dataStore.data.map { prefs ->
        if (prefs[KEY_NO_LYRICS_DISPLAY] == NoLyricsDisplay.ANIMATED_WAVE.name) NoLyricsDisplay.ANIMATED_WAVE
        else NoLyricsDisplay.ALBUM_ART
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
    }

    val showLyricsInCarFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_LYRICS_IN_CAR] ?: true
    }

    val keepScreenAwakeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEEP_SCREEN_AWAKE] ?: false
    }

    val lyricsFontSizeFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_FONT_SIZE] ?: 20f
    }

    val eqEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EQ_ENABLED] ?: true
    }

    val eqPresetFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_EQ_PRESET] ?: "Pop"
    }

    val bassPercentFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASS_PERCENT] ?: 35
    }

    val trebleDbFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_TREBLE_DB] ?: 2
    }

    val selectedFoldersFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_FOLDERS] ?: emptySet()
    }

    suspend fun setSelectedFolders(folders: Set<String>) {
        context.dataStore.edit { it[KEY_SELECTED_FOLDERS] = folders }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[KEY_APP_THEME] = theme.name }
    }

    suspend fun setNoLyricsDisplay(mode: NoLyricsDisplay) {
        context.dataStore.edit { it[KEY_NO_LYRICS_DISPLAY] = mode.name }
    }

    suspend fun setBackgroundStyle(style: BackgroundStyle) {
        context.dataStore.edit { it[KEY_BACKGROUND_STYLE] = style.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setShowLyricsInCar(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_LYRICS_IN_CAR] = enabled }
    }

    suspend fun setKeepScreenAwake(awake: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_AWAKE] = awake }
    }

    suspend fun setLyricsFontSize(size: Float) {
        context.dataStore.edit { it[KEY_LYRICS_FONT_SIZE] = size }
    }

    suspend fun setEqSettings(enabled: Boolean, preset: String, bass: Int, treble: Int) {
        context.dataStore.edit {
            it[KEY_EQ_ENABLED] = enabled
            it[KEY_EQ_PRESET] = preset
            it[KEY_BASS_PERCENT] = bass
            it[KEY_TREBLE_DB] = treble
        }
    }
}

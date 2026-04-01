package com.motorider.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motorider_settings")

/**
 * Repository for user settings.
 * Uses DataStore to persist settings across app restarts.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private object PreferencesKeys {
        val SPEED_LIMIT_KMH = doublePreferencesKey("speed_limit_kmh")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        val USE_MPH = booleanPreferencesKey("use_mph")
    }

    // Default 120 km/h
    val speedLimitKmh: StateFlow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SPEED_LIMIT_KMH] ?: 120.0
        }
        .stateIn(scope, SharingStarted.Eagerly, 120.0)

    val hapticEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val autoRecordEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_RECORD_ENABLED] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    // Use mph instead of km/h
    val useMph: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_MPH] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun setSpeedLimit(limit: Double) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.SPEED_LIMIT_KMH] = limit
            }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.HAPTIC_ENABLED] = enabled
            }
        }
    }

    fun setAutoRecordEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.AUTO_RECORD_ENABLED] = enabled
            }
        }
    }

    fun setUseMph(useMph: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.USE_MPH] = useMph
            }
        }
    }
}

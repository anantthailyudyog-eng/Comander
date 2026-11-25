package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.example.data.TelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemTelemetryMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _telemetry = MutableStateFlow(TelemetrySnapshot())
    val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

    private var batteryReceiverRegistered = false
    private var networkCallbackRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 100
            _telemetry.update { it.copy(batteryPercent = pct, batteryCharging = charging) }
        }
    }

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetwork()
        override fun onLost(network: Network) = refreshNetwork()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshNetwork()
    }

    init {
        startTelemetry()
    }

    private fun startTelemetry() {
        try {
            val initialBattery = appContext.registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            batteryReceiverRegistered = true
            if (initialBattery != null) {
                batteryReceiver.onReceive(appContext, initialBattery)
            }
        } catch (_: Exception) {
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
            refreshNetwork()
        } catch (_: Exception) {
        }

        scope.launch {
            while (isActive) {
                val now = Date()
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(1L)
                val maxMem = runtime.maxMemory().coerceAtLeast(1L)
                val ramPct = ((usedMem * 100L) / maxMem).toInt().coerceIn(8, 95)

                _telemetry.update {
                    it.copy(
                        clockTime = timeFormatter.format(now),
                        dateLabel = dateFormatter.format(now).uppercase(Locale.US),
                        ramUsagePercent = ramPct
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun refreshNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val label = when {
            !online -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G/LTE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
            else -> "NET-LINK"
        }
        _telemetry.update { it.copy(networkOnline = online, networkLabel = label) }
    }

    fun updateNeuralMetrics(loadPercent: Int, latencyMs: Long? = null) {
        _telemetry.update {
            it.copy(
                neuralLoadPercent = loadPercent.coerceIn(5, 100),
                apiLatencyMs = latencyMs ?: it.apiLatencyMs
            )
        }
    }

    fun release() {
        if (batteryReceiverRegistered) {
            try {
                appContext.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryReceiverRegistered = false
        }
        if (networkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {
            }
            networkCallbackRegistered = false
        }
    }
}

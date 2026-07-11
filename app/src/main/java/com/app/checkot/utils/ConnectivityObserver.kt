package com.app.checkot.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide internet connectivity state. Registered once in MainActivity and
 * observed above the NavHost so every screen is guarded by the same overlay.
 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            // The default network was lost, but another (e.g. cellular after
            // Wi-Fi drops) may take over — re-check instead of assuming offline.
            _isOnline.value = isCurrentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value =
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    private var registered = false

    fun start() {
        if (!registered) {
            connectivityManager.registerDefaultNetworkCallback(callback)
            registered = true
        }
    }

    fun stop() {
        if (registered) {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            registered = false
        }
    }

    // Manual re-check for the Retry button, in case a callback was missed.
    fun refresh() {
        _isOnline.value = isCurrentlyOnline()
    }

    private fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

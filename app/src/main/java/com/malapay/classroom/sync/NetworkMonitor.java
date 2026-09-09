package com.malapay.classroom.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Answers one question — "is there a usable internet connection right now?"
 * — for the sync layer to check before attempting any network call.
 *
 * Also provides a listener-based API so activities/fragments can register
 * a callback that fires when connectivity is RESTORED (not when lost).
 * This enables automatic catch-up sync when the device comes back online.
 */
public final class NetworkMonitor {

    public interface ConnectivityListener {
        void onConnectivityRestored();
    }

    private static volatile ConnectivityManager.NetworkCallback activeCallback;
    private static volatile ConnectivityListener listener;

    private NetworkMonitor() { }

    public static boolean isOnline(@NonNull Context context) {
        ConnectivityManager cm = getConnectivityManager(context);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) return false;
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Start monitoring for connectivity RESTORAL. When the device comes back
     * online, the listener fires once. Safe to call multiple times — cancels
     * any previous registration first.
     */
    public static void startMonitoring(@NonNull Context context, @NonNull ConnectivityListener listener) {
        stopMonitoring(context);
        NetworkMonitor.listener = listener;
        ConnectivityManager cm = getConnectivityManager(context);
        if (cm == null) return;
        activeCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                ConnectivityListener l = NetworkMonitor.listener;
                if (l != null) l.onConnectivityRestored();
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, activeCallback);
    }

    /** Stop monitoring and release the callback. */
    public static void stopMonitoring(@NonNull Context context) {
        listener = null;
        ConnectivityManager cm = getConnectivityManager(context);
        if (cm != null && activeCallback != null) {
            try { cm.unregisterNetworkCallback(activeCallback); } catch (Exception ignored) { }
        }
        activeCallback = null;
    }

    @Nullable
    private static ConnectivityManager getConnectivityManager(Context context) {
        return (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }
}
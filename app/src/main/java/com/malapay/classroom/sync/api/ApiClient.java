package com.malapay.classroom.sync.api;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import com.malapay.classroom.BuildConfig;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds the single Retrofit instance used for all backend calls.
 *
 * The base URL is configured via {@code BuildConfig.BASE_URL}, which is set
 * per build type in {@code app/build.gradle}:
 *   - debug:   LAN IP for same-network testing
 *   - internet: ngrok tunnel URL
 *   - release: your deployed backend domain (replace before publishing)
 */
public final class ApiClient {

    private static final String TAG = "ApiClient";
    private static final String BASE_URL = BuildConfig.BASE_URL;

    private static volatile ApiService apiService;

    private ApiClient() {
        // no instances
    }

    public static ApiService getApiService() {
        if (apiService == null) {
            synchronized (ApiClient.class) {
                if (apiService == null) {
                    try {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .readTimeout(10, TimeUnit.SECONDS)
                                .writeTimeout(10, TimeUnit.SECONDS)
                                .build();

                        Retrofit retrofit = new Retrofit.Builder()
                                .baseUrl(BASE_URL)
                                .client(client)
                                .addConverterFactory(GsonConverterFactory.create())
                                .build();

                        apiService = retrofit.create(ApiService.class);
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Invalid BASE_URL: " + BASE_URL, e);
                        return null;
                    }
                }
            }
        }
        return apiService;
    }
}

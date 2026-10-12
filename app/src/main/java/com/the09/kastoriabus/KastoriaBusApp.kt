package com.the09.kastoriabus

import android.app.Application
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.data.DiskCache
import com.the09.kastoriabus.data.FavoritesStore
import com.the09.kastoriabus.data.LocationHelper
import com.the09.kastoriabus.data.OfflineDownloader
import com.the09.kastoriabus.data.OfflineMapDownloader
import com.the09.kastoriabus.data.remote.AuthInterceptor
import com.the09.kastoriabus.data.remote.CITYBUS_BASE_URL
import com.the09.kastoriabus.data.remote.CitybusApi
import com.the09.kastoriabus.data.remote.TokenAuthenticator
import com.the09.kastoriabus.data.remote.TokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Runs once when the app starts, before any screen. It builds the networking
 * objects a single time, so every screen shares the same repository and caches.
 * Screens get them with: (application as KastoriaBusApp).repository etc.
 */
class KastoriaBusApp : Application() {

    lateinit var repository: BusRepository
        private set
    lateinit var favorites: FavoritesStore
        private set
    lateinit var location: LocationHelper
        private set
    lateinit var offline: OfflineDownloader
        private set

    /** Work that should continue while you switch screens (like the offline download). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // The map library must be set up once before any map is shown.
        MapLibre.getInstance(this)

        // Plain client: used only to download the stops page that holds the token.
        val baseClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val tokens = TokenProvider(this, baseClient)

        // API client: same settings plus the token handling.
        val apiClient = baseClient.newBuilder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens))
            .build()

        val api = Retrofit.Builder()
            .baseUrl(CITYBUS_BASE_URL)
            .client(apiClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CitybusApi::class.java)

        // R.string.api_language is "el" in values/ and "en" in values-en/,
        // so the API language always matches the language of the app's texts.
        // Everything downloaded is also saved on the phone, for use without internet.
        repository = BusRepository(api, DiskCache(filesDir)) { getString(R.string.api_language) }
        favorites = FavoritesStore(this)
        location = LocationHelper(this)
        offline = OfflineDownloader(this, repository, OfflineMapDownloader(this), appScope)
    }
}

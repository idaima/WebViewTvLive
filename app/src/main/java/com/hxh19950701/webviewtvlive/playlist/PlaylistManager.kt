package com.hxh19950701.webviewtvlive.playlist

import android.net.http.HttpException
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.hxh19950701.webviewtvlive.application
import com.hxh19950701.webviewtvlive.preference
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object PlaylistManager {

    private const val TAG = "PlaylistManager"
    private const val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L
    private const val KEY_PLAYLIST_URL = "playlist_url"
    private const val KEY_LAST_UPDATE = "last_update"
    private const val UPDATE_MAX_TRY = 3

    private val client = OkHttpClient()
    private val gson = GsonBuilder().setPrettyPrinting().create()!!
    private val jsonType = object : TypeToken<List<Channel>>() {}
    private val file = File(application.filesDir, "playlist.json")
    private val builtInPlaylists = listOf(
        Pair("中央&湖南", "https://raw.githubusercontent.com/hxh19950701/WebViewTvLive/main/app/channels/%E5%A4%AE%E8%A7%86%26%E6%B9%96%E5%8D%97.json"),
        Pair("完整", "https://raw.githubusercontent.com/hxh19950701/WebViewTvLive/main/app/channels/%E5%AE%8C%E6%95%B4.json"),
    )

    fun getBuiltInPlaylists() = builtInPlaylists

    fun setPlaylistUrl(url: String) {
        preference.edit()
            .putString(KEY_PLAYLIST_URL, url)
            .putLong(KEY_LAST_UPDATE, 0)
            .apply()
    }

    fun getPlaylistUrl() = preference.getString(KEY_PLAYLIST_URL, builtInPlaylists[0].second)!!

    fun setLastUpdate(time: Long) = preference.edit().putLong(KEY_LAST_UPDATE, time).apply()

    private suspend fun updatePlaylist() {
        var times = 0
        while (times < UPDATE_MAX_TRY) {
            Log.i(TAG, "Updating playlist... times: ${times + 1}")
            try {
                val request = Request.Builder().url(getPlaylistUrl()).get().build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Response code ${response.code}")
                }
                file.writeText(response.body!!.string())
                setLastUpdate(System.currentTimeMillis())
                Log.i(TAG, "Update playlist successfully.")
                break
            } catch (e: Exception) {
                Log.w(TAG, "Can't update playlist. ${e.message}")
            }
            ++times
        }
    }

    suspend fun loadPlaylist(): Playlist {
        val updateTime = preference.getLong(KEY_LAST_UPDATE, 0L)
        if (System.currentTimeMillis() - updateTime > CACHE_EXPIRATION_MS) {
            updatePlaylist()
        }
        return try {
            val channels = gson.fromJson(file.readText(), jsonType)
            Playlist.createFromAllChannels("default", channels)
        } catch (e: Exception) {
            Log.w(TAG, "Can't load playlist. ${e.message}")
            setLastUpdate(0)
            loadPlaylist()
        }
    }

}
package com.example.pandatemperature.data.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 前台 NFC 贴片检测：
 * - 使用 ReaderMode 由系统回调提供 Tag；
 * - 不持有 NfcV 长连接，不做轮询 transceive。
 *
 * 支持 pause/resume：发送期间暂停（不再更新 Tag），但 **不关闭 ReaderMode**，
 * 避免底层 NFC handle 失效导致已持有的 Tag 对象无法 connect。
 */
class NfcPresenceDetector(
    private val stateFlow: MutableStateFlow<Boolean>,
    private val tagFlow: MutableStateFlow<Tag?>? = null
) {
    private var adapter: NfcAdapter? = null
    private var activity: Activity? = null
    private var currentTag: Tag? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var paused: Boolean = false

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        mainHandler.post {
            if (!paused) {
                currentTag = tag
                stateFlow.value = true
                tagFlow?.value = tag
            }
        }
    }

    fun start(activity: Activity) {
        if (this.activity == activity && adapter != null) return
        stop()
        this.activity = activity
        adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        if (!adapter!!.isEnabled) return
        paused = false
        val flags = NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            adapter!!.enableReaderMode(activity, readerCallback, flags, extras)
        }
    }

    /**
     * 暂停：停止向外部更新 Tag/在场状态，但 **保持 ReaderMode 开启**，
     * 使已持有的 Tag 对象底层 handle 不会被系统回收。
     */
    fun pause() {
        paused = true
    }

    /**
     * 恢复：重新允许 ReaderMode 回调更新外部状态。
     * 若暂停期间有新 Tag 回调，立即同步最新状态。
     */
    fun resume() {
        paused = false
        val tag = currentTag
        if (tag != null) {
            stateFlow.value = true
            tagFlow?.value = tag
        }
    }

    fun stop() {
        paused = false
        currentTag = null
        activity?.let { act ->
            adapter?.disableReaderMode(act)
        }
        activity = null
        adapter = null
        stateFlow.value = false
        tagFlow?.value = null
    }
}

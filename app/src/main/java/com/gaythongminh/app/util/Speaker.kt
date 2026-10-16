package com.gaythongminh.app.util

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

/** Đọc to thông báo bằng giọng tiếng Việt (cần cài "Speech Services by Google" + gói tiếng Việt). */
object Speaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Boolean>>()

    @Synchronized
    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext, this)
    }

    @Synchronized
    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        t.setLanguage(Locale.forLanguageTag("vi-VN"))
        t.setSpeechRate(0.95f)
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
        while (pending.isNotEmpty()) {
            val (s, flush) = pending.removeFirst()
            doSpeak(s, flush)
        }
    }

    @Synchronized
    fun speak(text: String, interrupt: Boolean = false) {
        if (!ready) {
            pending.addLast(text to interrupt)
            return
        }
        doSpeak(text, interrupt)
    }

    private fun doSpeak(text: String, interrupt: Boolean) {
        tts?.speak(
            text,
            if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            UUID.randomUUID().toString()
        )
    }

    @Synchronized
    fun stop() {
        tts?.stop()
    }
}

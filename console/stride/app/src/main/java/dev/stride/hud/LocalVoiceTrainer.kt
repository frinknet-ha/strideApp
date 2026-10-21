package dev.stride.hud

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.util.ArrayDeque

/** All playback is main-thread owned. Never takes audio focus or controls YouTube. */
class LocalVoiceTrainer(
    private val context: Context, private val cfg: Settings,
    private val show: (String, String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val policy = VoiceTrainerPolicy()
    private val library by lazy { JSONObject(context.assets.open("coach/library.json").bufferedReader().use { it.readText() }) }
    private val audioFiles by lazy { context.assets.list("coach")?.toSet() ?: emptySet() }
    private var player: MediaPlayer? = null
    private var closed = false
    private var token = Long.MIN_VALUE
    private var selected = "off"
    private var wasPermitted = false
    private val variants = mutableMapOf<String, Int>()
    private data class Cue(val event: String, val expires: Long)
    private val pending = ArrayDeque<Cue>()

    fun observe(s: Snapshot, workoutToken: Long) {
        val f = VoiceFrame(workoutToken, s.mode, s.elapsed, s.distance,
            !s.dmk && s.boardOk && !s.boardLocked, s.plan, s.planLap, s.segment,
            s.segmentLabel, s.segmentLeft, s.segmentLeftIsDistance, s.nextLabel,
            s.nextIncline, s.targetIncline, s.inclineAuto)
        main.post {
            if (closed) return@post
            if (token != workoutToken) { stopNow(); token = workoutToken }
            val coach = cfg.voiceTrainer()
            // Respect the existing per-person coaching preference. Guests remain uncoached.
            val permitted = cfg.coachedFor(s.who) && coach != "off" && (coach != "drill" || cfg.voiceExplicit())
            if (coach != selected || permitted != wasPermitted) stopNow()
            selected = coach; wasPermitted = permitted
            val events = policy.observe(f, cfg.voiceInterval())
            if (!permitted || !f.safe || f.mode in listOf("paused", "welcome")) { stopNow(); return@post }
            events.forEach { event ->
                if (event == "finish") stopNow()
                if (event in listOf("steady", "push") && player != null) return@forEach
                if (pending.size < 12) pending.add(Cue(event, SystemClock.elapsedRealtime() + if (event == "hill") 10_000 else 90_000))
            }
            drain()
        }
    }

    fun settingsChanged() { main.post { stopNow() } }
    fun stop() { main.post { stopNow() } }
    private fun stopNow() {
        pending.clear()
        val old = player; player = null
        try { old?.reset(); old?.release() } catch (_: Exception) {}
    }
    private fun drain() {
        if (closed || player != null) return
        val coach = cfg.voiceTrainer()
        if (!wasPermitted || coach == "off" || (coach == "drill" && !cfg.voiceExplicit())) { pending.clear(); return }
        while (pending.isNotEmpty()) {
            val cue = pending.removeFirst()
            if (cue.expires < SystemClock.elapsedRealtime()) continue
            try {
                val people = library.getJSONArray("personalities")
                val person = (0 until people.length()).map { people.getJSONObject(it) }.first { it.getString("id") == coach }
                val lines = person.getJSONObject("lines").getJSONArray(cue.event)
                val key = "$coach:${cue.event}"
                val index = (variants[key] ?: 0) % lines.length()
                variants[key] = index + 1
                show(lines.getString(index), cue.event)
                val filename = "$coach-${cue.event}-$index.wav"
                if (filename !in audioFiles) {
                    Log.i("Stride", "local coach: text-only (audio pack not installed)")
                    return
                }
                val mp = MediaPlayer(); player = mp
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                mp.setVolume(0.85f, 0.85f)
                context.assets.openFd("coach/$filename").use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                mp.setOnPreparedListener { if (player === it) it.start() }
                mp.setOnCompletionListener { if (player === it) { player = null; it.release(); drain() } }
                mp.setOnErrorListener { failed, _, _ ->
                    if (player === failed) { player = null; failed.release(); drain() }; true
                }
                mp.prepareAsync()
                Log.i("Stride", "local voice: $coach ${cue.event} variant=$index")
                return
            } catch (e: Exception) {
                Log.w("Stride", "local voice unavailable: ${e.javaClass.simpleName}")
                val failed = player; player = null
                try { failed?.release() } catch (_: Exception) {}
            }
        }
    }
    fun close() { main.post { closed = true; stopNow() } }
}

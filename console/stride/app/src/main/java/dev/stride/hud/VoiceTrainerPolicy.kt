package dev.stride.hud

/** Read-only workout observations. No controller, network or Android dependencies. */
data class VoiceFrame(
    val token: Long, val mode: String, val elapsed: Double, val metres: Double,
    val safe: Boolean = true, val plan: String = "", val lap: Int = 1,
    val segment: Int = 0, val segmentLabel: String = "", val segmentLeft: Double = 0.0,
    val distanceBased: Boolean = false, val nextLabel: String = "",
    val nextIncline: Double = 0.0, val targetIncline: Double = 0.0,
    val inclineAuto: Boolean = false,
)

class VoiceTrainerPolicy {
    private var token = Long.MIN_VALUE
    private var started = false
    private var finished = false
    private var miles = 0
    private var lastTip = 0.0
    private var tipIndex = 0
    private var interval = 300
    private val hills = mutableSetOf<String>()
    private val recoveries = mutableSetOf<String>()

    fun observe(f: VoiceFrame, seconds: Int): List<String> {
        if (f.token != token) {
            token = f.token; started = false; finished = false; miles = 0
            lastTip = 0.0; tipIndex = 0; hills.clear(); recoveries.clear()
        }
        val gap = seconds.takeIf { it in listOf(180, 300, 600) } ?: 300
        if (gap != interval) { interval = gap; lastTip = f.elapsed }
        if (!f.safe || !f.elapsed.isFinite() || !f.metres.isFinite()) return emptyList()
        if (f.mode == "summary") {
            if (!started || finished) return emptyList()
            finished = true
            return listOf("finish")
        }
        if (f.mode !in listOf("running", "warmup", "cooldown") || finished) return emptyList()
        val events = mutableListOf<String>()
        if (!started) { started = true; lastTip = f.elapsed; events.add("start") }
        val completed = (f.metres.coerceAtLeast(0.0) / 1609.344).toInt()
        if (completed > miles) {
            // A stale or corrupt distance must never allocate an unbounded queue.
            repeat((completed - miles).coerceAtMost(10)) { events.add("mile") }
            miles = completed
        }
        val segmentId = "${f.lap}:${f.segment}"
        val near = f.segmentLeft > 0 && f.segmentLeft <= if (f.distanceBased) 25.0 else 15.0
        if (f.plan.isNotBlank() && f.inclineAuto && near && f.nextLabel.isNotBlank() &&
            f.nextIncline >= 1.0 && f.nextIncline >= f.targetIncline + 0.5 && hills.add(segmentId)) {
            events.add("hill")
        }
        val recovery = f.mode == "cooldown" ||
            (f.plan.isNotBlank() && Regex("recover|cool\\s*down|easing back", RegexOption.IGNORE_CASE).containsMatchIn(f.segmentLabel))
        val recoveryId = if (f.mode == "cooldown") "cooldown" else segmentId
        if (recovery && recoveries.add(recoveryId)) events.add("recovery")
        if (f.mode != "cooldown" && f.elapsed - lastTip >= interval) {
            lastTip = f.elapsed
            if (events.isEmpty()) events.add(if (tipIndex++ % 2 == 0) "steady" else "push")
        }
        return events
    }
}

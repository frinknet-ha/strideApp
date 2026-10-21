package dev.stride.hud

fun main() {
    for (gap in listOf(180, 300, 600)) {
        val policy = VoiceTrainerPolicy()
        fun frame(mode: String = "running", elapsed: Double = 0.0, metres: Double = 0.0) = VoiceFrame(1, mode, elapsed, metres)
        check(policy.observe(frame("welcome"), gap).isEmpty())
        check(policy.observe(frame(), gap) == listOf("start"))
        check(policy.observe(frame(elapsed = gap - 0.1), gap).isEmpty())
        check(policy.observe(frame(elapsed = gap.toDouble()), gap) == listOf("steady"))
        check(policy.observe(frame(elapsed = gap * 2.0), gap) == listOf("push"))
        check(policy.observe(frame(elapsed = gap * 2.0, metres = 1609.343), gap).isEmpty())
        check(policy.observe(frame(elapsed = gap * 2.0, metres = 1609.344), gap) == listOf("mile"))
        check(policy.observe(frame(elapsed = gap * 2.0, metres = 1609.344), gap).isEmpty())
        check(policy.observe(frame("paused", gap * 2.0, 3218.688), gap).isEmpty())
        check(policy.observe(frame(elapsed = gap * 2.0, metres = 3218.688), gap) == listOf("mile"))
        val hill = frame(elapsed = gap * 2.0, metres = 3218.688).copy(plan = "Rolling", segment = 1,
            nextLabel = "Climb", nextIncline = 4.0, segmentLeft = 16.0, inclineAuto = true)
        check(policy.observe(hill, gap).isEmpty())
        check(policy.observe(hill.copy(segmentLeft = 15.0), gap) == listOf("hill"))
        check(policy.observe(hill.copy(segmentLeft = 14.0), gap).isEmpty())
        check(policy.observe(hill.copy(segment = 2, segmentLeft = 10.0, inclineAuto = false), gap).isEmpty())
        check(policy.observe(hill.copy(segment = 3, segmentLeft = 25.0, distanceBased = true), gap) == listOf("hill"))
        check(policy.observe(hill.copy(segment = 4, segmentLeft = 2.0, nextIncline = -1.0), gap).isEmpty())
        val recovery = hill.copy(segment = 5, segmentLabel = "Short recovery", segmentLeft = 100.0)
        check(policy.observe(recovery, gap) == listOf("recovery"))
        check(policy.observe(recovery, gap).isEmpty())
        check(policy.observe(recovery.copy(lap = 2), gap) == listOf("recovery"))
        check(policy.observe(frame("cooldown", gap * 2.0, 3218.688), gap) == listOf("recovery"))
        check(policy.observe(frame("summary", gap * 2.0, 3218.688), gap) == listOf("finish"))
        check(policy.observe(frame("summary", gap * 2.0, 3218.688), gap).isEmpty())
        check(policy.observe(frame(elapsed = gap * 3.0), gap).isEmpty())
        check(policy.observe(frame().copy(token = 2), gap) == listOf("start"))
        check(policy.observe(frame().copy(token = 2, safe = false, metres = 1609.344), gap).isEmpty())
        check(policy.observe(frame().copy(token = 2, metres = Double.NaN), gap).isEmpty())
    }
    println("PASS: native voice policy 3/5/10 minute tips, mile boundaries/deduplication, pause/resume, hill lookahead in seconds/metres, manual-incline exclusion, recovery/laps, finish, workout reset and invalid telemetry.")
}

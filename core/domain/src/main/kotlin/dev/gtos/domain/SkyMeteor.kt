package dev.gtos.domain

import kotlin.math.hypot
import kotlin.math.sin

/** Time-based flight, with the entire head and trail outside both screen edges. */
object SkyMeteor {
    const val duration = 3.2
    private const val interval = 12.0
    fun cycle(seconds: Double): Int = ((seconds - 8).coerceAtLeast(0.0) / interval).toInt()
    fun age(seconds: Double, index: Int): Double? {
        if (seconds < 8 || index >= 1 + cycle(seconds) % 2) return null
        val age = (seconds - 8) % interval - index * .8
        return age.takeIf { it in 0.0..duration }
    }
    data class Flight(val x: Float, val y: Float, val tailX: Float, val tailY: Float, val opacity: Float)
    fun flight(width: Float, height: Float, age: Double, seed: Float, reverse: Boolean): Flight {
        val tail = width * .18f
        val margin = tail + width * .02f
        val startX = if (reverse) width + margin else -margin
        val endX = if (reverse) -margin else width + margin
        val startY = height * (.035f + seed * .26f)
        val endY = startY + height * (.24f + seed * .10f)
        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        val progress = (age / duration).coerceIn(0.0, 1.0).toFloat()
        val x = startX + dx * progress
        val y = startY + dy * progress
        return Flight(x, y, x - dx / length * tail, y - dy / length * tail,
            sin(progress * Math.PI).toFloat().coerceAtLeast(0f))
    }
}

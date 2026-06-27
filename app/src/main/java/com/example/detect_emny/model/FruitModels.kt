package com.example.detect_emeny.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.detect_emeny.config.AppConfig
import kotlin.random.Random

data class Fruit(
    val id: Long = Random.nextLong(),
    var position: Offset,
    val velocity: Offset,
    val spawnTime: Long = System.currentTimeMillis(),
    var isBoomed: Long = 0, // 0 if not boomed, else timestamp of boom
    val type: Int = Random.nextInt(6), // 0: Apple, 1: Banana, 2: Mango, 3: Watermelon, 4: Strawberry, 5: Pineapple
    val amplitude: Float = Random.nextFloat() * 20f,
    val phase: Float = Random.nextFloat() * 2f * kotlin.math.PI.toFloat(),
    val scoreValue: Int = 0,
    val isBomb: Boolean = false
)

class FruitManager {
    var fruits = mutableListOf<Fruit>()
    var totalScore = 0
    var bombHitCount = 0
    private var lastSpawnTime = 0L
    private var lastUpdateTime = 0L
    private var nextSpawnInterval = 500L

    // Runtime configurable parameters
    var spawnIntervalMin = AppConfig.FRUIT_SPAWN_INTERVAL_MS_MIN
    var spawnIntervalMax = AppConfig.FRUIT_SPAWN_INTERVAL_MS_MAX
    var speedMin = AppConfig.FRUIT_SPEED_MIN
    var speedMax = AppConfig.FRUIT_SPEED_MAX
    var bombRate = AppConfig.BOMB_RATE

    fun update(width: Float, height: Float) {
        val currentTime = System.currentTimeMillis()
        if (lastUpdateTime == 0L) {
            lastUpdateTime = currentTime
            return
        }
        val deltaTime = (currentTime - lastUpdateTime) / 1000f
        lastUpdateTime = currentTime
        
        // Remove expired or long-boomed fruits
        fruits.removeAll { 
            (it.isBoomed == 0L && currentTime - it.spawnTime > AppConfig.FRUIT_LIFETIME_MS) ||
            (it.isBoomed != 0L && currentTime - it.isBoomed > 500L) // Show boom for 500ms
        }

        // Spawn new fruits
        if (currentTime - lastSpawnTime > nextSpawnInterval) {
            spawnFruit(width, height)
            lastSpawnTime = currentTime
            nextSpawnInterval = Random.nextLong(
                spawnIntervalMin,
                spawnIntervalMax
            )
        }

        // Update positions
        fruits.forEach { fruit ->
            if (fruit.isBoomed == 0L) {
                // Update the position linearly
                val lastPos = fruit.position
                val nextPos = lastPos + fruit.velocity * deltaTime
                
                // Add a small amount of "jitter" or wave that doesn't accumulate
                // Actually, the simplest way to avoid drift is to only update position linearly
                // and if we want wave, we apply it during drawing or keep a separate 'renderPosition'
                fruit.position = nextPos
            }
        }
    }

    private fun spawnFruit(width: Float, height: Float) {
        val side = Random.nextInt(4) // 0: Top, 1: Bottom, 2: Left, 3: Right
        val margin = 50f
        val startX: Float
        val startY: Float
        
        when (side) {
            0 -> { startX = Random.nextFloat() * width; startY = -margin }
            1 -> { startX = Random.nextFloat() * width; startY = height + margin }
            2 -> { startX = -margin; startY = Random.nextFloat() * height }
            else -> { startX = width + margin; startY = Random.nextFloat() * height }
        }

        val targetX = Random.nextFloat() * width
        val targetY = Random.nextFloat() * height
        
        val dx = targetX - startX
        val dy = targetY - startY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val speed = Random.nextFloat() * (speedMax - speedMin) + speedMin
        val scoreValue = (speed / 100f).toInt().coerceAtLeast(1)
        
        val velocity = Offset(dx / distance * speed, dy / distance * speed)
        val isBomb = Random.nextFloat() < bombRate
        
        fruits.add(Fruit(
            position = Offset(startX, startY), 
            velocity = velocity, 
            scoreValue = if (isBomb) -50 else scoreValue,
            isBomb = isBomb
        ))
    }

    fun checkCollisions(weaponPoints: List<Offset>, weaponSize: Float, onHit: (Boolean) -> Unit) {
        val currentTime = System.currentTimeMillis()
        fruits.forEach { fruit ->
            if (fruit.isBoomed == 0L) {
                // Check each point along the weapon blade
                for (point in weaponPoints) {
                    val dist = (fruit.position - point).getDistance()
                    if (dist < weaponSize) { // Simple circular collision
                        fruit.isBoomed = currentTime
                        if (fruit.isBomb) {
                            bombHitCount++
                            totalScore += fruit.scoreValue
                        } else {
                            totalScore += fruit.scoreValue
                        }
                        if (totalScore < 0) totalScore = 0
                        onHit(fruit.isBomb)
                        break // This fruit is hit, no need to check other points for it
                    }
                }
            }
        }
    }
}

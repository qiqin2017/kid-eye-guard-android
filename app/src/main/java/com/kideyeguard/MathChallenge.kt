package com.kideyeguard

import kotlin.random.Random

data class MathProblem(
    val prompt: String,
    val answer: Int
)

object MathChallenge {
    fun generate(difficulty: String = "easy", count: Int = 2): List<MathProblem> {
        val safeCount = count.coerceIn(1, 5)
        return List(safeCount) {
            when (difficulty) {
                "medium" -> genMedium()
                "hard" -> genHard()
                else -> genEasy()
            }
        }
    }

    private fun genEasy(): MathProblem {
        return if (Random.nextBoolean()) {
            val a = Random.nextInt(3, 19)
            val b = Random.nextInt(2, 19)
            MathProblem("$a + $b = ?", a + b)
        } else {
            val a = Random.nextInt(8, 25)
            val b = Random.nextInt(1, a)
            MathProblem("$a - $b = ?", a - b)
        }
    }

    private fun genMedium(): MathProblem {
        return if (Random.nextBoolean()) {
            val a = Random.nextInt(2, 10)
            val b = Random.nextInt(2, 10)
            MathProblem("$a × $b = ?", a * b)
        } else {
            val b = Random.nextInt(2, 10)
            val q = Random.nextInt(2, 10)
            val a = b * q
            MathProblem("$a ÷ $b = ?", q)
        }
    }

    private fun genHard(): MathProblem {
        val a = Random.nextInt(2, 10)
        val b = Random.nextInt(2, 10)
        val c = Random.nextInt(1, 20)
        return if (Random.nextBoolean()) {
            MathProblem("$a × $b + $c = ?", a * b + c)
        } else {
            val prod = a * b
            val safeC = c.coerceAtMost(prod)
            MathProblem("$a × $b - $safeC = ?", prod - safeC)
        }
    }
}

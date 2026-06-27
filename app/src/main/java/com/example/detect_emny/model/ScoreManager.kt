package com.example.detect_emeny.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ScoreRecord(val score: Int, val bombCount: Int = 0, val timestamp: Long)

class ScoreManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getTopScores(): List<ScoreRecord> {
        val json = prefs.getString("top_scores", "[]")
        val type = object : TypeToken<List<ScoreRecord>>() {}.type
        return try {
            gson.fromJson<List<ScoreRecord>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addScore(score: Int, bombCount: Int) {
        if (score <= 0 && bombCount == 0) return
        val currentScores = getTopScores().toMutableList()
        currentScores.add(ScoreRecord(score, bombCount, System.currentTimeMillis()))
        val top10 = currentScores.sortedByDescending { it.score }.take(10)
        prefs.edit().putString("top_scores", gson.toJson(top10)).apply()
    }
}

package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.ui.viewmodel.SequencerTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiSongResult(
    val title: String,
    val description: String,
    val genre: String,
    val bpm: Int,
    val mood: String,
    val aiInsight: String,
    val melodyNotes: List<String>,
    val tracks: List<SequencerTrack>,
    val patternJson: String
)

object AiMusicComposer {
    private const val TAG = "AiMusicComposer"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun generateSong(title: String, description: String): AiSongResult = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifEmpty { "Pista IA OmniStudio" }
        val cleanDesc = description.trim().ifEmpty { "Ritmo instrumental moderno y armónico" }

        // Attempt Gemini API call if key is present
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val geminiResult = callGeminiApi(cleanTitle, cleanDesc, apiKey)
                if (geminiResult != null) {
                    return@withContext geminiResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API call fell back to algorithmic composer: ${e.message}")
            }
        }

        // Reliable Algorithmic Music Engine
        return@withContext composeAlgorithmicBeat(cleanTitle, cleanDesc)
    }

    private fun callGeminiApi(title: String, description: String, apiKey: String): AiSongResult? {
        val systemPrompt = """
            Eres un productor musical profesional e ingeniero de sonido de OmniStudio.
            Tu tarea es componer un beat y estructura musical completa para una canción a partir del título y descripción del usuario.
            Debes generar 6 pistas rítmicas y melódicas (kick, snare, hihat, clap, bass, lead) de exactamente 16 pasos cada una (true o false).
            Responde ÚNICAMENTE un objeto JSON válido con la siguiente estructura:
            {
              "genre": "Género musical (ej: Trap Latino, Lo-Fi Chill, Synthwave 80s, Deep House, Boom Bap)",
              "bpm": 120, // entero entre 75 y 150
              "mood": "Descripción breve del estado de ánimo (ej: Energético, Melancólico, Festivo)",
              "aiInsight": "Explicación técnica de la producción (2 frases en español sobre el patrón rítmico y bajo)",
              "melodyNotes": ["C4", "E4", "G4", "A4"],
              "pattern": {
                "kick": [true, false, false, false, true, false, false, false, true, false, false, false, true, false, false, false],
                "snare": [false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false],
                "hihat": [true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true],
                "clap": [false, false, false, false, false, false, false, false, false, false, false, false, true, false, false, false],
                "bass": [true, false, false, true, false, false, true, false, false, true, false, false, true, false, false, false],
                "lead": [false, false, true, false, false, true, false, false, true, false, false, true, false, false, true, false]
              }
            }
        """.trimIndent()

        val userPrompt = "Título de la canción: '$title'\nDescripción de la canción: '$description'"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "$systemPrompt\n\n$userPrompt") })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini API failed with code ${response.code}: ${response.body?.string()}")
            return null
        }

        val resBody = response.body?.string() ?: return null
        val root = JSONObject(resBody)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val json = JSONObject(text.trim())
        val genre = json.optString("genre", "Producción AI")
        val bpm = json.optInt("bpm", 120).coerceIn(60, 180)
        val mood = json.optString("mood", "Creativo")
        val aiInsight = json.optString("aiInsight", "Composición estocástica optimizada por IA")

        val melodyArray = json.optJSONArray("melodyNotes")
        val melodyNotes = mutableListOf<String>()
        if (melodyArray != null) {
            for (i in 0 until melodyArray.length()) {
                melodyNotes.add(melodyArray.optString(i))
            }
        }
        if (melodyNotes.isEmpty()) {
            melodyNotes.addAll(listOf("C4", "D#4", "G4", "A#4"))
        }

        val patternObj = json.getJSONObject("pattern")
        val tracks = listOf(
            buildTrackFromJson("Kick Drum", "kick", patternObj),
            buildTrackFromJson("Snare Drum", "snare", patternObj),
            buildTrackFromJson("Hi-Hat", "hihat", patternObj),
            buildTrackFromJson("Clap FX", "clap", patternObj),
            buildTrackFromJson("Synth Bass", "bass", patternObj),
            buildTrackFromJson("Lead Synth", "lead", patternObj)
        )

        val serializedPattern = JSONObject().apply {
            for (track in tracks) {
                val arr = JSONArray()
                track.steps.forEach { arr.put(it) }
                put(track.soundType, arr)
            }
        }.toString()

        return AiSongResult(
            title = title,
            description = description,
            genre = genre,
            bpm = bpm,
            mood = mood,
            aiInsight = aiInsight,
            melodyNotes = melodyNotes,
            tracks = tracks,
            patternJson = serializedPattern
        )
    }

    private fun buildTrackFromJson(name: String, key: String, parent: JSONObject): SequencerTrack {
        val steps = BooleanArray(16)
        if (parent.has(key)) {
            val arr = parent.getJSONArray(key)
            for (i in 0 until minOf(arr.length(), 16)) {
                steps[i] = arr.optBoolean(i, false)
            }
        }
        return SequencerTrack(name = name, soundType = key, steps = steps)
    }

    /**
     * Advanced algorithmic composer that understands musical semantics:
     * Analyzes style tags, words like "trap", "lofi", "chill", "house", "electro", "bajo", "rapido"
     * and produces genre-correct 16-step patterns, BPM, and melody scale.
     */
    fun composeAlgorithmicBeat(title: String, description: String): AiSongResult {
        val lowerText = "$title $description".lowercase()

        val isTrap = lowerText.contains("trap") || lowerText.contains("drill") || lowerText.contains("urbano") || lowerText.contains("808")
        val isLoFi = lowerText.contains("lofi") || lowerText.contains("lo-fi") || lowerText.contains("chill") || lowerText.contains("relajante") || lowerText.contains("estudiar")
        val isHouse = lowerText.contains("house") || lowerText.contains("dance") || lowerText.contains("club") || lowerText.contains("electronica") || lowerText.contains("fiesta")
        val isSynthwave = lowerText.contains("synth") || lowerText.contains("retro") || lowerText.contains("80s") || lowerText.contains("cyber") || lowerText.contains("futuro")
        val isBoomBap = lowerText.contains("boombap") || lowerText.contains("hip hop") || lowerText.contains("rap") || lowerText.contains("calle")

        val (genre, bpm, mood, insight) = when {
            isTrap -> Quadruple(
                "Trap Moderno 808",
                140,
                "Intenso y Enérgico",
                "Patrón de hi-hats acelerados con redobles, bombo con síncopa y bajo 808 profundo en compases alternos."
            )
            isLoFi -> Quadruple(
                "Lo-Fi Nostálgico",
                84,
                "Chill & Reflexivo",
                "Groove relajado con bombo perezoso (lazy kick), caja con sutil retardo y arpegios melódicos suaves."
            )
            isHouse -> Quadruple(
                "Deep Electro House",
                124,
                "Bailable & Pulsante",
                "Patrón Four-on-the-floor con bombo en cada pulso de negra, hi-hat en contratiempo y clap contundente."
            )
            isSynthwave -> Quadruple(
                "Retro Synthwave",
                116,
                "Ciberpunk Ochentero",
                "Bajo rodante en semicorcheas, caja con reverb amplificado y melodía de sintetizador de plomo analógico."
            )
            isBoomBap -> Quadruple(
                "Classic Boom Bap",
                92,
                "Groove Clásico Urbano",
                "Ritmo de batería acústica recortada con acentos en los pasos 5 y 13 y línea de bajo funk melódico."
            )
            else -> Quadruple(
                "Pop Electrónico IA",
                120,
                "Inspirador y Armónico",
                "Composición equilibrada con percusión nítida, contrapunto en sintetizador y tempo versátil."
            )
        }

        val kickSteps = BooleanArray(16)
        val snareSteps = BooleanArray(16)
        val hihatSteps = BooleanArray(16)
        val clapSteps = BooleanArray(16)
        val bassSteps = BooleanArray(16)
        val leadSteps = BooleanArray(16)

        when {
            isTrap -> {
                // Trap beat: Kick on 0, 7, 10; Snare on 8; HiHat almost all steps; Bass on 0, 7, 10
                listOf(0, 7, 10).forEach { kickSteps[it] = true }
                snareSteps[8] = true
                clapSteps[8] = true
                for (i in 0 until 16) {
                    if (i !in listOf(3, 11, 15)) hihatSteps[i] = true
                }
                // Triplet rolls on hihat at 12, 13, 14
                listOf(0, 4, 7, 10, 14).forEach { bassSteps[it] = true }
                listOf(2, 6, 9, 13).forEach { leadSteps[it] = true }
            }
            isHouse -> {
                // 4 on the floor: 0, 4, 8, 12
                listOf(0, 4, 8, 12).forEach { kickSteps[it] = true }
                listOf(4, 12).forEach { snareSteps[it] = true }
                listOf(4, 12).forEach { clapSteps[it] = true }
                // Offbeat hi-hat: 2, 6, 10, 14
                listOf(2, 6, 10, 14).forEach { hihatSteps[it] = true }
                listOf(0, 3, 6, 8, 11, 14).forEach { bassSteps[it] = true }
                listOf(1, 5, 9, 13).forEach { leadSteps[it] = true }
            }
            isLoFi -> {
                // Lo-fi lazy kick
                listOf(0, 6, 10).forEach { kickSteps[it] = true }
                listOf(4, 12).forEach { snareSteps[it] = true }
                for (i in 0 until 16 step 2) hihatSteps[i] = true
                clapSteps[12] = true
                listOf(0, 3, 8, 11).forEach { bassSteps[it] = true }
                listOf(2, 6, 10, 14).forEach { leadSteps[it] = true }
            }
            isSynthwave -> {
                listOf(0, 4, 8, 12).forEach { kickSteps[it] = true }
                listOf(4, 12).forEach { snareSteps[it] = true }
                listOf(12).forEach { clapSteps[it] = true }
                for (i in 0 until 16) hihatSteps[i] = true
                // Rolling 16th bass
                listOf(0, 2, 4, 6, 8, 10, 12, 14).forEach { bassSteps[it] = true }
                listOf(0, 3, 7, 11, 14).forEach { leadSteps[it] = true }
            }
            else -> {
                listOf(0, 4, 8, 12).forEach { kickSteps[it] = true }
                listOf(4, 12).forEach { snareSteps[it] = true }
                for (i in 0 until 16 step 2) hihatSteps[i] = true
                clapSteps[12] = true
                listOf(0, 6, 8, 14).forEach { bassSteps[it] = true }
                listOf(2, 6, 10, 14).forEach { leadSteps[it] = true }
            }
        }

        val tracks = listOf(
            SequencerTrack("Kick Drum", "kick", steps = kickSteps),
            SequencerTrack("Snare Drum", "snare", steps = snareSteps),
            SequencerTrack("Hi-Hat", "hihat", steps = hihatSteps),
            SequencerTrack("Clap FX", "clap", steps = clapSteps),
            SequencerTrack("Synth Bass", "bass", steps = bassSteps),
            SequencerTrack("Lead Synth", "lead", steps = leadSteps)
        )

        val serialized = JSONObject().apply {
            for (track in tracks) {
                val arr = JSONArray()
                track.steps.forEach { arr.put(it) }
                put(track.soundType, arr)
            }
        }.toString()

        val melodyNotes = when {
            isTrap -> listOf("C4", "D#4", "G4", "G#4", "C5")
            isLoFi -> listOf("C4", "E4", "G4", "B4", "D5")
            isHouse -> listOf("F4", "G#4", "C5", "D#5")
            else -> listOf("C4", "D4", "E4", "G4", "A4", "C5")
        }

        return AiSongResult(
            title = title,
            description = description,
            genre = genre,
            bpm = bpm,
            mood = mood,
            aiInsight = insight,
            melodyNotes = melodyNotes,
            tracks = tracks,
            patternJson = serialized
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

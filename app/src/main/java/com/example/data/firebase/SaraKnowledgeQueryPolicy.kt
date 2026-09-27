package com.example.data.firebase

import java.text.Normalizer
import java.util.Locale

/** Recognizes explicit questions asking Sara to recall the user's saved personal notes. */
internal object SaraKnowledgeQueryPolicy {
    private val recallWords = setOf(
        "recuerda", "recuerdas", "recuerdo", "recordar", "acuerdas", "acuerdo",
        "sabes", "sabe", "saber", "conoces", "conoce", "conocer",
        "remember", "recall", "know", "knows"
    )
    private val personalFacts = listOf(
        "mi nombre", "mi correo", "mi email", "mi edad", "mi cumpleanos", "mi fecha de nacimiento",
        "mi direccion", "mi telefono", "mis preferencias", "my name", "my email", "my age",
        "my birthday", "my address", "my phone", "my preferences",
        "sobre mi", "de mi", "about me", "remember me", "recuerdas mi", "recuerda mi",
        "sabes mi", "conoces mi", "know my", "remember my"
    )
    private val directPersonalFactQuestions = listOf(
        "mi nombre", "mi correo", "mi email", "mi edad", "mi cumpleanos", "mi fecha de nacimiento",
        "mi direccion", "mi telefono", "mis preferencias", "my name", "my email", "my age",
        "my birthday", "my address", "my phone", "my preferences"
    )
    private val recallPhrases = listOf(
        "como me llamo",
        "cual es mi nombre",
        "quien soy",
        "que sabes de mi",
        "que sabes sobre mi",
        "que sabes acerca de mi",
        "que recuerdas de mi",
        "que recuerdas sobre mi",
        "que informacion tienes de mi",
        "que informacion tienes sobre mi",
        "tienes informacion sobre mi",
        "what is my name",
        "what is my email",
        "what do you know about me",
        "what do you remember about me"
    )

    fun shouldUseMemoryAwareFallback(rasaRequestedFallback: Boolean, savedContext: String): Boolean =
        rasaRequestedFallback || savedContext.isNotBlank()

    fun isPersonalRecallQuery(query: String): Boolean {
        val normalized = normalize(query)
        if (normalized.isBlank()) return false
        if (recallPhrases.any(normalized::contains)) return true
        if (directPersonalFactQuestions.any(normalized::contains)) return true
        val asksToRecall = normalized.split(Regex("\\s+")).any(recallWords::contains)
        return asksToRecall && personalFacts.any(normalized::contains)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

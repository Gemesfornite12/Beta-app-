package com.example

import com.example.data.firebase.SaraKnowledgeQueryPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaraKnowledgeQueryPolicyTest {
    @Test
    fun recognizesSpanishRecallQuestionsWithAccents() {
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("¿Recuerdas cómo me llamo?"))
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("¿Qué sabes sobre mí?"))
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("¿Qué información tienes sobre mí?"))
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("¿Cuál es mi cumpleaños?"))
    }

    @Test
    fun recognizesEnglishRecallQuestions() {
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("What do you remember about me?"))
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("What is my name?"))
        assertTrue(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("What is my birthday?"))
    }

    @Test
    fun usesMemoryAwareFallbackForSavedContextOrRasaFallback() {
        assertTrue(SaraKnowledgeQueryPolicy.shouldUseMemoryAwareFallback(false, "• me llamo Cris"))
        assertTrue(SaraKnowledgeQueryPolicy.shouldUseMemoryAwareFallback(true, ""))
        assertFalse(SaraKnowledgeQueryPolicy.shouldUseMemoryAwareFallback(false, ""))
    }

    @Test
    fun doesNotTreatGeneralQuestionsAsMemoryRecall() {
        assertFalse(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("¿Qué tiempo hace hoy?"))
        assertFalse(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("Explícame cómo funciona Firebase"))
        assertFalse(SaraKnowledgeQueryPolicy.isPersonalRecallQuery("¿Sabes cómo resolver este error para mí?"))
    }
}

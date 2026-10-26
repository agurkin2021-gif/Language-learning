package ru.spanish.learning;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpanishPhrasePolicyTest {
    @Test public void rejectsBareInfinitiveTemplatesAndClitics() {
        for (String phrase : new String[]{
                "tener razón", "No tener razón", "darse cuenta de", "estar de acuerdo",
                "salir para", "ir a trabajar", "al volver a casa", "hacerlo",
                "reírse", "pesar mucho"}) {
            assertTrue("Should reject the infinitive-based expression: " + phrase,
                    SpanishPhrasePolicy.startsWithInfinitive(phrase));
        }
    }

    @Test public void keepsSpokenFormsAndNormalNonVerbalExpressions() {
        for (String phrase : new String[]{
                "tengo razón", "tenés razón", "él tiene razón", "vos tenés razón",
                "me doy cuenta de", "estoy de acuerdo", "tengo ganas de comer",
                "lugar de trabajo", "de vez en cuando", "por supuesto",
                "¡No te olvides!", "me voy a dormir", "a lo mejor",
                "a pesar de", "a pesar de la lluvia"}) {
            assertFalse("Should keep the natural expression: " + phrase,
                    SpanishPhrasePolicy.startsWithInfinitive(phrase));
        }
    }
}

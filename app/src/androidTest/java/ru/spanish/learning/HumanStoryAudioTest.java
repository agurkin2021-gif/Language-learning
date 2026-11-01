package ru.spanish.learning;

import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileOutputStream;

/** Tests recording identity and completeness without requesting microphone permission. */
public final class HumanStoryAudioTest extends AndroidTestCase {
    public void testSentenceStorageDoesNotMixProfilesLanguagesOrStoryEdits() throws Exception {
        final long id = 987654321L;
        String text = "Hola. ¿Cómo estás? Estoy bien.";
        HumanStoryAudio papa = new HumanStoryAudio(getContext(), "papa", "es", id, text);
        HumanStoryAudio mama = new HumanStoryAudio(getContext(), "mama", "es", id, text);
        HumanStoryAudio english = new HumanStoryAudio(getContext(), "papa", "en", id, text);
        HumanStoryAudio revised = new HumanStoryAudio(getContext(), "papa", "es", id, text + " Hasta luego.");
        try {
            assertEquals(3, papa.count());
            assertFalse(papa.complete());
            for (int i = 0; i < papa.count(); i++) {
                File f = papa.segment(i);
                assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
                try (FileOutputStream stream = new FileOutputStream(f)) {
                    stream.write(new byte[512]);
                }
            }
            assertTrue(papa.complete());
            assertEquals(3, papa.recordedCount());
            assertFalse(mama.complete());
            assertFalse(english.complete());
            assertFalse(revised.complete());
            assertFalse(papa.segment(0).equals(mama.segment(0)));
            assertFalse(papa.segment(0).equals(english.segment(0)));
            assertFalse(papa.segment(0).equals(revised.segment(0)));
            papa.deleteSentence(1);
            assertEquals(2, papa.recordedCount());
            assertFalse(papa.complete());
        } finally {
            papa.deleteAll();
            mama.deleteAll();
            english.deleteAll();
            revised.deleteAll();
        }
    }
}

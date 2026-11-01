package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Google cached audio stays isolated and only valid complete WAV takes are playable. */
public final class GoogleStoryAudioTest extends AndroidTestCase {
    public void testCachePerProfileLanguageStoryAndText() throws Exception {
        GoogleStoryAudio a = new GoogleStoryAudio(getContext(), "papa", "es", 44444444L, "Hola. Chau.");
        GoogleStoryAudio b = new GoogleStoryAudio(getContext(), "mama", "es", 44444444L, "Hola. Chau.");
        GoogleStoryAudio english = new GoogleStoryAudio(getContext(), "papa", "en", 44444444L, "Hola. Chau.");
        GoogleStoryAudio edited = new GoogleStoryAudio(getContext(), "papa", "es", 44444444L, "Hola. Chau. Otra.");
        try {
            assertFalse(a.complete());
            assertEquals(2, a.count());
            assertFalse(a.segment(0).equals(b.segment(0)));
            assertFalse(a.segment(0).equals(english.segment(0)));
            assertFalse(a.segment(0).equals(edited.segment(0)));
            File part = a.staging(0);
            try (FileOutputStream out = new FileOutputStream(part)) {
                out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
                out.write(new byte[128]);
            }
            a.publish(0, part);
            assertTrue(a.recorded(0));
            assertFalse(a.complete());
            assertFalse(b.recorded(0));
            File second = a.staging(1);
            try (FileOutputStream out = new FileOutputStream(second)) {
                out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
                out.write(new byte[128]);
            }
            a.publish(1, second);
            assertTrue(a.complete());
            assertFalse(edited.complete());
        } finally {
            a.deleteAll();
            b.deleteAll();
            english.deleteAll();
            edited.deleteAll();
        }
    }
}

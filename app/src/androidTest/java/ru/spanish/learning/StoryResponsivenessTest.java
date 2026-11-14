package ru.spanish.learning;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.speech.tts.TextToSpeech;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise the long-story UI paths with Android's actual disk-I/O detector. */
@TargetApi(28)
public final class StoryResponsivenessTest extends InstrumentationTestCase {
    private Context context() { return getInstrumentation().getTargetContext(); }

    private void withoutMainThreadDisk(List<Violation> violations, Runnable action) {
        getInstrumentation().runOnMainSync(() -> {
            StrictMode.ThreadPolicy before = StrictMode.getThreadPolicy();
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites()
                    .penaltyListener(Runnable::run, violations::add).build());
            try { action.run(); }
            finally { StrictMode.setThreadPolicy(before); }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testProgressReturnsWithoutDiskAndSurvivesOriginalStoreClose() throws Exception {
        String name = "test-background-story-progress.db";
        context().deleteDatabase(name);
        List<Violation> violations = new CopyOnWriteArrayList<>();
        AtomicReference<Future<?>> saved = new AtomicReference<>();
        Store original = Store.englishForTest(context(), name);
        try {
            long id = original.addStory("Progress", "One. Two. Three.", "Test");
            withoutMainThreadDisk(violations, () -> saved.set(original.positionAsync(id, 2)));
            original.close();
            saved.get().get(10, TimeUnit.SECONDS);
            try (Store reopened = Store.englishForTest(context(), name)) {
                assertEquals(2, reopened.stories().get(0).position());
                long before;
                try (android.database.Cursor c = reopened.getReadableDatabase()
                        .rawQuery("SELECT total_changes()", null)) {
                    assertTrue(c.moveToFirst());
                    before = c.getLong(0);
                }
                reopened.position(id, 2);
                try (android.database.Cursor c = reopened.getReadableDatabase()
                        .rawQuery("SELECT total_changes()", null)) {
                    assertTrue(c.moveToFirst());
                    assertEquals("An unchanged position must not rewrite SQLite", before, c.getLong(0));
                }
                assertTrue(reopened.deleteStory(id));
                long replacement = reopened.addStory("Replacement", "Different text.", "Test");
                assertEquals("SQLite can reuse a deleted story ID", id, replacement);
                reopened.positionAsync(id, 2, "One. Two. Three.").get(10, TimeUnit.SECONDS);
                assertEquals("A queued old-story position cannot overwrite its replacement",
                        0, reopened.stories().get(0).position());
            }
            assertTrue("Progress must not touch disk on the UI thread: " + violations,
                    violations.isEmpty());
        } finally {
            original.close();
            context().deleteDatabase(name);
        }
    }

    public void testRepeatedPlaybackStatusAndPauseDoNotRescanStoryFiles() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 80; i++) body.append("Hola amigo. ");
        GoogleStoryAudio audio = new GoogleStoryAudio(context(), "papa", "es",
                876543219L, body.toString());
        AtomicReference<SpeechPlayer> player = new AtomicReference<>();
        CountDownLatch checked = new CountDownLatch(1);
        List<Violation> violations = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < audio.count(); i++) {
                File wav = audio.staging(i);
                try (FileOutputStream out = new FileOutputStream(wav)) {
                    out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
                    out.write(new byte[128]);
                }
                audio.publish(i, wav);
            }
            getInstrumentation().runOnMainSync(() -> {
                SpeechPlayer p = new SpeechPlayer(context(), () -> {
                    SpeechPlayer current = player.get();
                    if (current != null && current.googleSelected()) checked.countDown();
                });
                player.set(p);
                p.load(body.toString(), 0);
                p.attachGoogleStory(audio, true);
            });
            assertTrue("Background recording check must complete", checked.await(10, TimeUnit.SECONDS));
            withoutMainThreadDisk(violations, () -> {
                SpeechPlayer p = player.get();
                for (int i = 0; i < 200; i++) {
                    assertTrue(p.googleAvailable());
                    assertTrue(p.googleSelected());
                    assertTrue(p.canPlay());
                    p.storyAudioStatus();
                }
                p.pause();
            });
            assertTrue("Playback controls must use the availability snapshot: " + violations,
                    violations.isEmpty());
        } finally {
            if (player.get() != null)
                getInstrumentation().runOnMainSync(() -> player.get().close());
            audio.deleteAll();
        }
    }

    public void testWarmMarkedStoryCacheNeverReadsDiskOnMainThread() throws Exception {
        List<String> sentences = new ArrayList<>();
        for (int i = 0; i < 80; i++) sentences.add("Hola amigo.");
        String key = "responsiveness-test-" + UUID.randomUUID();
        List<File> fixtures = new ArrayList<>();
        File folder = new File(context().getFilesDir(), "story_prepared_audio");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        for (int i = 0; i < sentences.size(); i++) {
            String identity = key + ":" + i + ":rate:" + Float.floatToIntBits(.85f)
                    + ":voice::text:" + sentences.get(i);
            File source = new File(folder, UUID.nameUUIDFromBytes(
                    identity.getBytes(StandardCharsets.UTF_8)) + ".wav");
            fixtures.add(source);
            if (i == 0) {
                String marked = source.getName() + ":pause:700:[4]";
                fixtures.add(new File(folder, UUID.nameUUIDFromBytes(
                        marked.getBytes(StandardCharsets.UTF_8)) + ".wav"));
            }
        }
        for (File fixture : fixtures)
            try (FileOutputStream out = new FileOutputStream(fixture)) { out.write(new byte[128]); }
        AtomicReference<StoryAudioPreparer> preparation = new AtomicReference<>();
        AtomicReference<TextToSpeech> engine = new AtomicReference<>();
        CountDownLatch engineInitialized = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        List<Violation> violations = new CopyOnWriteArrayList<>();
        AtomicReference<StrictMode.ThreadPolicy> before = new AtomicReference<>();
        try {
            getInstrumentation().runOnMainSync(() -> engine.set(
                    new TextToSpeech(context(), status -> engineInitialized.countDown())));
            assertTrue(engineInitialized.await(10, TimeUnit.SECONDS));
            getInstrumentation().runOnMainSync(() -> {
                preparation.set(new StoryAudioPreparer(context(), new Handler(Looper.getMainLooper()),
                        () -> { if (preparation.get().isReady()) ready.countDown(); }));
                before.set(StrictMode.getThreadPolicy());
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads().detectDiskWrites()
                        .penaltyListener(Runnable::run, violations::add).build());
                preparation.get().start(engine.get(), sentences, Map.of(0, List.of(4)),
                        700, key, "es", .85f, "");
            });
            assertTrue("Warm story must become playable without TTS regeneration",
                    ready.await(15, TimeUnit.SECONDS));
            getInstrumentation().runOnMainSync(() -> {
                assertNotNull(preparation.get().audioAt(0));
                // Reapplying unchanged settings must preserve the already-ready cache.
                preparation.get().start(engine.get(), sentences, Map.of(0, List.of(4)),
                        700, key, "es", .85f, "");
                assertTrue(preparation.get().isReady());
            });
        } finally {
            getInstrumentation().runOnMainSync(() -> {
                if (before.get() != null) StrictMode.setThreadPolicy(before.get());
                if (preparation.get() != null) preparation.get().close();
                if (engine.get() != null) engine.get().shutdown();
            });
            getInstrumentation().waitForIdleSync();
            for (File fixture : fixtures) fixture.delete();
        }
        assertTrue("Warm-cache preparation must not block UI on disk: " + violations,
                violations.isEmpty());
    }
}

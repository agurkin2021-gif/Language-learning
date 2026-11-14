package ru.spanish.learning;

import android.test.AndroidTestCase;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** SQLite and picture checks: only disposable test data is ever changed. */
public final class CardEditingTest extends AndroidTestCase {
    private static final String DB = "test-edit-word-phrase-2026.db";

    @Override protected void setUp() throws Exception {
        super.setUp();
        getContext().deleteDatabase(DB);
    }

    @Override protected void tearDown() throws Exception {
        getContext().deleteDatabase(DB);
        super.tearDown();
    }

    public void testEditWordKeepsIdMasteryCategoryReviewsAndSyncTombstone() {
        try (Store store = Store.forProfileTest(getContext(), DB, StudyProfile.MAMA, true)) {
            String oldWord = "card-edit-word-tb-723";
            String newWord = "card-edit-word-tb-723 corrected";
            store.addCard(oldWord, "старый перевод", 0, 2);
            Store.Card initial = find(store, oldWord);
            assertNotNull(initial);
            store.review(initial.id(), 2, 0); // Already learned and archived
            Store.Card learned = find(store, oldWord);
            assertTrue(learned.mastered());
            assertEquals(1, learned.reviewCount());
            assertTrue(store.editCard(initial.id(), newWord, "исправленный перевод"));

            Store.Card edited = find(store, newWord);
            assertNotNull(edited);
            assertEquals(initial.id(), edited.id());
            assertEquals(2, edited.category());
            assertEquals(0, edited.kind());
            assertTrue(edited.mastered());
            assertEquals(learned.reviewCount(), edited.reviewCount());
            assertEquals(learned.lastResult(), edited.lastResult());
            assertEquals(learned.nextDue(), edited.nextDue());
            assertEquals("исправленный перевод", edited.meaning());
            assertNull(find(store, oldWord));
            assertTrue(store.vocabularyForSync().stream()
                    .anyMatch(e -> e.deleted() && e.spanish().equals(oldWord)));
            assertTrue(store.vocabularyForSync().stream()
                    .anyMatch(e -> !e.deleted() && e.spanish().equals(newWord)));
            assertEquals(1, store.learnedWordCount());

            // Changing translation or only capitalization must not create a tombstone.
            assertTrue(store.editCard(initial.id(), newWord.toUpperCase(), "третий вариант"));
            assertEquals(1, store.vocabulary().size());
            assertEquals("третий вариант", store.vocabulary().get(0).meaning());
        }
    }

    public void testPhraseEditAndDuplicateRejectionLeaveOtherCardUnchanged() {
        try (Store store = Store.forProfileTest(getContext(), DB, StudyProfile.DIMA, true)) {
            store.addCard("original phrase in test", "исходная фраза", 1, 0);
            store.addCard("other phrase in test", "чужой перевод", 1, 0);
            Store.Card phrase = find(store, "original phrase in test");
            assertNotNull(phrase);
            store.review(phrase.id(), 1, 12L);
            assertTrue(store.editCard(phrase.id(), "corrected phrase in test", "новая форма"));
            Store.Card edited = find(store, "corrected phrase in test");
            assertEquals(phrase.id(), edited.id());
            assertEquals(1, edited.kind());
            assertEquals(0, edited.category());
            assertFalse(edited.mastered());
            assertEquals(1, edited.reviewCount());
            assertEquals(12L, edited.nextDue());

            try {
                store.editCard(phrase.id(), "OTHER PHRASE IN TEST", "не должен измениться");
                fail("Must reject duplicates case-insensitively");
            } catch (IllegalArgumentException expected) {}
            assertEquals("новая форма", find(store, "corrected phrase in test").meaning());
            assertEquals("чужой перевод", find(store, "other phrase in test").meaning());
            assertEquals(2, store.vocabulary().size());

            try {
                store.editCard(phrase.id(), " ", "имеет перевод");
                fail("Empty source must fail");
            } catch (IllegalArgumentException expected) {}
            try {
                store.editCard(phrase.id(), "corrected phrase in test", " ");
                fail("Empty meaning must fail");
            } catch (IllegalArgumentException expected) {}
        }
    }

    public void testCopiesCustomArtOnEditWithoutOverwritingExistingArt() throws Exception {
        String before = "image-edit-unit-test-original-821";
        String after = "image-edit-unit-test-corrected-821";
        boolean english = true;
        File dir = new File(getContext().getFilesDir(),
                StudyProfile.imageDirectory(StudyProfile.active(getContext()), english));
        File svgBefore = new File(dir, CardImageLoader.key(before) + ".svg");
        File svgAfter = new File(dir, CardImageLoader.key(after) + ".svg");
        File emojiBefore = new File(dir, CardImageLoader.key(before) + ".emoji");
        File emojiAfter = new File(dir, CardImageLoader.key(after) + ".emoji");
        try {
            dir.mkdirs();
            Files.write(svgBefore.toPath(), "<svg>original</svg>".getBytes(StandardCharsets.UTF_8));
            Files.write(emojiBefore.toPath(), "🚗".getBytes(StandardCharsets.UTF_8));
            CardImageMigration.copyOnEdit(getContext(), before, after, english);
            assertEquals("<svg>original</svg>", read(svgAfter));
            assertEquals("🚗", read(emojiAfter));
            Files.write(svgAfter.toPath(), "<svg>MY OWN</svg>".getBytes(StandardCharsets.UTF_8));
            CardImageMigration.copyOnEdit(getContext(), before, after, english);
            assertEquals("<svg>MY OWN</svg>", read(svgAfter));
            assertEquals("<svg>original</svg>", read(svgBefore));
        } finally {
            for (File f : new File[]{svgBefore,svgAfter,emojiBefore,emojiAfter})
                Files.deleteIfExists(f.toPath());
        }
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
    private static Store.Card find(Store store, String word) {
        for (Store.Card card : store.vocabulary()) if (word.equals(card.spanish())) return card;
        return null;
    }
}

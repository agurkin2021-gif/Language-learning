package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** No Internet or model invocation: all answers are simulated manual ChatGPT copies. */
public final class OfficialStudyHandoffTest extends AndroidTestCase {
    private static String id(String prompt) {
        Matcher matcher = Pattern.compile("session_id: ([0-9a-f]{32})").matcher(prompt);
        if (!matcher.find()) throw new AssertionError("Session ID is missing");
        return matcher.group(1);
    }
    private static String json(JSONObject obj) { return obj.toString(); }

    private static JSONObject story(String id, String language) throws Exception {
        return new JSONObject().put("format", "study_story_v1")
                .put("session_id", id).put("language", language)
                .put("title", "Mis amigos").put("body", "Hola. Vamos.")
                .put("translation", "Привет. Пойдём.");
    }

    private static JSONObject audit(String id, int stage, boolean pass, String translation)
            throws Exception {
        JSONArray issues = new JSONArray();
        if (!pass) issues.put("Перевод не соответствует исходному значению");
        return new JSONObject().put("format", "study_audit_v1").put("session_id", id)
                .put("stage", stage).put("pass", pass).put("issues", issues)
                .put("verified_translation", translation);
    }

    @Override protected void tearDown() throws Exception {
        OfficialStudyHandoff.clear(getContext(), "es");
        OfficialStudyHandoff.clear(getContext(), "en");
        super.tearDown();
    }

    public void testStoryRequiresThreeSeparateStepsAndSurvivesDialogClose() throws Exception {
        String prompt = OfficialStudyHandoff.begin(getContext(), "es", "story",
                "Напиши рассказ про друзей", List.of(new Store.Card(
                    1L, "tenés", "ты имеешь", 0, 0, true, 0, 0, 0L)));
        String key = id(prompt);
        assertTrue(prompt.contains("tenés"));
        assertTrue(prompt.contains("voseo"));
        assertTrue(OfficialStudyHandoff.stage(getContext(), "es").contains("рассказ"));
        OfficialStudyHandoff.Outcome next =
                OfficialStudyHandoff.accept(getContext(), "es", json(story(key,"es")));
        assertEquals("audit", next.status());
        assertTrue(next.prompt().contains("ДОСЛОВНО"));
        assertEquals(next.prompt(), OfficialStudyHandoff.preparedPrompt(getContext(), "es"));
        try {
            OfficialStudyHandoff.alreadyVerified(getContext(), "es");
            fail("Unverified story was accepted");
        } catch (IllegalStateException expected) {}
        for (int i = 1; i <= 3; i++) {
            next = OfficialStudyHandoff.accept(getContext(), "es",
                    json(audit(key, i, true, "Привет. Пойдём.")));
            if (i < 3) {
                assertEquals("audit", next.status());
                assertEquals("Ожидается аудит " + (i + 1) + "/3",
                        OfficialStudyHandoff.stage(getContext(), "es"));
            }
        }
        assertEquals("verified", next.status());
        assertEquals("Mis amigos", OfficialStudyHandoff.alreadyVerified(getContext(), "es").title());
        assertEquals("Привет. Пойдём.", next.translation());
    }

    public void testRejectOutOfOrderAndOtherPhoneReplies() throws Exception {
        String prompt = OfficialStudyHandoff.begin(getContext(), "es", "story",
                "Рассказ", List.of());
        String key = id(prompt);
        try {
            OfficialStudyHandoff.accept(getContext(),"es",
                    json(story("00000000000000000000000000000000", "es")));
            fail("Other device reply accepted");
        } catch (IllegalArgumentException expected) {}
        assertFalse(OfficialStudyHandoff.isOurReply(getContext(), "es",
                json(story("00000000000000000000000000000000", "es"))));
        OfficialStudyHandoff.accept(getContext(), "es", json(story(key,"es")));
        try {
            OfficialStudyHandoff.accept(getContext(),"es",
                    json(audit(key,3,true,"Привет. Пойдём.")));
            fail("Skipped stages");
        } catch (IllegalArgumentException expected) {}
        assertTrue(OfficialStudyHandoff.stage(getContext(),"es").endsWith("1/3"));
    }

    public void testFailAuditBlocksSavingEvenIfUserRetries() throws Exception {
        String key=id(OfficialStudyHandoff.begin(getContext(), "es","story","Рассказ",List.of()));
        OfficialStudyHandoff.accept(getContext(),"es",json(story(key,"es")));
        try {
            OfficialStudyHandoff.accept(getContext(),"es",
                    json(audit(key,1,false,"Привет. Пойдём.")));
            fail("Failed audit allowed");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Ничего не сохранено"));
        }
        assertTrue(OfficialStudyHandoff.stage(getContext(),"es").contains("отклонена"));
        try {
            OfficialStudyHandoff.alreadyVerified(getContext(),"es");
            fail("Rejected story allowed");
        } catch (IllegalStateException expected) {}
    }

    public void testWordExactOriginalAndThreeAudits() throws Exception {
        String prompt=OfficialStudyHandoff.begin(getContext(), "es","word","tenés",List.of());
        String key=id(prompt);
        JSONObject guessed = new JSONObject().put("format","study_word_v1")
                .put("session_id",key).put("language","es").put("kind","word")
                .put("term","tienes").put("translation","ты имеешь");
        try {
            OfficialStudyHandoff.accept(getContext(),"es",json(guessed));
            fail("Silent source substitution was accepted");
        } catch (IllegalArgumentException expected) {}
        guessed.put("term","tenés");
        OfficialStudyHandoff.accept(getContext(),"es",json(guessed));
        for (int i=1;i<=3;i++)
            OfficialStudyHandoff.accept(getContext(),"es",json(audit(key,i,true,"ты имеешь")));
        OfficialStudyHandoff.Outcome approved=
                OfficialStudyHandoff.alreadyVerified(getContext(),"es");
        assertEquals("word",approved.kind());
        assertEquals("tenés",approved.term());
    }

    public void testLanguageSessionsDoNotMixAndOneAuditCannotBeReused() throws Exception {
        String es=id(OfficialStudyHandoff.begin(getContext(),"es","story","Рассказ",List.of()));
        String en=id(OfficialStudyHandoff.begin(getContext(),"en","story","Story",List.of()));
        assertFalse(es.equals(en));
        assertTrue(OfficialStudyHandoff.pending(getContext(),"es"));
        assertTrue(OfficialStudyHandoff.pending(getContext(),"en"));
        assertFalse(OfficialStudyHandoff.isOurReply(getContext(),"en", json(story(es,"es"))));
        OfficialStudyHandoff.clear(getContext(),"es");
        assertTrue(OfficialStudyHandoff.pending(getContext(),"en"));
    }
}

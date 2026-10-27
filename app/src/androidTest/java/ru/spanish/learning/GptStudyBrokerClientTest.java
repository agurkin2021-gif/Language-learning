package ru.spanish.learning;

import android.test.AndroidTestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public final class GptStudyBrokerClientTest extends AndroidTestCase {
    public void testRejectsUnsecuredOrFakeProviderUrls() {
        for (String value : new String[]{"http://study.example.com",
                "https://study.example.com.evil.invalid/path",
                "https://study.example.com/remote",
                "https://study.example.com?token=abc",
                "https://someone@study.example.com",
                "https://study.example.com/#fragment"}) {
            try {
                GptStudyBrokerClient.validateBaseUrl(value);
                fail("Untrusted or malformed URL accepted: " + value);
            } catch (IllegalArgumentException expected) { }
        }
    }

    public void testStoryHasPersonalDictionaryAndCorrectLanguage() throws Exception {
        Store.Card card = new Store.Card(1L, "tenés", "ты имеешь",
                0, 0, true, 0, 0, 0L);
        JSONObject request = GptStudyBrokerClient.makeRequest(List.of(card),
                "es", "story", "Про дружбу");
        assertEquals("es", request.getString("language"));
        assertEquals("story", request.getString("kind"));
        JSONArray vocab = request.getJSONArray("cards");
        assertEquals(1, vocab.length());
        assertEquals("tenés", vocab.getJSONObject(0).getString("term"));
        assertTrue(vocab.getJSONObject(0).getBoolean("mastered"));
    }

    private JSONObject response(boolean thirdPassed) throws Exception {
        JSONArray audits = new JSONArray();
        for (int i = 1; i <= 3; i++) {
            audits.put(new JSONObject().put("stage", i)
                    .put("passed", i != 3 || thirdPassed));
        }
        return new JSONObject().put("kind", "story").put("language", "es")
                .put("title", "Mis amigos").put("body", "Hola. Vamos.")
                .put("translation", "Привет. Пойдём.")
                .put("audits", audits);
    }

    public void testNoSaveWithoutThreePositiveAudits() throws Exception {
        try {
            GptStudyBrokerClient.story(response(false));
            fail("Third audit must veto saving");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("3/3"));
        }
        assertEquals("Mis amigos", GptStudyBrokerClient.story(response(true)).title());
    }

    public void testNoCredentialsUntilExplicitSetup() {
        // Installing an APK must not enable paid API calls.
        GptStudyBrokerClient.disconnect(getContext());
        assertFalse(GptStudyBrokerClient.configured(getContext()));
    }
}

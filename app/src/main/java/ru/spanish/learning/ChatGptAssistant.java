package ru.spanish.learning;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class ChatGptAssistant {
    private static final String AUTH_URL = "https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS_URL = "https://auth.openai.com/.well-known/jwks.json";
    private static final String MODELS_URL = "https://api.openai.com/v1/models";
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String KEY_ALIAS = "spanish_learning_chatgpt_tokens";
    private static final int MAX_HISTORY = 16;

    public interface ConnectCallback {
        void finished(boolean ok, String message);
    }

    public interface MessageCallback {
        void finished(boolean ok, String answer);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();

    public ChatGptAssistant(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("chatgpt_assistant", Context.MODE_PRIVATE);
        if (!prefs.contains("host_id")) {
            prefs.edit().putString("host_id", "urn:uuid:" + UUID.randomUUID()).apply();
        }
    }

    public boolean isConnected() {
        try {
            Session session = loadSession();
            return session != null && session.scope.contains("chatgpt.tokens.use.direct");
        } catch (Exception ignored) {
            return false;
        }
    }

    public String accountLabel() {
        try {
            Session s = loadSession();
            return s == null ? "" : s.email;
        } catch (Exception ignored) {
            return "";
        }
    }

    public String historyText() {
        JSONArray history = loadHistory();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            String role = item.optString("role");
            String text = item.optString("content");
            if (text.isEmpty()) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append("assistant".equals(role) ? "GPT: " : "Вы: ").append(text);
        }
        return out.toString();
    }

    public void clearHistory() {
        prefs.edit().remove("history").apply();
    }

    public void connect(Activity activity, ConnectCallback callback) {
        worker.execute(() -> {
            try {
                authorize(activity);
                activity.runOnUiThread(() -> callback.finished(true, "ChatGPT подключён"));
            } catch (Exception e) {
                String message = readable(e);
                activity.runOnUiThread(() -> callback.finished(false, message));
            }
        });
    }

    public void send(Store store, String userText, MessageCallback callback) {
        String clean = userText == null ? "" : userText.trim();
        if (clean.isEmpty()) {
            callback.finished(false, "Введите сообщение");
            return;
        }
        worker.execute(() -> {
            try {
                Session session = ensureSession();
                String model = chooseModel(session.accessToken);
                JSONArray input = buildInput(clean);
                String instructions = buildInstructions(store);
                String finalText = "";
                boolean executedAny = false;

                for (int round = 0; round < 4; round++) {
                    ResponseTurn turn = request(session.accessToken, model, instructions, input);
                    if (turn.calls.isEmpty()) {
                        finalText = turn.text.trim();
                        if (finalText.isEmpty() && executedAny) finalText = "Готово.";
                        break;
                    }

                    executedAny = true;
                    for (ToolCall call : turn.calls) {
                        JSONObject callItem = new JSONObject();
                        callItem.put("type", "function_call");
                        callItem.put("call_id", call.callId);
                        callItem.put("name", call.name);
                        callItem.put("arguments", call.arguments);
                        input.put(callItem);

                        String result = executeTool(store, call);
                        JSONObject output = new JSONObject();
                        output.put("type", "function_call_output");
                        output.put("call_id", call.callId);
                        output.put("output", result);
                        input.put(output);
                    }
                }

                if (finalText.isEmpty()) finalText = executedAny ? "Готово." : "Не удалось получить ответ.";
                appendHistory("user", clean);
                appendHistory("assistant", finalText);
                String answer = finalText;
                callback.finished(true, answer);
            } catch (Exception e) {
                callback.finished(false, readable(e));
            }
        });
    }

    private void authorize(Activity activity) throws Exception {
        Session previous = loadSession();
        String clientId = previous == null ? "dynamic_agent_client" : previous.clientId;
        String verifier = randomUrl(48);
        String state = randomUrl(32);
        String nonce = randomUrl(32);
        String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(180000);
            String redirectUri = "http://127.0.0.1:" + server.getLocalPort() + "/auth/callback";
            Uri.Builder builder = Uri.parse(AUTH_URL).buildUpon()
                    .appendQueryParameter("client_id", clientId)
                    .appendQueryParameter("ext_agent_host_id", prefs.getString("host_id", ""))
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("redirect_uri", redirectUri)
                    .appendQueryParameter("scope", SCOPES)
                    .appendQueryParameter("resource", RESOURCE)
                    .appendQueryParameter("state", state)
                    .appendQueryParameter("nonce", nonce)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("code_challenge", challenge);
            if (previous == null) {
                builder.appendQueryParameter("agent_name_hint", "Изучение испанского");
            } else {
                if (!previous.idToken.isEmpty()) builder.appendQueryParameter("id_token_hint", previous.idToken);
                if (!previous.email.isEmpty()) builder.appendQueryParameter("login_hint", previous.email);
            }
            String authUrl = builder.build().toString();
            activity.runOnUiThread(() -> activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))));

            CallbackData callback = acceptCallback(server);
            if (!state.equals(callback.state)) throw new IllegalStateException("Неверный ответ авторизации");
            if (!callback.error.isEmpty()) throw new IllegalStateException("Доступ не разрешён: " + callback.error);
            if (callback.code.isEmpty()) throw new IllegalStateException("ChatGPT не вернул код авторизации");

            String issuedClient = previous == null ? callback.clientId : previous.clientId;
            if (issuedClient == null || issuedClient.isEmpty() || "dynamic_agent_client".equals(issuedClient))
                throw new IllegalStateException("Не удалось зарегистрировать приложение");
            if (previous != null && !callback.clientId.isEmpty() && !issuedClient.equals(callback.clientId))
                throw new IllegalStateException("Получен другой ChatGPT-профиль");

            JSONObject token = postForm(TOKEN_URL,
                    "grant_type", "authorization_code",
                    "client_id", issuedClient,
                    "code", callback.code,
                    "code_verifier", verifier,
                    "redirect_uri", redirectUri,
                    "resource", RESOURCE);

            String access = token.optString("access_token");
            String refresh = token.optString("refresh_token");
            String idToken = token.optString("id_token");
            String scope = token.optString("scope");
            if (access.isEmpty() || idToken.isEmpty()) throw new IllegalStateException("Не получены данные ChatGPT");
            if (!scope.contains("chatgpt.tokens.use.direct"))
                throw new IllegalStateException("Нужно разрешить приложению использовать ваш план ChatGPT");

            JSONObject identity = verifyIdToken(idToken, issuedClient, nonce);
            String email = identity.optString("email");
            long expires = System.currentTimeMillis() + Math.max(60, token.optLong("expires_in", 3600)) * 1000L;
            saveSession(new Session(issuedClient, access, refresh, idToken, scope, email, expires));
        }
    }

    private CallbackData acceptCallback(ServerSocket server) throws Exception {
        try (Socket socket = server.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String request = reader.readLine();
            if (request == null || !request.startsWith("GET ")) throw new IllegalStateException("Неверный callback");
            String target = request.split(" ")[1];
            Uri uri = Uri.parse("http://127.0.0.1" + target);
            String code = safe(uri.getQueryParameter("code"));
            String state = safe(uri.getQueryParameter("state"));
            String clientId = safe(uri.getQueryParameter("client_id"));
            String error = safe(uri.getQueryParameter("error"));

            String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>"
                    + "<title>Готово</title></head><body style='font-family:sans-serif;padding:24px'>"
                    + "<h2>ChatGPT подключён</h2><p>Можно вернуться в приложение.</p>"
                    + "<p><a href='spanishlearning://assistant/connected'>Вернуться в «Изучение испанского»</a></p>"
                    + "<script>setTimeout(function(){location.href='spanishlearning://assistant/connected'},300)</script></body></html>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                    + body.length + "\r\nConnection: close\r\n\r\n");
            writer.flush();
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            return new CallbackData(code, state, clientId, error);
        }
    }

    private Session ensureSession() throws Exception {
        Session s = loadSession();
        if (s == null) throw new IllegalStateException("Сначала подключите ChatGPT один раз");
        if (!s.scope.contains("chatgpt.tokens.use.direct"))
            throw new IllegalStateException("Использование плана ChatGPT не разрешено");
        if (s.expiresAt > System.currentTimeMillis() + 90_000) return s;
        if (s.refreshToken.isEmpty()) throw new IllegalStateException("Нужно заново подключить ChatGPT");

        JSONObject token = postForm(TOKEN_URL,
                "grant_type", "refresh_token",
                "client_id", s.clientId,
                "refresh_token", s.refreshToken,
                "resource", RESOURCE);
        String access = token.optString("access_token");
        if (access.isEmpty()) throw new IllegalStateException("Не удалось обновить подключение ChatGPT");
        String refresh = token.optString("refresh_token", s.refreshToken);
        String idToken = token.optString("id_token", s.idToken);
        String scope = token.optString("scope", s.scope);
        long expires = System.currentTimeMillis() + Math.max(60, token.optLong("expires_in", 3600)) * 1000L;
        Session updated = new Session(s.clientId, access, refresh, idToken, scope, s.email, expires);
        saveSession(updated);
        return updated;
    }

    private String chooseModel(String accessToken) throws Exception {
        String cached = prefs.getString("model", "");
        JSONObject root = getJson(MODELS_URL, accessToken);
        JSONArray models = root.optJSONArray("models");
        if (models == null) models = root.optJSONArray("data");
        if (models == null || models.length() == 0) throw new IllegalStateException("Для аккаунта нет доступных моделей");

        List<String> slugs = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.optJSONObject(i);
            if (m == null) continue;
            String visibility = m.optString("visibility", "list");
            String slug = m.optString("slug", m.optString("id"));
            if (!slug.isEmpty() && ("list".equals(visibility) || visibility.isEmpty())) slugs.add(slug);
        }
        if (slugs.isEmpty()) throw new IllegalStateException("Для аккаунта нет доступных моделей");
        if (!cached.isEmpty() && slugs.contains(cached)) return cached;

        String selected = slugs.get(0);
        for (String slug : slugs) {
            if (slug.contains("6.1-sol")) { selected = slug; break; }
            if (slug.contains("5.6-sol")) selected = slug;
        }
        prefs.edit().putString("model", selected).apply();
        return selected;
    }

    private JSONArray buildInput(String userText) throws Exception {
        JSONArray input = new JSONArray();
        JSONArray history = loadHistory();
        int start = Math.max(0, history.length() - MAX_HISTORY);
        for (int i = start; i < history.length(); i++) {
            JSONObject h = history.optJSONObject(i);
            if (h == null) continue;
            String role = h.optString("role");
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            JSONObject item = new JSONObject();
            item.put("role", role);
            item.put("content", h.optString("content"));
            input.put(item);
        }
        JSONObject current = new JSONObject();
        current.put("role", "user");
        current.put("content", userText);
        input.put(current);
        return input;
    }

    private String buildInstructions(Store store) {
        StringBuilder learned = new StringBuilder();
        List<Store.Card> mastered = store.mastered();
        int limit = Math.min(mastered.size(), 300);
        for (int i = 0; i < limit; i++) {
            Store.Card c = mastered.get(i);
            if (learned.length() > 0) learned.append("; ");
            learned.append(c.spanish()).append(" = ").append(c.meaning());
        }
        return """
                Ты встроенный помощник Android-приложения «Изучение испанского». Пользователь говорит по-русски и учит аргентинский Rioplatense Spanish: используй vos/tenés и естественные аргентинские формы. Никогда не проси пользователя заново объяснять, что это за приложение: контекст проекта уже передан тебе.

                Основная задача: разговаривать с пользователем внутри приложения и выполнять контентные команды прямо в рабочей базе. Если просят добавить слово — вызови add_word. Если устойчивое выражение — add_phrase. Если просят сочинить/добавить сказку — создай короткий естественный испанский текст и синхронный русский перевод и вызови add_story. После инструмента кратко скажи, что сделано. Не утверждай, что что-то добавлено, пока инструмент реально не вызван.

                Для каждой новой карточки обязательно создай простую понятную SVG-иллюстрацию 256x256 без текста, скриптов, внешних ссылок и foreignObject. Иллюстрация должна визуально передавать смысл слова/выражения и быть пригодна для офлайн-показа.

                Слова и устойчивые выражения — разные типы. Слово обязательно классифицируй как verb, noun или adjective. Не добавляй дубли. Для сказки количество предложений в испанском и русском должно совпадать. Когда пользователь просит сказку по уже изученным словам, приоритетно используй список освоенных карточек ниже.

                Изменение программного кода, интерфейса или логики APK нельзя выполнить этими контентными инструментами: в таком случае объясни кратко, что это требует отдельной сборки приложения, но не заставляй пользователя объяснять проект заново.

                Репозиторий проекта: agurkin2021-gif/Language-learning. Рабочая ветка разработки: codex/android-v1. Канал живого контента: live-content.

                Освоенных карточек: %d.
                Освоенные карточки (до 300): %s
                """.formatted(mastered.size(), learned);
    }

    private JSONArray tools() throws Exception {
        JSONArray tools = new JSONArray();

        JSONObject word = baseTool("add_word", "Добавить новое испанское слово в рабочую базу приложения.");
        JSONObject wp = new JSONObject();
        wp.put("spanish", stringSchema("Испанское слово или краткая словарная форма"));
        wp.put("russian", stringSchema("Точный русский смысл"));
        wp.put("category", enumSchema("verb", "noun", "adjective"));
        wp.put("svg", stringSchema("Полный безопасный SVG 256x256 без текста, скриптов и внешних ссылок"));
        word.getJSONObject("parameters").put("properties", wp)
                .put("required", new JSONArray().put("spanish").put("russian").put("category").put("svg"));
        tools.put(word);

        JSONObject phrase = baseTool("add_phrase", "Добавить устойчивое выражение отдельно от обычных слов.");
        JSONObject pp = new JSONObject();
        pp.put("spanish", stringSchema("Устойчивое испанское выражение"));
        pp.put("russian", stringSchema("Русский смысл выражения"));
        pp.put("svg", stringSchema("Полный безопасный SVG 256x256 без текста, скриптов и внешних ссылок"));
        phrase.getJSONObject("parameters").put("properties", pp)
                .put("required", new JSONArray().put("spanish").put("russian").put("svg"));
        tools.put(phrase);

        JSONObject story = baseTool("add_story", "Добавить новую сказку/рассказ в библиотеку приложения.");
        JSONObject sp = new JSONObject();
        sp.put("title", stringSchema("Короткое название"));
        sp.put("spanish", stringSchema("Испанский текст рассказа"));
        sp.put("russian", stringSchema("Русский перевод с тем же количеством предложений"));
        story.getJSONObject("parameters").put("properties", sp)
                .put("required", new JSONArray().put("title").put("spanish").put("russian"));
        tools.put(story);
        return tools;
    }

    private JSONObject baseTool(String name, String description) throws Exception {
        JSONObject p = new JSONObject();
        p.put("type", "object");
        p.put("properties", new JSONObject());
        p.put("required", new JSONArray());
        p.put("additionalProperties", false);
        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("name", name);
        tool.put("description", description);
        tool.put("parameters", p);
        tool.put("strict", true);
        return tool;
    }

    private JSONObject stringSchema(String description) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "string");
        o.put("description", description);
        return o;
    }

    private JSONObject enumSchema(String... values) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "string");
        JSONArray arr = new JSONArray();
        for (String value : values) arr.put(value);
        o.put("enum", arr);
        return o;
    }

    private ResponseTurn request(String accessToken, String model, String instructions, JSONArray input) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("input", input);
        body.put("tools", tools());
        body.put("store", false);
        body.put("stream", true);

        HttpURLConnection connection = (HttpURLConnection) new URL(RESPONSES_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            String error = readAll(connection.getErrorStream());
            throw new IllegalStateException("ChatGPT HTTP " + code + ": " + error);
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        boolean completed = false;
        String failure = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        EventResult result = processEvent(data.toString(), text, calls);
                        completed |= result.completed;
                        if (!result.failure.isEmpty()) failure = result.failure;
                        data.setLength(0);
                    }
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
            if (data.length() > 0) {
                EventResult result = processEvent(data.toString(), text, calls);
                completed |= result.completed;
                if (!result.failure.isEmpty()) failure = result.failure;
            }
        } finally {
            connection.disconnect();
        }
        if (!failure.isEmpty()) throw new IllegalStateException(failure);
        if (!completed) throw new IllegalStateException("Ответ ChatGPT оборвался до завершения");
        return new ResponseTurn(text.toString(), calls);
    }

    private EventResult processEvent(String data, StringBuilder text, List<ToolCall> calls) {
        if (data.isEmpty() || "[DONE]".equals(data)) return new EventResult(false, "");
        try {
            JSONObject event = new JSONObject(data);
            String type = event.optString("type");
            if ("response.output_text.delta".equals(type)) {
                text.append(event.optString("delta"));
            } else if ("response.output_item.done".equals(type)) {
                JSONObject item = event.optJSONObject("item");
                if (item != null && "function_call".equals(item.optString("type"))) {
                    calls.add(new ToolCall(
                            item.optString("call_id"),
                            item.optString("name"),
                            item.optString("arguments")));
                }
            } else if ("response.completed".equals(type)) {
                return new EventResult(true, "");
            } else if ("response.failed".equals(type)) {
                JSONObject response = event.optJSONObject("response");
                JSONObject error = response == null ? null : response.optJSONObject("error");
                return new EventResult(false, error == null ? "Запрос ChatGPT завершился ошибкой" : error.optString("message", error.optString("code")));
            } else if ("error".equals(type)) {
                return new EventResult(false, event.optString("message", "Ошибка ChatGPT"));
            }
        } catch (Exception ignored) {}
        return new EventResult(false, "");
    }

    private String executeTool(Store store, ToolCall call) {
        try {
            JSONObject args = new JSONObject(call.arguments);
            if ("add_word".equals(call.name)) {
                String spanish = args.getString("spanish").trim();
                String russian = args.getString("russian").trim();
                String category = args.getString("category");
                int cat = switch (category) {
                    case "verb" -> 1;
                    case "noun" -> 2;
                    case "adjective" -> 3;
                    default -> throw new IllegalArgumentException("Неизвестная категория");
                };
                boolean added = store.addCardIfMissing(spanish, russian, 0, cat);
                saveSvg(spanish, args.getString("svg"));
                return new JSONObject().put("ok", true).put("status", added ? "added" : "already_exists").toString();
            }
            if ("add_phrase".equals(call.name)) {
                String spanish = args.getString("spanish").trim();
                String russian = args.getString("russian").trim();
                boolean added = store.addCardIfMissing(spanish, russian, 1, 0);
                saveSvg(spanish, args.getString("svg"));
                return new JSONObject().put("ok", true).put("status", added ? "added" : "already_exists").toString();
            }
            if ("add_story".equals(call.name)) {
                boolean added = store.addStoryIfMissing(
                        args.getString("title"),
                        args.getString("spanish"),
                        args.getString("russian"),
                        "ChatGPT • добавлено из приложения");
                return new JSONObject().put("ok", true).put("status", added ? "added" : "already_exists").toString();
            }
            throw new IllegalArgumentException("Неизвестный инструмент");
        } catch (Exception e) {
            try {
                return new JSONObject().put("ok", false).put("error", readable(e)).toString();
            } catch (Exception ignored) {
                return "{\"ok\":false}";
            }
        }
    }

    private void saveSvg(String spanish, String svg) throws Exception {
        String clean = svg == null ? "" : svg.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (!clean.contains("<svg") || !clean.contains("</svg>")
                || !clean.contains("width=\"256\"") || !clean.contains("height=\"256\"")
                || !clean.contains("viewBox=\"0 0 256 256\""))
            throw new IllegalArgumentException("SVG должен быть 256x256");
        if (lower.contains("<script") || lower.contains("foreignobject")
                || lower.contains("javascript:") || lower.contains("http://") || lower.contains("https://")
                || lower.matches("(?s).*\\son[a-z]+\\s*=.*"))
            throw new IllegalArgumentException("SVG содержит запрещённые элементы");
        if (clean.length() > 40_000) throw new IllegalArgumentException("SVG слишком большой");
        File dir = new File(context.getFilesDir(), "card-images");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Не удалось создать папку картинок");
        File file = new File(dir, CardImageLoader.key(spanish) + ".svg");
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(clean.getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONObject verifyIdToken(String jwt, String clientId, String nonce) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw new IllegalStateException("Неверный ID token");
        JSONObject header = new JSONObject(new String(Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8));
        JSONObject payload = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.optString("alg"))) throw new IllegalStateException("Неподдерживаемая подпись ID token");
        String kid = header.optString("kid");
        JSONObject jwks = getJson(JWKS_URL, null);
        JSONArray keys = jwks.optJSONArray("keys");
        JSONObject match = null;
        if (keys != null) {
            for (int i = 0; i < keys.length(); i++) {
                JSONObject key = keys.optJSONObject(i);
                if (key != null && kid.equals(key.optString("kid"))) { match = key; break; }
            }
        }
        if (match == null) throw new IllegalStateException("Ключ подписи ChatGPT не найден");

        BigInteger n = new BigInteger(1, Base64.decode(match.getString("n"), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
        BigInteger e = new BigInteger(1, Base64.decode(match.getString("e"), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(Base64.decode(parts[2], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)))
            throw new IllegalStateException("Подпись ChatGPT не подтверждена");

        if (!"https://auth.openai.com".equals(payload.optString("iss"))) throw new IllegalStateException("Неверный issuer");
        if (!audienceMatches(payload.opt("aud"), clientId)) throw new IllegalStateException("Неверный audience");
        if (!nonce.equals(payload.optString("nonce"))) throw new IllegalStateException("Неверный nonce");
        if (payload.optLong("exp", 0) <= System.currentTimeMillis() / 1000L - 30)
            throw new IllegalStateException("ID token истёк");
        return payload;
    }

    private boolean audienceMatches(Object aud, String clientId) {
        if (aud instanceof String) return clientId.equals(aud);
        if (aud instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) if (clientId.equals(arr.optString(i))) return true;
        }
        return false;
    }

    private JSONObject postForm(String url, String... pairs) throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (body.length() > 0) body.append('&');
            body.append(enc(pairs[i])).append('=').append(enc(pairs[i + 1]));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String text = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("OAuth HTTP " + code + ": " + text);
        return new JSONObject(text);
    }

    private JSONObject getJson(String url, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        int code = c.getResponseCode();
        String text = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + text);
        return new JSONObject(text);
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1; ) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private JSONArray loadHistory() {
        try {
            return new JSONArray(prefs.getString("history", "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void appendHistory(String role, String content) {
        try {
            JSONArray old = loadHistory();
            JSONArray next = new JSONArray();
            int start = Math.max(0, old.length() - (MAX_HISTORY - 2));
            for (int i = start; i < old.length(); i++) next.put(old.get(i));
            next.put(new JSONObject().put("role", role).put("content", content));
            prefs.edit().putString("history", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void saveSession(Session session) throws Exception {
        JSONObject json = new JSONObject();
        json.put("client_id", session.clientId);
        json.put("access_token", session.accessToken);
        json.put("refresh_token", session.refreshToken);
        json.put("id_token", session.idToken);
        json.put("scope", session.scope);
        json.put("email", session.email);
        json.put("expires_at", session.expiresAt);
        prefs.edit().putString("session", encrypt(json.toString())).apply();
    }

    private Session loadSession() throws Exception {
        String encrypted = prefs.getString("session", "");
        if (encrypted.isEmpty()) return null;
        JSONObject json = new JSONObject(decrypt(encrypted));
        return new Session(
                json.optString("client_id"),
                json.optString("access_token"),
                json.optString("refresh_token"),
                json.optString("id_token"),
                json.optString("scope"),
                json.optString("email"),
                json.optLong("expires_at"));
    }

    private String encrypt(String plain) throws Exception {
        SecretKey key = key();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        JSONObject result = new JSONObject();
        result.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        result.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        return result.toString();
    }

    private String decrypt(String value) throws Exception {
        JSONObject json = new JSONObject(value);
        byte[] iv = Base64.decode(json.getString("iv"), Base64.DEFAULT);
        byte[] data = Base64.decode(json.getString("data"), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private String randomUrl(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return base64Url(value);
    }

    private String base64Url(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String readable(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        if (message.contains("subscription_sharing_user_not_eligible"))
            return "Этот ChatGPT-аккаунт сейчас не поддерживает использование плана внутри приложения.";
        if (message.contains("subscription_sharing_usage_limit_exceeded"))
            return "Достигнут лимит использования ChatGPT для этого приложения.";
        return message;
    }

    public void close() {
        worker.shutdownNow();
    }

    private record Session(String clientId, String accessToken, String refreshToken, String idToken,
                           String scope, String email, long expiresAt) {}
    private record CallbackData(String code, String state, String clientId, String error) {}
    private record ToolCall(String callId, String name, String arguments) {}
    private record ResponseTurn(String text, List<ToolCall> calls) {}
    private record EventResult(boolean completed, String failure) {}
}

package ru.spanish.learning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure deterministic merge, shared by the GitHub push and Android import paths.
 * Both mastered and studying cards are included. Deletions remain as tombstones.
 */
public final class VocabularyReconcile {
    private VocabularyReconcile() {}

    private static String key(Store.SyncEntry entry) {
        return Store.syncKey(entry.kind(), entry.spanish());
    }

    /** Positive means a wins. The tie-breaker is deterministic across devices. */
    public static int compare(Store.SyncEntry a, Store.SyncEntry b) {
        int result = Long.compare(a.revision(), b.revision());
        if (result != 0) return result;
        result = a.origin().compareTo(b.origin());
        if (result != 0) return result;
        result = Boolean.compare(a.deleted(), b.deleted());
        if (result != 0) return result;
        result = Boolean.compare(a.mastered(), b.mastered());
        if (result != 0) return result;
        result = Integer.compare(a.reviewCount(), b.reviewCount());
        if (result != 0) return result;
        // Last tie-breaker prevents split-brain even if two payloads reuse a revision.
        return canonical(a).compareTo(canonical(b));
    }

    private static String canonical(Store.SyncEntry entry) {
        return entry.spanish()+"\u0001"+entry.meaning()+"\u0001"+entry.category()+"\u0001"
                +entry.lastResult()+"\u0001"+entry.nextDue()+"\u0001"+entry.kind();
    }

    public static List<Store.SyncEntry> merge(List<Store.SyncEntry> local,
                                               List<Store.SyncEntry> remote) {
        Map<String, Store.SyncEntry> result = new HashMap<>();
        for (Store.SyncEntry e : remote) {
            Store.SyncEntry prior = result.get(key(e));
            if (prior == null || compare(e, prior) > 0) result.put(key(e), e);
        }
        for (Store.SyncEntry e : local) {
            Store.SyncEntry prior = result.get(key(e));
            if (prior == null || compare(e, prior) > 0) result.put(key(e), e);
        }
        List<Store.SyncEntry> values = new ArrayList<>(result.values());
        values.sort(Comparator.comparing(VocabularyReconcile::key));
        return values;
    }

    static JSONObject serialize(String profile, String language,
                                List<Store.SyncEntry> cards) throws Exception {
        if (!validProfile(profile) || !validLanguage(language))
            throw new IllegalArgumentException("Invalid profile/language");
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("profile", profile);
        root.put("language", language);
        JSONArray items = new JSONArray();
        for(Store.SyncEntry card : cards) {
            JSONObject item = new JSONObject();
            item.put("spanish",card.spanish());
            item.put("kind",card.kind()==1?"phrase":"word");
            item.put("revision",card.revision());
            item.put("origin",card.origin());
            item.put("deleted",card.deleted());
            if(!card.deleted()) {
                item.put("meaning",card.meaning());
                item.put("category",card.category());
                item.put("mastered",card.mastered());
                item.put("last_result",card.lastResult());
                item.put("review_count",card.reviewCount());
                item.put("next_due",card.nextDue());
            }
            items.put(item);
        }
        root.put("items",items);
        return root;
    }

    private static boolean validProfile(String p) {
        return "papa".equals(p) || "mama".equals(p) || "dima".equals(p);
    }
    private static boolean validLanguage(String l) {
        return "es".equals(l) || "en".equals(l);
    }

    /** Reject the entire file if any entry is malformed. Never use partial archives. */
    static List<Store.SyncEntry> parse(JSONObject root, String profile, String language)
            throws Exception {
        if(root.getInt("version")!=1 ||
                !profile.equals(root.getString("profile")) ||
                !language.equals(root.getString("language")))
            throw new IllegalArgumentException("Wrong vocabulary archive profile or language");
        JSONArray items=root.getJSONArray("items");
        if(items.length()>20000) throw new IllegalArgumentException("Vocabulary archive too large");
        List<Store.SyncEntry> records=new ArrayList<>();
        for(int i=0;i<items.length();i++) {
            JSONObject v=items.getJSONObject(i);
            String spanish=v.getString("spanish").trim();
            String label=v.getString("kind");
            int kind="phrase".equals(label)?1:"word".equals(label)?0:-1;
            long rev=v.getLong("revision");
            String origin=v.getString("origin");
            boolean deleted=v.getBoolean("deleted");
            if(spanish.isEmpty() || spanish.length()>500 ||
                    kind<0 || rev<0 || origin.isEmpty() || origin.length()>120)
                throw new IllegalArgumentException("Invalid vocabulary archive item");
            String meaning=deleted?"":v.getString("meaning").trim();
            int category=deleted?0:v.getInt("category");
            boolean mastered=!deleted && v.getBoolean("mastered");
            int last=deleted?0:v.getInt("last_result");
            int reviewed=deleted?0:v.getInt("review_count");
            long due=deleted?0:v.getLong("next_due");
            if(!deleted && (meaning.isEmpty() || meaning.length()>1000 ||
                    category<0 || category>3 || last<0 || last>2 ||
                    reviewed<0 || due<0)) throw new IllegalArgumentException("Invalid vocabulary card");
            records.add(new Store.SyncEntry(spanish,meaning,kind,category,mastered,last,
                    reviewed,due,rev,origin,deleted));
        }
        return records;
    }
}
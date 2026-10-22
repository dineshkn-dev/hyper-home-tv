package dev.hyper.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A user-created app folder. Package names preserve the member order. */
final class Folder {

    final String id;
    String name;
    final List<String> apps = new ArrayList<>();

    Folder(String id, String name) {
        this.id = id.isEmpty() ? java.util.UUID.randomUUID().toString() : id;
        this.name = name;
    }

    String key() {
        return "folder:" + id;
    }

    JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("id", id);
            j.put("name", name);
            JSONArray a = new JSONArray();
            for (String p : apps) a.put(p);
            j.put("apps", a);
        } catch (Exception ignored) {}
        return j;
    }

    static Folder fromJson(JSONObject j) {
        Folder f = new Folder(j.optString("id", ""),
                j.optString("name", "Folder"));
        JSONArray a = j.optJSONArray("apps");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                String p = a.optString(i, "");
                if (!p.isEmpty() && !f.apps.contains(p)) f.apps.add(p);
            }
        }
        return f;
    }
}

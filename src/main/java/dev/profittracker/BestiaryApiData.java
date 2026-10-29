package dev.profittracker;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Set;

/** Read only the selected player's Endermite Bestiary family; skip inventories and other profile data. */
public record BestiaryApiData(String profileId, String profileName, long kills, long savedAt) {
    private static final Set<String> FAMILY = Set.of("nest_endermite_50", "endermite_37", "endermite_40");
    private record Candidate(String id, String name, boolean selected, long kills, long savedAt) { }
    public static BestiaryApiData parse(String json, String playerUuid, String preferredProfile) {
        String player = playerUuid.replace("-", "");
        var profiles = new ArrayList<Candidate>();
        boolean success = false;
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "success" -> success = reader.nextBoolean();
                    case "profiles" -> {
                        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); break; }
                        reader.beginArray();
                        while (reader.hasNext()) profiles.add(profile(reader, player));
                        reader.endArray();
                    }
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing profile data");
        } catch (IOException | IllegalStateException ex) { throw new IllegalArgumentException("Invalid profile response", ex); }
        if (!success) throw new IllegalArgumentException("Profile API did not report success");
        var matching = profiles.stream().filter(p -> p.kills >= 0 && !p.id.isBlank())
                .filter(p -> preferredProfile != null && !preferredProfile.isBlank() ? p.name.equalsIgnoreCase(preferredProfile) : p.selected).toList();
        if (matching.size() != 1) throw new IllegalArgumentException("Active profile Bestiary unavailable");
        var p = matching.getFirst();
        return new BestiaryApiData(p.id, p.name, p.kills, p.savedAt);
    }
    private static Candidate profile(JsonReader r, String player) throws IOException {
        String id = "", name = ""; boolean selected = false; long kills = -1, saved = 0;
        r.beginObject();
        while (r.hasNext()) {
            switch (r.nextName()) {
                case "profile_id" -> id = r.nextString();
                case "cute_name" -> name = r.nextString();
                case "selected" -> selected = r.nextBoolean();
                case "members" -> {
                    r.beginObject();
                    while (r.hasNext()) {
                        if (!r.nextName().replace("-", "").equalsIgnoreCase(player)) { r.skipValue(); continue; }
                        r.beginObject();
                        while (r.hasNext()) {
                            switch (r.nextName()) {
                                case "last_save" -> saved = r.nextLong();
                                case "profile" -> {
                                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); break; }
                                    r.beginObject();
                                    while (r.hasNext()) {
                                        if (r.nextName().equals("last_save")) saved = Math.max(saved, r.nextLong());
                                        else r.skipValue();
                                    }
                                    r.endObject();
                                }
                                case "bestiary" -> kills = bestiary(r);
                                default -> r.skipValue();
                            }
                        }
                        r.endObject();
                    }
                    r.endObject();
                }
                default -> r.skipValue();
            }
        }
        r.endObject();
        return new Candidate(id, name, selected, kills, saved);
    }
    private static long bestiary(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return -1; }
        long modern = -1, legacy = -1;
        r.beginObject();
        while (r.hasNext()) {
            String field = r.nextName();
            if (field.equals("kills") && r.peek() == JsonToken.BEGIN_OBJECT) {
                modern = 0; r.beginObject();
                while (r.hasNext()) {
                    if (FAMILY.contains(r.nextName())) modern = Math.addExact(modern, count(r));
                    else r.skipValue();
                }
                r.endObject();
            } else if (field.equals("kills_family_endermite")) legacy = count(r);
            else r.skipValue();
        }
        r.endObject();
        long total = modern >= 0 ? modern : legacy;
        if (total > 1_000_000_000_000L) throw new IllegalArgumentException("Invalid Bestiary count");
        return total;
    }
    private static long count(JsonReader r) throws IOException {
        long count = r.nextLong();
        if (count < 0 || count > 1_000_000_000_000L) throw new IllegalArgumentException("Invalid Bestiary count");
        return count;
    }
}

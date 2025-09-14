package org.csu.pixelstrikejavafx.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CharacterRegistry {
    private static final Map<String, CharacterDef> CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper M = new ObjectMapper();

    /** 从 classpath: /assets/characters/{id}.json 读取并缓存 */
    public static CharacterDef get(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            String path = "/assets/characters/" + k + ".json";
            try (InputStream in = CharacterRegistry.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Missing " + path);
                return M.readValue(in, CharacterDef.class);
            } catch (Exception e) {
                throw new RuntimeException("Load character json failed: " + path, e);
            }
        });
    }
}

package org.csu.pixelstrikejavafx.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** /assets/weapons/{id}.json 读取缓存；缺失时回退 pistol */
public final class WeaponRegistry {
    private static final Map<String, WeaponDef> CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper M = new ObjectMapper();

    public static WeaponDef get(String id) {
        String key = (id == null || id.isBlank()) ? "pistol" : id;
        return CACHE.computeIfAbsent(key, k -> {
            String path = "/assets/weapons/" + k + ".json";
            try (InputStream in = WeaponRegistry.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Missing " + path);
                return M.readValue(in, WeaponDef.class);
            } catch (Exception e) {
                if (!"pistol".equals(key)) return get("pistol");
                throw new RuntimeException("Load weapon json failed: " + path, e);
            }
        });
    }
}

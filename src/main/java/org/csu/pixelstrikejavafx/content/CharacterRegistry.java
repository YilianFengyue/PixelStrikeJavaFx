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
        // ★ 核心修复：如果传入的id是null或空的，就默认使用 "ash"
        String key = (id == null || id.isBlank()) ? "ash" : id;

        return CACHE.computeIfAbsent(key, k -> {
            String path = "/assets/characters/" + k + ".json";
            try (InputStream in = CharacterRegistry.class.getResourceAsStream(path)) {
                if (in == null) {
                    // 如果连请求的文件都找不到，并且它还不是 "ash"，就再次尝试加载 "ash" 作为最终后备
                    if (!"ash".equals(k)) {
                        System.err.println("警告: 找不到角色定义 " + path + ", 将使用默认角色 'ash'.");
                        return get("ash");
                    }
                    throw new IllegalStateException("严重错误: 找不到基础角色文件 " + path);
                }
                return M.readValue(in, CharacterDef.class);
            } catch (Exception e) {
                // 如果发生任何其他错误，也尝试返回 "ash"
                if (!"ash".equals(k)) {
                    System.err.println("错误: 加载角色 " + path + " 失败, 将使用默认角色 'ash'. 错误信息: " + e.getMessage());
                    return get("ash");
                }
                // 如果连加载 "ash" 都失败了，那就没办法了，抛出严重错误
                throw new RuntimeException("严重错误: 加载默认角色 'ash' 失败!", e);
            }
        });
    }
}
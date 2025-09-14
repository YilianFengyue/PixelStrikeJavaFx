package org.csu.pixelstrikejavafx.core;
import com.google.gson.JsonObject;

public class GlobalState {
    public static String authToken = null;
    public static Long userId = null;
    public static String nickname = null;
    public static JsonObject currentRoomInfo = null;
    public static String avatarUrl = null;
    public static String currentGameServerUrl = null;
    public static Long currentGameId = null;
    public static JsonObject lastMatchResults = null; // <- 新增此行
    public static boolean shouldShowLastMatchResults = false; // <- 新增此行
}
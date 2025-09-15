package org.csu.pixelstrikejavafx.lobby.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.csu.pixelstrikejavafx.core.GlobalState;

import java.io.File;
import java.util.List;
import java.util.Map;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;

import java.io.IOException;
import java.util.Objects;

public class ApiClient {
    private static final String BASE_URL = "http://localhost:8080";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    /**
     * 调用后端 API 获取所有可用地图的列表
     * @return 包含地图信息的 Map 列表
     * @throws IOException
     */
    public List<Map<String, Object>> getMaps() throws IOException {
        String url = BASE_URL + "/game-data/maps";
        Request request = new Request.Builder().url(url).build(); // 无需Token

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取地图列表失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() == 0) {
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                return gson.fromJson(jsonObject.get("data"), listType);
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 API 获取所有可用角色的列表
     * @return 包含角色信息的 Map 列表
     * @throws IOException
     */
    public List<Map<String, Object>> getCharacters() throws IOException {
        String url = BASE_URL + "/game-data/characters";
        Request request = new Request.Builder().url(url).build(); // 无需Token

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取角色列表失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() == 0) {
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                return gson.fromJson(jsonObject.get("data"), listType);
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 在自定义房间内更换角色
     * @param characterId 新的角色ID
     * @throws IOException
     */
    public void changeCharacterInRoom(long characterId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/character/change"))
                .newBuilder()
                .addQueryParameter("characterId", String.valueOf(characterId))
                .build();

        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("更换角色请求失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 /auth/login 接口 (新版)
     * @param username 用户名
     * @param password 密码
     * @return 成功时返回 token, 失败时抛出异常
     * @throws IOException
     */
    public String login(String username, String password) throws IOException {
        String url = BASE_URL + "/auth/login";
        String jsonBody = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {


                // 1. 获取 data 这个 JsonObject
                JsonObject dataObject = jsonObject.getAsJsonObject("data");

                // 2. 从 data 对象中获取 token
                String token = dataObject.get("token").getAsString();
                GlobalState.authToken = token; // 存入全局状态

                // 3. 从 data 对象中获取 userProfile 对象
                JsonObject userProfileObject = dataObject.getAsJsonObject("userProfile");

                // 4. 从 userProfile 对象中获取具体信息并存入全局状态
                GlobalState.userId = userProfileObject.get("userId").getAsLong();
                GlobalState.nickname = userProfileObject.get("nickname").getAsString();

                JsonElement avatarUrlElement = userProfileObject.get("avatarUrl");
                if (avatarUrlElement != null && !avatarUrlElement.isJsonNull()) {
                    GlobalState.avatarUrl = avatarUrlElement.getAsString();
                } else {
                    GlobalState.avatarUrl = null;
                }

                // 方法依然返回 token，表示登录成功
                return token;

            } else {
                String message = jsonObject.get("message").getAsString();
                throw new IOException(message);
            }
        }
    }
    /**
     * 调用后端 /auth/register 接口
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱
     * @param nickname 昵称
     * @throws IOException 当网络请求失败或业务逻辑失败时抛出异常
     */
    public void register(String username, String password, String email, String nickname) throws IOException {
        String url = BASE_URL + "/auth/register";

        // 1. 使用 Map 来构建 JSON 对象，更灵活
        java.util.Map<String, String> registrationData = new java.util.HashMap<>();
        registrationData.put("username", username);
        registrationData.put("password", password);
        registrationData.put("email", email);
        registrationData.put("nickname", nickname);

        // 2. 使用 Gson 将 Map 转换为 JSON 字符串
        String jsonBody = gson.toJson(registrationData);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            // 3. 检查后端返回的业务状态码
            // 如果 status 不为 0，说明注册失败，我们将后端消息作为异常抛出
            if (jsonObject.get("status").getAsInt() != 0) {
                String message = jsonObject.get("message").getAsString();
                throw new IOException(message); // 例如 "注册失败，用户名重复"
            }

            // status 为 0，说明成功，方法正常结束
            System.out.println("注册成功!");
        }
    }

    /**
     * 调用后端 /auth/logout 接口
     * @throws IOException 当网络请求或业务逻辑失败时
     */
    public void logout() throws IOException {
        // 登出前必须是登录状态，所以 token 不能为空
        if (GlobalState.authToken == null) {
            // 理论上不应该发生，但作为防御性编程
            System.err.println("用户未登录，无需调用登出接口");
            return;
        }

        String url = BASE_URL + "/auth/logout";

        // 创建一个空的请求体，因为登出是POST请求但不需要body
        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                // 关键：根据后端API文档，在请求头中附带 token
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("登出请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                String message = jsonObject.get("message").getAsString();
                throw new IOException(message);
            }

            System.out.println("成功调用后端登出接口");
        }
    }

    /**
     * 根据用户ID获取用户详细信息
     * @param userId 要查询的用户ID
     * @return 包含用户信息的 JsonObject
     */
    public JsonObject getUserProfile(long userId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/friends/" + userId + "/details";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取用户信息失败: " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                return jsonObject.getAsJsonObject("data");
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 /friends 接口，获取当前用户的好友列表
     * @return 一个包含好友信息的 Map 列表
     */
    public List<Map<String, Object>> getFriends() throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/friends";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取好友列表失败: " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                // 使用 TypeToken 来帮助 Gson 解析泛型列表
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                return gson.fromJson(jsonObject.get("data"), listType);
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 根据昵称模糊搜索用户
     * @param nickname 要搜索的昵称
     * @return 包含用户搜索结果的 Map 列表
     */
    public List<Map<String, Object>> searchUsers(String nickname) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        // 使用 HttpUrl.Builder 来安全地构建带查询参数的 URL
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/friends/search").newBuilder();
        urlBuilder.addQueryParameter("nickname", nickname);

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("搜索用户失败: " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                return gson.fromJson(jsonObject.get("data"), listType);
            } else {
                return new ArrayList<>(); // 返回空列表
            }
        }
    }

    /**
     * 发送好友申请
     * @param userId 目标用户的ID
     */
    public void sendFriendRequest(long userId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/friends/requests/" + userId;
        RequestBody body = RequestBody.create(new byte[0]); // 空的 POST 请求体

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("发送好友申请失败: " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                // 将后端返回的业务错误信息（如“不能添加自己为好友”）抛出
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 获取待处理的好友申请列表
     * @return 包含申请人信息的 Map 列表
     */
    public List<Map<String, Object>> getFriendRequests() throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/friends/requests/pending";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取好友申请列表失败: " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> resultList = gson.fromJson(jsonObject.get("data"), listType);

                // ==========================================================
                // ========== 在这里加上这行打印语句 ==========
                System.out.println("DEBUG (ApiClient): 成功从JSON解析出 " + resultList.size() + " 条好友申请。");
                // ==========================================================

                return resultList;
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 同意好友申请
     * @param userId 申请人的用户ID
     */
    public void acceptFriendRequest(long userId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/friends/requests/" + userId + "/accept";
        RequestBody body = RequestBody.create(new byte[0]); // PUT 请求通常也需要一个请求体，即使是空的

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .put(body) // 根据你的API文档，这里是 PUT 请求
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("同意好友申请失败: " + response);

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 API 创建一个自定义房间。
     * 对应文档："四、房间模块 I.创建房间"
     * @return 成功时返回房间的 ID 字符串。
     * @throws IOException 当网络或业务逻辑失败时抛出。
     */
    /*public String createRoom() throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/custom-room/create";
        RequestBody body = RequestBody.create(new byte[0]); // POST 请求需要一个请求体，即使是空的

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("创建房间请求失败: " + response.code());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                // 成功时，从 "data" 字段获取房间 ID 并返回
                return jsonObject.get("data").getAsString();
            } else {
                // 失败时，抛出后端返回的错误信息
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }*/

    // 修改为（使其接受 mapId 参数）
    public String createRoom(String mapId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        // 根据新API文档，使用带参数的URL
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/create"))
                .newBuilder()
                .addQueryParameter("mapId", mapId)
                .build();

        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("创建房间请求失败: " + response.code());
            }
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() == 0) {
                return jsonObject.get("data").getAsString();
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * (房主) 移交房主权限给房间内另一位玩家
     * @param newHostId 新房主的用户ID
     * @throws IOException
     */
    public void transferHost(long newHostId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/transfer-host"))
                .newBuilder()
                .addQueryParameter("newHostId", String.valueOf(newHostId))
                .build();

        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("移交房主请求失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 API 根据房间 ID 加入一个房间。
     * 对应文档："四、房间模块 II.加入房间"
     * @param roomId 要加入的房间的密钥/ID。
     * @return 成功时返回房间的 ID 字符串。
     * @throws IOException 当网络或业务逻辑失败时抛出。
     */
    public String joinRoom(String roomId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        // 使用 HttpUrl.Builder 来安全地构建带查询参数的 URL
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/join"))
                .newBuilder()
                .addQueryParameter("roomId", roomId)
                .build();

        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("加入房间请求失败: " + response.code());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                return jsonObject.get("data").getAsString();
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 API 离开当前所在的房间。
     * 对应文档："四、房间模块 III.离开房间"
     * @throws IOException 当网络或业务逻辑失败时抛出。
     */
    public void leaveRoom() throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/custom-room/leave";
        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("离开房间请求失败: " + response.code());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
            // 离开房间成功，没有返回值，方法正常结束即可
        }
    }

    /**
     * (房主) 从房间中踢出一位玩家
     * 对应文档："四、房间模块 V.踢出玩家"
     * @param targetId 被踢出玩家的用户ID
     */
    public void kickPlayer(long targetId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/kick"))
                .newBuilder()
                .addQueryParameter("targetId", String.valueOf(targetId))
                .build();

        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("踢出玩家请求失败: " + response);

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 邀请好友加入当前所在的房间。
     * 对应文档："四、房间模块 IV.邀请好友"
     * @param friendId 被邀请好友的用户ID
     * @throws IOException 当网络请求或业务逻辑失败时抛出异常
     */
    public void inviteFriend(long friendId) throws IOException {
        // 检查是否已登录，因为所有需要认证的接口都依赖这个 token
        if (GlobalState.authToken == null) {
            throw new IllegalStateException("Not logged in");
        }

        // 1. 使用 OkHttp 的 HttpUrl.Builder 来安全地构建带查询参数的 URL
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/invite"))
                .newBuilder()
                .addQueryParameter("friendId", String.valueOf(friendId))
                .build();

        // 2. 创建一个空的 POST 请求体，因为这个接口不需要发送 JSON 数据
        RequestBody body = RequestBody.create(new byte[0]);

        // 3. 构建请求，附上必需的 Authorization 请求头
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        // 4. 发送请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("发送邀请请求失败: " + response);
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            // 5. 检查后端返回的业务状态码
            if (jsonObject.get("status").getAsInt() != 0) {
                // 如果失败，将后端返回的友好提示信息作为异常抛出
                throw new IOException(jsonObject.get("message").getAsString());
            }

            // 如果 status 为 0，说明邀请已成功发送，方法正常结束
            System.out.println("成功发送邀请给用户: " + friendId);
        }
    }

    /**
     * 接受房间邀请。
     * 对应文档："四、房间模块 IX.接收房间邀请"
     * @param roomId 要加入的房间的ID
     * @throws IOException 当网络或业务逻辑失败时抛出
     */
    public void acceptInvite(String roomId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/accept-invite"))
                .newBuilder()
                .addQueryParameter("roomId", roomId)
                .build();

        RequestBody body = RequestBody.create(new byte[0]); // 空的POST请求体

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("接受邀请请求失败: " + response.code());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }

            System.out.println("成功接受邀请，加入房间: " + roomId);
        }
    }

    /**
     * 拒绝房间邀请。
     * 对应文档："四、房间模块 X.拒绝房间邀请"
     * @param inviterId 邀请者的用户ID
     * @throws IOException 当网络或业务逻辑失败时抛出
     */
    public void rejectInvite(long inviterId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/custom-room/reject-invite"))
                .newBuilder()
                .addQueryParameter("inviterId", String.valueOf(inviterId))
                .build();

        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("拒绝邀请请求失败: " + response.code());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                // 根据文档，拒绝邀请失败时也可能返回 status:0 message:"操作成功"，所以这里可能不需要抛异常
                // 但为了严谨，我们仍然检查非0状态
                throw new IOException(jsonObject.get("message").getAsString());
            }

            System.out.println("成功拒绝来自用户 " + inviterId + " 的邀请");
        }
    }

    /**
     * 更新用户昵称
     * @param newNickname 新的昵称
     * @return 包含更新后用户信息的 JsonObject
     */
    public JsonObject updateNickname(String newNickname) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL + "/users/me/nickname"))
                .newBuilder()
                .addQueryParameter("newNickname", newNickname)
                .build();

        // PUT 请求需要一个请求体，即使是空的
        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .put(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("更新昵称失败: " + response);

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() == 0) {
                return jsonObject.getAsJsonObject("data");
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    public List<Map<String, Object>> getHistory() throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");
        String url = BASE_URL + "/history";
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + GlobalState.authToken).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取历史战绩失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() == 0) {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                return gson.fromJson(jsonObject.get("data"), listType);
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    public JsonObject getHistoryDetails(long matchId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");
        String url = BASE_URL + "/history/" + matchId;
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + GlobalState.authToken).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("获取战绩详情失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            // 【新增】打印从后端接收到的原始JSON字符串
            System.out.println("DEBUG: Received history details JSON from backend: " + responseBody);
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() == 0) {
                return jsonObject.getAsJsonObject("data");
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 上传用户头像文件
     * @param avatarFile 用户选择的图片文件
     * @return 包含更新后用户信息的 JsonObject
     */
    public JsonObject uploadAvatar(File avatarFile) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/users/me/avatar";

        // 判断文件类型，用于设置 MediaType
        String contentType = "image/png"; // 默认为 png
        if (avatarFile.getName().toLowerCase().endsWith(".jpg") || avatarFile.getName().toLowerCase().endsWith(".jpeg")) {
            contentType = "image/jpeg";
        }

        // 1. 创建一个 MultipartBody，用于文件上传
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "avatar", // 这个是后端接口定义的参数名
                        avatarFile.getName(), // 文件名
                        RequestBody.create(avatarFile, MediaType.parse(contentType)) // 文件内容
                )
                .build();

        // 2. 创建 POST 请求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(requestBody)
                .build();

        // 3. 发送请求并解析响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("上传头像失败: " + response);
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() == 0) {
                return jsonObject.getAsJsonObject("data");
            } else {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 刪除好友
     * @param friendId 要刪除的好友的用戶 ID
     * @throws IOException 當網路或業務邏輯失敗時拋出
     */
    public void deleteFriend(long friendId) throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        // 根據我們建議的 API 設計，URL 是 /friends/{friendId}
        String url = BASE_URL + "/friends/" + friendId;

        // 建立一個 DELETE 請求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .delete() // 使用 DELETE 方法
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("刪除好友請求失敗: " + response);
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 调用后端 /matchmaking/start 接口开始匹配
     * @throws IOException 当网络或业务逻辑失败时
     */
    public void startMatchmaking(String mapId, String characterId) throws IOException {
        if (GlobalState.authToken == null) {
            throw new IllegalStateException("用户未登录，无法开始匹配");
        }

        String url = BASE_URL + "/matchmaking/start";

        // 1. 创建包含地图和角色ID的JSON体
        JsonObject matchmakingData = new JsonObject();
        matchmakingData.addProperty("mapId", mapId);
        matchmakingData.addProperty("characterId", characterId);
        String jsonBody = gson.toJson(matchmakingData);

        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("开始匹配请求失败: " + response.code());
            }
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * 【兼容版】调用后端 /matchmaking/start 接口开始匹配 (不带参数，使用服务器默认设置)
     * @throws IOException 当网络或业务逻辑失败时
     */
    public void startMatchmaking() throws IOException {
        if (GlobalState.authToken == null) {
            throw new IllegalStateException("用户未登录，无法开始匹配");
        }

        String url = BASE_URL + "/matchmaking/start";

        // 1. 创建一个空的请求体，以触发后端的向后兼容逻辑
        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("开始匹配请求失败: " + response.code());
            }
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }
    /**
     * 调用后端 /matchmaking/cancel 接口取消匹配
     * @throws IOException 当网络或业务逻辑失败时
     */
    public void cancelMatchmaking() throws IOException {
        if (GlobalState.authToken == null) {
            throw new IllegalStateException("用户未登录，无法取消匹配");
        }

        String url = BASE_URL + "/matchmaking/cancel";
        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("取消匹配请求失败: " + response.code());
            }
            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.get("status").getAsInt() != 0) {
                throw new IOException(jsonObject.get("message").getAsString());
            }
        }
    }

    /**
     * (房主) 开始自定义房间的游戏。
     * 对应文档："四、房间模块 VIII.开始游戏"
     * @throws IOException 当网络或业务逻辑失败时抛出。
     */
    public void startGame() throws IOException {
        if (GlobalState.authToken == null) throw new IllegalStateException("Not logged in");

        String url = BASE_URL + "/custom-room/start-game";
        RequestBody body = RequestBody.create(new byte[0]); // 空的POST请求体

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + GlobalState.authToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("开始游戏请求失败: " + response.code());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            if (jsonObject.get("status").getAsInt() != 0) {
                // 如果后端返回业务错误（例如"房间未满员"），则抛出异常
                throw new IOException(jsonObject.get("message").getAsString());
            }
            // 成功时，后端会通过WebSocket广播，所以这里无需做其他事
            System.out.println("成功发送“开始游戏”请求，等待服务器广播...");
        }
    }
}
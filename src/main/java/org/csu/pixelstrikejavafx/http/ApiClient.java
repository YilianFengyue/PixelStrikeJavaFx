package org.csu.pixelstrikejavafx.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.csu.pixelstrikejavafx.state.GlobalState;
import java.util.List;
import java.util.Map;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;

import java.io.IOException;

public class ApiClient {
    private static final String BASE_URL = "http://localhost:8080";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

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
                // ==========================================================
                // ==================== 核心改动在这里 ====================
                // ==========================================================

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

}
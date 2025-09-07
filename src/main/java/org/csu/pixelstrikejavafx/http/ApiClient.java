package org.csu.pixelstrikejavafx.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.csu.pixelstrikejavafx.state.GlobalState; // 我们稍后创建这个类

import java.io.IOException;

public class ApiClient {
    private static final String BASE_URL = "http://localhost:8080";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    /**
     * 调用后端 /auth/login 接口
     * @param username 用户名
     * @param password 密码
     * @return 成功时返回 token, 失败时抛出异常
     */
    public String login(String username, String password) throws IOException {
        // 1. 根据你的API文档，构建请求的JSON体
        String jsonBody = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        // 2. 创建一个POST请求
        Request request = new Request.Builder()
                .url(BASE_URL + "/auth/login")
                .post(body)
                .build();

        // 3. 发送请求并获取响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            // 4. 使用Gson解析返回的JSON数据
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);

            // 5. 根据文档中的 status 字段判断业务是否成功
            if (jsonObject.get("status").getAsInt() == 0) {
                String token = jsonObject.get("data").getAsString();
                // 将获取到的token存到全局状态中
                GlobalState.authToken = token;
                return token;
            } else {
                // 业务失败 (例如密码错误)
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

}
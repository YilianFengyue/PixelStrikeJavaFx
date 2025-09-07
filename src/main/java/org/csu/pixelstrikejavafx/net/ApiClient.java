package org.csu.pixelstrikejavafx.net;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;

public final class ApiClient {
    private volatile String token;
    private final HttpClient http = HttpClient.newHttpClient();

    public void setToken(String t) { this.token = t; }

    public CompletableFuture<JsonObject> getJson(String url, String bearer) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        b.header("Accept", "application/json");
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> JsonParser.parseString(r.body()).getAsJsonObject());
    }

    // body 可为 null
    public CompletableFuture<JsonObject> postJson(String url, JsonObject body, String bearer) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body==null? "{}" : body.toString()))
                .header("Content-Type", "application/json").header("Accept", "application/json");
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> JsonParser.parseString(r.body()).getAsJsonObject());
    }
}

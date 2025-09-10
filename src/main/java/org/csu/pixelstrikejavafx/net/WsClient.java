package org.csu.pixelstrikejavafx.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class WsClient implements WebSocket.Listener {
    private final HttpClient http = HttpClient.newHttpClient();
    private volatile WebSocket ws;
    private Consumer<String> onMessage = s -> {};
    private Runnable onOpen = () -> {};
    private Runnable onClose = () -> {};

    public void connect(String url) {
        http.newWebSocketBuilder().buildAsync(URI.create(url), this)
                .thenAccept(w -> this.ws = w);
    }
    public void send(String text) { var w = ws; if (w != null) w.sendText(text, true); }
    public void close() { var w = ws; if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); }

    // callbacks
    public void onMessage(Consumer<String> cb) { this.onMessage = cb; }
    public void onOpen(Runnable cb) { this.onOpen = cb; }
    public void onClose(Runnable cb) { this.onClose = cb; }

    // Listener
    @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); onOpen.run(); }
    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        onMessage.accept(data.toString()); webSocket.request(1); return CompletableFuture.completedFuture(null);
    }
    @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        onClose.run(); return CompletableFuture.completedFuture(null);
    }
}

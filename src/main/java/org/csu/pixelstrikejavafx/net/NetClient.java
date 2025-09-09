package org.csu.pixelstrikejavafx.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public final class NetClient {
    private WebSocket ws;
    private Consumer<String> onMessage = s -> {};
    private Runnable onOpen = () -> {};
    private String urlForLog = "";

    public void connect(String url, Runnable onOpen, Consumer<String> onMessage) {
        this.urlForLog = url;
        this.onOpen = onOpen;
        this.onMessage = onMessage;

        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        ws = webSocket;
                        System.out.println("[WS] OPEN " + urlForLog);
                        try { onOpen.run(); } finally { }
                        webSocket.request(1);                 // [FIX] 首次拉取一条
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String s = data.toString();
                        System.out.println("[WS] << " + (s.length() > 160 ? s.substring(0,160)+"..." : s));
                        onMessage.accept(s);
                        webSocket.request(1);                 // [FIX] 再次申请下一条
                        return null; // returning null 也可；或 CompletableFuture.completedFuture(null)
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.out.println("[WS] ERROR " + urlForLog + " : " + error);
                        WebSocket.Listener.super.onError(webSocket, error);
                    }
                });
        System.out.println("[WS] CONNECTING " + url);
    }

    public void send(String json) {
        if (ws != null) {
            ws.sendText(json, true);
        } else {
            System.out.println("[WS] SEND skipped (ws==null)");
        }
    }

    public void sendJoin(String name) {
        String json = "{\"type\":\"join\",\"name\":\"" + name + "\"}";
        System.out.println("[WS] >> " + json);
        // ★★★ 真的发出去
        send(json); // <-- 新增：别再注释
    }

    public void sendState(double x, double y, double vx, double vy, boolean facing, boolean onGround,
                          long ts, long seq) {
        String j = String.format(
                "{\"type\":\"state\",\"x\":%.2f,\"y\":%.2f,\"vx\":%.2f,\"vy\":%.2f," +
                        "\"facing\":%s,\"onGround\":%s,\"ts\":%d,\"seq\":%d}",
                x, y, vx, vy, facing, onGround, ts, seq
        );
        send(j);
    }
    //10参版本
    public void sendState(double x, double y, double vx, double vy, boolean facing, boolean onGround,
                          String anim, String phase, long ts, long seq) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"type\":\"state\"")
                .append(",\"x\":").append(String.format(java.util.Locale.US,"%.2f",x))
                .append(",\"y\":").append(String.format(java.util.Locale.US,"%.2f",y))
                .append(",\"vx\":").append(String.format(java.util.Locale.US,"%.2f",vx))
                .append(",\"vy\":").append(String.format(java.util.Locale.US,"%.2f",vy))
                .append(",\"facing\":").append(facing)
                .append(",\"onGround\":").append(onGround);
        if (anim  != null) sb.append(",\"anim\":\"").append(anim).append('"');
        if (phase != null) sb.append(",\"phase\":\"").append(phase).append('"');
        sb.append(",\"ts\":").append(ts).append(",\"seq\":").append(seq).append("}");
        send(sb.toString());
    }
    // [NEW] 本地射击上报（hitscan）
    public void sendShot(double ox, double oy, double dx, double dy,
                         double range, int damage, long ts, long seq) {
        String j = String.format(java.util.Locale.US,
                "{\"type\":\"shot\",\"ox\":%.2f,\"oy\":%.2f,\"dx\":%.4f,\"dy\":%.4f," +
                        "\"range\":%.2f,\"damage\":%d,\"ts\":%d,\"seq\":%d}",
                ox, oy, dx, dy, range, damage, ts, seq);
        send(j);
    }
}

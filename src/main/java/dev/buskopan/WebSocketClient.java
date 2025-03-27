package dev.buskopan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class WebSocketClient {
    public static void main(String[] args) throws InterruptedException {
        URI uri = URI.create("ws://localhost:8080");
        CountDownLatch latch = new CountDownLatch(1);

        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("Conexão WebSocket aberta");
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        System.out.println("Mensagem recebida do servidor: " + data);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        System.out.println("Mensagem binária recebida");
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        System.out.println("Conexão fechada: " + statusCode + " - " + reason);
                        latch.countDown();
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        System.err.println("Erro ocorrido: " + error.getMessage());
                        error.printStackTrace();
                    }
                }).thenAccept(webSocket -> {
                    // Enviar mensagem após conectar
                    webSocket.sendText("Hello WebSocket!", true);
                });

        latch.await(); // Mantém o programa rodando até que a conexão seja fechada
    }
}
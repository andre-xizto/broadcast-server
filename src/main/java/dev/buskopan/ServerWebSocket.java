package dev.buskopan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerWebSocket {
    private static ServerSocket socket;
    private static ExecutorService threadPool;
    private static CopyOnWriteArrayList<Socket> clientsList;

    public static void start(int port) throws IOException {
        socket = new ServerSocket(port);
        threadPool = Executors.newCachedThreadPool(Executors.defaultThreadFactory());
        clientsList = new CopyOnWriteArrayList<>();

        System.out.println("Server Web Socket running at " + port);

        while(true) {
            Socket client = socket.accept();
            clientsList.add(client);
            threadPool.execute(() -> handleClient(client));
        }
    }

    public static void stop() {
        try {
            socket.close();
            threadPool.shutdownNow();

            for (Socket client : clientsList) {
                client.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            clientsList.clear();
        }
    }

    private static void handleClient(Socket client) {
        try(BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream os = client.getOutputStream();
        ) {
            String requestLine;
            String websocketClienteKey = null;

            while (!(requestLine = br.readLine()).isEmpty()) {
                if (requestLine.startsWith("Sec-WebSocket-Key: ")) {
                    websocketClienteKey = requestLine.split(": ")[1];
                    break;
                }
            }

            if (websocketClienteKey != null) {
                executeHandshake(websocketClienteKey, os);

                while (true) {
                    byte[] buffer = new byte[1024];
                    int bytesRead = client.getInputStream().read(buffer);
                    if (bytesRead == -1) break;

                    String decodedMessage = decodeMessage(bytesRead, buffer);
                    System.out.println("Message received: " + decodedMessage);

                    byte[] response = encodeMessage("Echo: " + decodedMessage);
                    System.out.println("Sending messages to " + clientsList.size() + " clients");
                    broadcastMessage(response);
                }
            }
            client.close();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                System.out.println("Error closing client socket: "+e.getMessage());
            }
            clientsList.remove(client);
        }
    }

    private static void broadcastMessage(byte[] response) {
        for (Socket client : clientsList) {
            try {
                OutputStream os = client.getOutputStream();
                os.write(response);
                os.flush();
            } catch (IOException e) {
                try {
                    client.close();
                } catch (IOException ex) {
                    System.out.println("Error when tried close dead client");
                } finally {
                    clientsList.remove(client);
                }
            }
        }
    }

    private static byte[] encodeMessage(String msg) {
        byte[] messageBytes = msg.getBytes(StandardCharsets.UTF_8);
        int messageLength = messageBytes.length;
        byte[] frame = new byte[2 + messageLength];

        frame[0] = (byte) 129; // Final Frame e opcode Text (0x81)
        frame[1] = (byte) messageLength; // Sem máscara e tamanho
        System.arraycopy(messageBytes, 0, frame, 2, messageLength);

        return frame;
    }

    private static String decodeMessage(int read, byte[] buffer) {
        byte mask = buffer[1];
        int length = mask & 0x7f;
        int maskIndex = 2;
        int dataIndex = maskIndex + 4;

        byte[] decoded = new byte[length];
        for (int i = 0; i < length; i++) {
            decoded[i] = (byte) (buffer[dataIndex + i] ^ buffer[maskIndex + (i % 4)]);
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static void executeHandshake(String websocketClienteKey, OutputStream os) throws NoSuchAlgorithmException, IOException {
        String acceptKey = generateAcceptKey(websocketClienteKey);
        String response = "HTTP/1.1 101 Switching Protocols\r\n"+
                "Upgrade: websocket\r\n"+
                "Connection: upgrade\r\n"+
                "Sec-WebSocket-Accept: "+ acceptKey + "\r\n\r\n";

        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.flush();
        System.out.println("Handshake has been successful");
    }

    private static String generateAcceptKey(String websocketClienteKey) throws NoSuchAlgorithmException {
        String magicString = websocketClienteKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(magicString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}

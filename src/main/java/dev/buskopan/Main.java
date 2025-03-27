package dev.buskopan;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            ServerWebSocket.start(8080);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
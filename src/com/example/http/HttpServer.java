package com.example.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class HttpServer {
    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private final ExecutorService pool;

    // 可配置线程池大小
    public HttpServer(int port, int nThreads) {
        this.port = port;
        this.pool = Executors.newFixedThreadPool(nThreads);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("HTTP Server started on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                // 提交到线程池处理
                pool.submit(new ClientHandler(clientSocket, new SimpleRouter())); // SimpleRouter 是临时默认路由
            } catch (IOException e) {
                if (!running) break;
                e.printStackTrace();
            }
        }
        shutdown();
    }

    public void shutdown() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("HTTP Server stopped.");
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = new HttpServer(8080, 10);
        server.start();
    }
}

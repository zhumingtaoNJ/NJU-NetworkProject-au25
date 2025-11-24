package com.example.http;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Router router;
    // 连接超时（读取下一请求的阻塞保护），单位毫秒
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    public ClientHandler(Socket socket, Router router) {
        this.socket = socket;
        this.router = router;
        try {
            this.socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            boolean keepAlive = true;
            while (keepAlive && !socket.isClosed()) {
                // 1) 读取请求头（按行）
                String requestLine = readLine(in);
                if (requestLine == null) break; // 客户端关闭连接

                // 如果是空行（客户端可能发了多余的 CRLF），跳过并尝试继续读取
                if (requestLine.trim().isEmpty()) {
                    continue;
                }

                StringBuilder headerBlock = new StringBuilder();
                headerBlock.append(requestLine).append("\r\n");

                // 读取头部直到空行
                String line;
                Map<String, String> headers = new HashMap<>();
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    headerBlock.append(line).append("\r\n");
                    int idx = line.indexOf(":");
                    if (idx > 0) {
                        String name = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        headers.put(name.toLowerCase(Locale.ROOT), value);
                    }
                }
                headerBlock.append("\r\n");

                // 2) 读取 body（如果有）
                int contentLength = 0;
                if (headers.containsKey("content-length")) {
                    try {
                        contentLength = Integer.parseInt(headers.get("content-length"));
                    } catch (NumberFormatException ignored) { contentLength = 0; }
                }
                byte[] body = new byte[0];
                if (contentLength > 0) {
                    body = readBytes(in, contentLength);
                }

                String rawRequest = headerBlock.toString() + (body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "");
                // 3) 将请求交给路由模块处理（router 返回 HttpResponse）
                HttpResponse response;
                try {
                    response = router.handle(rawRequest);
                } catch (Exception e) {
                    e.printStackTrace();
                    response = HttpResponse.internalServerError();
                }

                // 4) 写回响应
                out.write(response.toBytes());
                out.flush();

                // 5) decide keep-alive: 优先看请求头 Connection，若没有，则看 HTTP/1.1 默认 keep-alive
                String connHeader = headers.getOrDefault("connection", "");
                if (connHeader.equalsIgnoreCase("close")) {
                    keepAlive = false;
                } else if (connHeader.equalsIgnoreCase("keep-alive")) {
                    keepAlive = true;
                } else {
                    // 没有明确 Connection 字段，则根据版本判断：如果请求行含 HTTP/1.1 默认 keep-alive
                    if (requestLine.contains("HTTP/1.1")) keepAlive = true;
                    else keepAlive = false;
                }
            }
        } catch (SocketTimeoutException e) {
            // 读超时，关闭连接
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // 逐行读取（CRLF 结尾），返回不包含 CRLF 的行（或 null 表示流结束）
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (buf.size() == 0) return null;
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
            previous = b;
        }
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    private byte[] readBytes(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            int r = in.read(data, read, length - read);
            if (r == -1) throw new EOFException("Unexpected EOF when reading body");
            read += r;
        }
        return data;
    }
}

package com.example.http.client;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * HttpClient：简单的HTTP客户端，使用Socket发送GET/POST请求，处理响应，包括重定向和条件GET。
 * 支持长连接（Keep-Alive），命令行交互。
 * 可以测试服务器的注册/登录接口（POST /register 或 /login，body格式：username=xx&password=yy）。
 */
public class HttpClient {

    private static final String HOST = "localhost";  // 服务器主机，默认本地
    private static final int PORT = 8080;  // 服务器端口
    private static final int MAX_REDIRECTS = 5;  // 最大重定向次数，防止无限循环

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Socket socket = null;
        boolean keepAlive = true;

        try {
            // 建立Socket连接
            socket = new Socket(HOST, PORT);
            System.out.println("已连接到服务器: " + HOST + ":" + PORT);

            while (keepAlive) {
                // 用户输入请求细节
                System.out.print("输入方法 (GET/POST): ");
                String method = scanner.nextLine().toUpperCase();
                System.out.print("输入路径 (e.g., /hello, /register): ");
                String path = scanner.nextLine();
                String body = "";
                if ("POST".equals(method)) {
                    System.out.print("输入body (e.g., username=test&password=123): ");
                    body = scanner.nextLine();
                }

                // 发送请求并获取响应
                HttpResponseData response = sendRequest(socket, method, path, body, null, 0);

                // 显示响应
                System.out.println("响应状态: " + response.statusCode + " " + response.reason);
                System.out.println("响应头: ");
                response.headers.forEach((k, v) -> System.out.println(k + ": " + v));
                if (response.body.length > 0) {
                    String contentType = response.headers.getOrDefault("Content-Type", "text/plain");
                    if (contentType.startsWith("text/")) {
                        System.out.println("响应Body: " + new String(response.body));
                    } else {
                        // 非文本类型，保存到文件
                        saveToFile(response.body, "response_file");
                        System.out.println("响应Body: 非文本类型，已保存到response_file (长度: " + response.body.length + " bytes)");
                    }
                }

                // 检查是否保持连接
                String connection = response.headers.getOrDefault("Connection", "close");
                keepAlive = "keep-alive".equalsIgnoreCase(connection);
                if (!keepAlive) {
                    System.out.println("连接关闭.");
                } else {
                    System.out.print("继续发送请求? (y/n): ");
                    keepAlive = "y".equalsIgnoreCase(scanner.nextLine());
                }
            }
        } catch (IOException e) {
            System.err.println("连接错误: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 发送HTTP请求并处理响应，包括重定向。
     * @param socket Socket连接（支持复用）
     * @param method 请求方法 (GET/POST)
     * @param path 请求路径
     * @param body 请求体 (POST时使用)
     * @param extraHeaders 额外头 (e.g., If-Modified-Since for 304)
     * @param redirectCount 当前重定向次数
     * @return 响应数据
     * @throws IOException IO异常
     */
    private static HttpResponseData sendRequest(Socket socket, String method, String path, String body,
                                                Map<String, String> extraHeaders, int redirectCount) throws IOException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("重定向次数过多");
        }

        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        // 构建请求字符串
        StringBuilder request = new StringBuilder();
        request.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(HOST).append("\r\n");
        request.append("Connection: keep-alive\r\n");  // 默认请求长连接
        request.append("User-Agent: SimpleHttpClient\r\n");
        if (extraHeaders != null) {
            extraHeaders.forEach((k, v) -> request.append(k).append(": ").append(v).append("\r\n"));
        }
        if ("POST".equals(method) && !body.isEmpty()) {
            request.append("Content-Type: application/x-www-form-urlencoded\r\n");
            request.append("Content-Length: ").append(body.length()).append("\r\n");
        }
        request.append("\r\n");
        if ("POST".equals(method) && !body.isEmpty()) {
            request.append(body);
        }

        // 发送请求
        out.write(request.toString().getBytes("UTF-8"));
        out.flush();

        // 读取响应
        HttpResponseData response = readResponse(in);

        // 处理状态码
        if (response.statusCode == 301 || response.statusCode == 302) {
            String location = response.headers.get("Location");
            if (location == null) {
                throw new IOException("重定向无Location头");
            }
            System.out.println("重定向到: " + location);
            // 解析新URL（简单处理，假设同域或绝对URL）
            URL newUrl = new URL(location.startsWith("http") ? location : "http://" + HOST + ":" + PORT + location);
            // 如果主机/端口变化，需新Socket；否则复用
            if (!newUrl.getHost().equals(HOST) || newUrl.getPort() != PORT) {
                socket.close();
                socket = new Socket(newUrl.getHost(), newUrl.getPort() == -1 ? 80 : newUrl.getPort());
            }
            // 递归重定向
            return sendRequest(socket, method, newUrl.getPath(), body, extraHeaders, redirectCount + 1);
        } else if (response.statusCode == 304) {
            System.out.println("资源未修改 (304 Not Modified)");
        }

        return response;
    }

    /**
     * 从InputStream读取HTTP响应。
     * @param in 输入流
     * @return 响应数据
     * @throws IOException IO异常
     */
    private static HttpResponseData readResponse(InputStream in) throws IOException {
        HttpResponseData data = new HttpResponseData();

        // 读取响应行
        String responseLine = readLine(in);
        if (responseLine == null) {
            throw new IOException("无响应");
        }
        String[] parts = responseLine.split(" ", 3);
        data.statusCode = Integer.parseInt(parts[1]);
        data.reason = parts.length > 2 ? parts[2] : "";

        // 读取头
        String line;
        while (!(line = readLine(in)).isEmpty()) {
            int colon = line.indexOf(":");
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                data.headers.put(key, value);
            }
        }

        // 读取body
        int contentLength = 0;
        if (data.headers.containsKey("Content-Length")) {
            contentLength = Integer.parseInt(data.headers.get("Content-Length"));
        }
        if (contentLength > 0) {
            data.body = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                read += in.read(data.body, read, contentLength - read);
            }
        }

        return data;
    }

    /**
     * 读取一行（以\r\n结束）。
     * @param in 输入流
     * @return 行内容（不含\r\n）
     * @throws IOException IO异常
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) {
                return null;
            }
            if (prev == '\r' && b == '\n') {
                break;
            }
            baos.write(b);
            prev = b;
        }
        // 移除最后的\r
        byte[] bytes = baos.toByteArray();
        if (bytes.length > 0 && bytes[bytes.length - 1] == '\r') {
            bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
        }
        return new String(bytes, "UTF-8");
    }

    /**
     * 保存body到文件（用于非文本MIME）。
     * @param body 字节数组
     * @param filename 文件名
     * @throws IOException IO异常
     */
    private static void saveToFile(byte[] body, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(body);
        }
    }

    /**
     * 响应数据结构。
     */
    private static class HttpResponseData {
        int statusCode;
        String reason;
        Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
    }
}

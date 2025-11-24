package com.example.http;

import java.util.Locale;

public class SimpleRouter implements Router {
    @Override
    public HttpResponse handle(String rawRequest) {
        // 非严格解析，仅做 demo 用
        String firstLine = rawRequest.split("\r\n", 2)[0];
        String[] parts = firstLine.split(" ");
        String method = parts[0].toUpperCase(Locale.ROOT);
        String path = parts.length > 1 ? parts[1] : "/";

        if ("GET".equals(method)) {
            if ("/".equals(path) || "/index".equals(path)) {
                return HttpResponse.okText("Hello from SimpleHttpServer");
            } else if ("/hello".equals(path)) {
                return HttpResponse.okText("hello world");
            } else {
                return HttpResponse.notFound();
            }
        } else if ("POST".equals(method)) {
            if ("/echo".equals(path)) {
                // 直接返回请求体（简单 demo 提取）
                String[] split = rawRequest.split("\r\n\r\n", 2);
                String body = split.length > 1 ? split[1] : "";
                HttpResponse r = new HttpResponse(200, "OK");
                r.setHeader("Content-Type", "text/plain; charset=utf-8");
                r.setBody(("echo:" + body).getBytes());
                return r;
            }
            return HttpResponse.methodNotAllowed();
        }
        return HttpResponse.methodNotAllowed();
    }
}

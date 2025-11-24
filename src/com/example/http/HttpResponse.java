package com.example.http;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    private int statusCode;
    private String reason;
    private Map<String,String> headers = new HashMap<>();
    private byte[] body = new byte[0];

    public HttpResponse(int statusCode, String reason) {
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public void setBody(byte[] body) {
        this.body = body;
        this.headers.put("Content-Length", String.valueOf(body.length));
    }

    public void setHeader(String k, String v) { headers.put(k, v); }

    public byte[] toBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(reason).append("\r\n");
        headers.forEach((k,v) -> sb.append(k).append(": ").append(v).append("\r\n"));
        sb.append("\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[head.length + body.length];
        System.arraycopy(head, 0, res, 0, head.length);
        System.arraycopy(body, 0, res, head.length, body.length);
        return res;
    }

    public static HttpResponse okText(String text) {
        HttpResponse r = new HttpResponse(200, "OK");
        r.setHeader("Content-Type", "text/plain; charset=utf-8");
        r.setBody(text.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    public static HttpResponse notFound() {
        HttpResponse r = new HttpResponse(404, "Not Found");
        r.setHeader("Content-Type", "text/plain; charset=utf-8");
        r.setBody("404 Not Found".getBytes(StandardCharsets.UTF_8));
        return r;
    }

    public static HttpResponse methodNotAllowed() {
        HttpResponse r = new HttpResponse(405, "Method Not Allowed");
        r.setHeader("Content-Type", "text/plain; charset=utf-8");
        r.setBody("405 Method Not Allowed".getBytes(StandardCharsets.UTF_8));
        return r;
    }

    public static HttpResponse internalServerError() {
        HttpResponse r = new HttpResponse(500, "Internal Server Error");
        r.setHeader("Content-Type", "text/plain; charset=utf-8");
        r.setBody("500 Internal Server Error".getBytes(StandardCharsets.UTF_8));
        return r;
    }
}

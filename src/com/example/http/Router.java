package com.example.http;

public interface Router {
    HttpResponse handle(String rawRequest) throws Exception;
}

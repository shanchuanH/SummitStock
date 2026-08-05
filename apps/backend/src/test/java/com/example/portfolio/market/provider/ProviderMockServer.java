package com.example.portfolio.market.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Function;

final class ProviderMockServer implements AutoCloseable {
    private final HttpServer server;

    ProviderMockServer(Function<HttpExchange, Response> responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, responder.apply(exchange)));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, Response response) throws IOException {
        var bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    record Response(int status, String body) {}
}

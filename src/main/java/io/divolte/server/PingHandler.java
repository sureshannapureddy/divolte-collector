package io.divolte.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import javax.annotation.ParametersAreNonnullByDefault;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler for ping requests.
 *
 * Ping requests are immediately and always responded to with a "pong" text response.
 */
@ParametersAreNonnullByDefault
final class PingHandler {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private PingHandler() {
        // Prevent external instantiation.
    }

    public static void handlePingRequest(final HttpServerExchange exchange) throws Exception {
        logger.debug("Ping received from {}", exchange.getDestinationAddress().getHostString());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
        exchange.getResponseSender().send("pong", StandardCharsets.UTF_8);
    }
}

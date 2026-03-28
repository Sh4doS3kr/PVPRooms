package com.pvprooms.util;

import com.pvprooms.PvPRoomsPro;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Detects the server's geographic region asynchronously using ip-api.com.
 * No API key required for non-commercial use.
 *
 * Endpoint: http://ip-api.com/json?fields=continentCode
 * Sample response: {"continentCode":"EU"}
 *
 * Continent → region code mapping:
 *   AF → af   AN → an   AS → as
 *   EU → eu   NA → na   OC → oc   SA → sa
 */
public class RegionDetector {

    private static final String API_URL =
            "http://ip-api.com/json?fields=continentCode";

    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private RegionDetector() {}

    /**
     * Fires an async HTTP request to ip-api.com.
     * Calls {@code onResult} on the main thread with the detected region code,
     * or the {@code fallback} string if detection fails.
     */
    public static void detectAsync(PvPRoomsPro plugin, String fallback, Consumer<String> onResult) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String region = fallback;
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .timeout(TIMEOUT)
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                region = parseContinentCode(response.body(), fallback);
                plugin.getLogger().info("[PvPRooms] Región detectada: " + region.toUpperCase());

            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[PvPRooms] No se pudo detectar la región del servidor, usando fallback '"
                                + fallback + "': " + ex.getMessage());
            }

            final String result = region;
            // Return to main thread for safe state mutation
            plugin.getServer().getScheduler().runTask(plugin, () -> onResult.accept(result));
        });
    }

    // ── Parser ────────────────────────────────────────────────────────────

    /**
     * Extracts the continentCode value from a minimal JSON string.
     * E.g. {"continentCode":"EU"} → "eu"
     */
    static String parseContinentCode(String json, String fallback) {
        if (json == null) return fallback;
        int idx = json.indexOf("\"continentCode\"");
        if (idx < 0) return fallback;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return fallback;
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return fallback;
        String code = json.substring(q1 + 1, q2).trim();
        return code.isEmpty() ? fallback : code.toLowerCase();
    }
}

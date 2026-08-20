package com.jcm.recommendations.soccer.web.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Same-origin proxy for remote league/team logos so shortlist image export
 * can embed them via html2canvas (external CDNs often lack CORS headers).
 */
@RestController
@RequestMapping("/api/media")
public class MediaProxyController {

    private static final int MAX_BYTES = 512 * 1024;
    private static final Set<String> ALLOWED_HOST_SUFFIXES = Set.of(
            "footystats.org",
            "football-data-api.com",
            "cloudfront.net",
            "amazonaws.com"
    );

    private final RestClient restClient = RestClient.create();

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!isAllowedHost(uri.getHost())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            ResponseEntity<byte[]> upstream = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = upstream.getBody();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            MediaType contentType = upstream.getHeaders().getContentType();
            if (contentType == null || !contentType.getType().equals("image")) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(body);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static boolean isAllowedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String suffix : ALLOWED_HOST_SUFFIXES) {
            if (normalized.equals(suffix) || normalized.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }
}

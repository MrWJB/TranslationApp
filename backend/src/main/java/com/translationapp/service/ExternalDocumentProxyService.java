package com.translationapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Set;

/**
 * Proxies allowed external documentation pages for in-app iframe preview.
 */
@Slf4j
@Service
public class ExternalDocumentProxyService {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "docs.spring.io",
            "github.com"
    );

    private final RestTemplate restTemplate;

    public ExternalDocumentProxyService(
            @Value("${crawler.nav-fetch-timeout-ms:30000}") long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public String fetchProxiedHtml(String url) {
        URI uri = validateUrl(url);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; TranslationApp/1.0)");
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");

        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        String html = response.getBody();
        if (html == null || html.isBlank()) {
            throw new IllegalStateException("Empty response from " + uri);
        }

        html = sanitizeExternalHtml(html);
        return injectBaseTag(html, baseHref(uri));
    }

    /** Remove cookie/consent scripts that break iframe preview. */
    private String sanitizeExternalHtml(String html) {
        return html
                .replaceAll("(?is)<script[^>]*cookielaw[^>]*>.*?</script>", "")
                .replaceAll("(?is)<script[^>]*googletagmanager[^>]*>.*?</script>", "")
                .replaceAll("(?is)<script[^>]*>.*?OptanonWrapper.*?</script>", "")
                .replaceAll("(?is)<script[^>]*>.*?setGTM\\(.*?\\).*?</script>", "")
                .replaceAll("(?is)<noscript>.*?googletagmanager.*?</noscript>", "");
    }

    private URI validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || ALLOWED_HOSTS.stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
            throw new IllegalArgumentException("Host not allowed: " + host);
        }
        return uri;
    }

    private String baseHref(URI uri) {
        String path = uri.getPath() != null ? uri.getPath() : "/";
        if (!path.endsWith("/")) {
            int slash = path.lastIndexOf('/');
            path = slash >= 0 ? path.substring(0, slash + 1) : "/";
        }
        return uri.getScheme() + "://" + uri.getHost() + path;
    }

    private String injectBaseTag(String html, String baseHref) {
        String baseTag = "<base href=\"" + baseHref + "\">";
        if (html.contains("<head")) {
            return html.replaceFirst("(?i)<head(\\s[^>]*)?>", "<head$1>" + baseTag);
        }
        return baseTag + html;
    }
}

package com.translationapp.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofMinutes(60))
                .errorHandler(crawlerErrorHandler())
                .build();
    }

    /** Short timeouts for crawl progress/status polling so busy crawls do not block the poller. */
    @Bean(name = "crawlerProgressRestTemplate")
    public RestTemplate crawlerProgressRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .errorHandler(crawlerErrorHandler())
                .build();
    }

    private static ResponseErrorHandler crawlerErrorHandler() {
        return new ResponseErrorHandler() {
                    @Override
                    public boolean hasError(ClientHttpResponse response) throws IOException {
                        // Don't throw exceptions for error status codes
                        // We want to handle errors ourselves by parsing the response body
                        return false;
                    }

                    @Override
                    public void handleError(ClientHttpResponse response) throws IOException {
                        // No handling needed - we handle errors in the service layer
                    }
                };
    }
}

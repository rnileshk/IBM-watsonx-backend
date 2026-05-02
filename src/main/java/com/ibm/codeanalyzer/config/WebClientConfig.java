package com.ibm.codeanalyzer.config;

import com.ibm.codeanalyzer.model.exception.LLMException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Provides pre-configured WebClient instances for each LLM provider.
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${llm.timeout-seconds:60}")
    private int timeoutSeconds;

    @Bean("watsonxClient")
    public WebClient watsonxClient(
            @Value("${llm.watsonx.api-url}") String baseUrl,
            @Value("${llm.watsonx.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .defaultStatusHandler(
                    HttpStatusCode::isError,
                    response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("watsonx API error {}: {}", response.statusCode(), body);
                            return Mono.error(new LLMException(
                                "watsonx",
                                "API error: " + response.statusCode() + " - " + body,
                                response.statusCode().value()
                            ));
                        })
                )
                .build();
    }

    @Bean("openaiClient")
    public WebClient openaiClient(
            @Value("${llm.openai.api-url}") String baseUrl,
            @Value("${llm.openai.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .defaultStatusHandler(
                    HttpStatusCode::isError,
                    response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("OpenAI API error {}: {}", response.statusCode(), body);
                            return Mono.error(new LLMException(
                                "openai",
                                "API error: " + response.statusCode() + " - " + body,
                                response.statusCode().value()
                            ));
                        })
                )
                .build();
    }

    @Bean("anthropicClient")
    public WebClient anthropicClient(
            @Value("${llm.anthropic.api-url}") String baseUrl,
            @Value("${llm.anthropic.api-key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .defaultStatusHandler(
                    HttpStatusCode::isError,
                    response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Anthropic API error {}: {}", response.statusCode(), body);
                            return Mono.error(new LLMException(
                                "anthropic",
                                "API error: " + response.statusCode() + " - " + body,
                                response.statusCode().value()
                            ));
                        })
                )
                .build();
    }
}

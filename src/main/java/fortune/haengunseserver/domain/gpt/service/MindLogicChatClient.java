package fortune.haengunseserver.domain.gpt.service;

import fortune.haengunseserver.domain.gpt.dto.MindLogicChatRequest;
import fortune.haengunseserver.domain.gpt.dto.MindLogicChatResponse;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class MindLogicChatClient {

    @Value("${mindlogic.base-url}")
    private String baseUrl;

    @Value("${mindlogic.api-key}")
    private String apiKey;

    @Value("${mindlogic.model}")
    private String model;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    public Mono<String> call(String content, Integer maxTokens, Double temperature) {
        MindLogicChatRequest request = new MindLogicChatRequest(
                model,
                List.of(new MindLogicChatRequest.Message("user", content)),
                maxTokens,
                temperature
        );

        return webClient.post()
                .uri("/v1/api/openai/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("MindLogic API 오류 - status: {}, body: {}", response.statusCode(), body);
                                    if (body != null && body.contains("rate_limit")) {
                                        return Mono.error(new CustomException(ErrorCode.GPT_OVERLOADED));
                                    }
                                    return Mono.error(new CustomException(ErrorCode.GPT_ERROR));
                                }))
                .bodyToMono(MindLogicChatResponse.class)
                .timeout(Duration.ofSeconds(30))
                .doOnNext(response -> {
                    if (response.usage() != null) {
                        log.info("GPT 토큰 사용량 - Prompt: {}, Completion: {}, Total: {}",
                                response.usage().promptTokens(),
                                response.usage().completionTokens(),
                                response.usage().totalTokens());
                    }
                })
                .map(response -> {
                    if (response.choices() != null && !response.choices().isEmpty()
                            && response.choices().get(0).message() != null) {
                        return response.choices().get(0).message().content();
                    }
                    return "";
                });
    }
}

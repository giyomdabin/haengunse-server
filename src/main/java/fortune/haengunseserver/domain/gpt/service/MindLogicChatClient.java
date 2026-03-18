package fortune.haengunseserver.domain.gpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                temperature
        );

        return webClient.post()
                .uri("/v1/api/openai/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60))
                .flatMap(rawBody -> {
                    log.debug("MindLogic API 원본 응답: {}", rawBody);

                    if (rawBody == null || rawBody.isBlank()) {
                        log.error("MindLogic API 빈 응답 수신");
                        return Mono.error(new CustomException(ErrorCode.GPT_ERROR));
                    }

                    // 에러 응답 체크
                    if (rawBody.contains("\"detail\"") || rawBody.contains("\"error\"")) {
                        log.error("MindLogic API 오류 응답: {}", rawBody);
                        if (rawBody.contains("rate_limit")) {
                            return Mono.error(new CustomException(ErrorCode.GPT_OVERLOADED));
                        }
                        return Mono.error(new CustomException(ErrorCode.GPT_ERROR));
                    }

                    try {
                        MindLogicChatResponse response = objectMapper.readValue(rawBody, MindLogicChatResponse.class);

                        if (response.usage() != null) {
                            log.info("GPT 토큰 사용량 - Prompt: {}, Completion: {}, Total: {}",
                                    response.usage().promptTokens(),
                                    response.usage().completionTokens(),
                                    response.usage().totalTokens());
                        }

                        if (response.choices() != null && !response.choices().isEmpty()
                                && response.choices().get(0).message() != null
                                && response.choices().get(0).message().content() != null
                                && !response.choices().get(0).message().content().isBlank()) {
                            return Mono.just(response.choices().get(0).message().content());
                        }

                        log.error("MindLogic API choices 비어있음: {}", rawBody);
                        return Mono.error(new CustomException(ErrorCode.GPT_ERROR));
                    } catch (Exception e) {
                        log.error("MindLogic API 응답 파싱 실패: {}", rawBody, e);
                        return Mono.error(new CustomException(ErrorCode.GPT_ERROR));
                    }
                });
    }
}

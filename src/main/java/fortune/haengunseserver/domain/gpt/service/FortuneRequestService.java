package fortune.haengunseserver.domain.gpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public abstract class FortuneRequestService<T, R> implements FortuneService<T, R> {

    protected final MindLogicChatClient chatClient;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<R> getFortune(T input) {
        String content = generatePrompt(input);

        return callApi(content)
                .map(this::parseResponse)
                .onErrorMap(e -> {
                    if (e instanceof CustomException) {
                        return e;
                    }
                    return mapToGptException(e);
                });
    }

    protected Mono<String> callApi(String content) {
        return chatClient.call(content, null, null)
                .map(this::cleanJsonResponse);
    }

    protected Mono<String> callApi(String content, Integer maxTokens, Double temperature) {
        return chatClient.call(content, maxTokens, temperature)
                .map(this::cleanJsonResponse);
    }

    /**
     * JSON 응답에서 마크다운 코드 블록 제거
     */
    protected String cleanJsonResponse(String raw) {
        return raw.replace("```json", "").replace("```", "").trim();
    }

    /**
     * JSON 문자열을 응답 객체로 파싱
     */
    protected abstract R parseResponse(String jsonText);

    /**
     * 예외를 GPT 관련 CustomException으로 변환
     */
    protected CustomException mapToGptException(Throwable e) {
        String message = e.getMessage();
        if (message != null && message.contains("rate_limit")) {
            return new CustomException(ErrorCode.GPT_OVERLOADED);
        }
        log.error("GPT 호출 오류: {}", message, e);
        return new CustomException(ErrorCode.GPT_ERROR);
    }
}

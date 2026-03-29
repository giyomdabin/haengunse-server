package fortune.haengunseserver.domain.gpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
@Slf4j
public abstract class FortuneRequestService<T, R> implements FortuneService<T, R> {

    protected final OpenAiChatModel chatModel;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<R> getFortune(T input) {
        Prompt prompt = generatePrompt(input);

        return streamGptResponse(prompt)
                .map(this::parseResponse)
                .onErrorMap(e -> {
                    if (e instanceof CustomException) {
                        return e;
                    }
                    return mapToGptException(e);
                });
    }

    /**
     * GPT 스트리밍 호출 후 전체 응답을 문자열로 합침
     */
    protected Mono<String> streamGptResponse(Prompt prompt) {
        return Flux.from(chatModel.stream(prompt))
                .map(response -> {
                    if (response.getResult() != null &&
                        response.getResult().getOutput() != null &&
                        response.getResult().getOutput().getText() != null) {
                        return response.getResult().getOutput().getText();
                    }
                    return "";
                })
                .reduce("", String::concat)
                .map(this::cleanJsonResponse)
                .timeout(Duration.ofSeconds(60));
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
        if (e instanceof WebClientResponseException wce) {
            log.error("OpenAI API 오류 - Status: {}, Body: {}", wce.getStatusCode(), wce.getResponseBodyAsString());
        }
        if (message != null && message.contains("rate_limit")) {
            return new CustomException(ErrorCode.GPT_OVERLOADED);
        }
        log.error("GPT 호출 오류: {}", message, e);
        return new CustomException(ErrorCode.GPT_ERROR);
    }
}

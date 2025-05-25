package fortune.haengunseserver.domain.gpt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

@RequiredArgsConstructor
@Slf4j
public abstract class FortuneRequestService<T, R> implements FortuneService<T, R> {

    protected final OpenAiChatModel chatModel; // Spring AI GPT 모델

    @Override
    public R getFortune(T input) {
        Prompt prompt = generatePrompt(input);

        try {
            ChatResponse response = chatModel.call(prompt);
            return processResponse(response);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw mapToGptException(e);
        }
    }

    // GPT API 응답을 가공하여 반환합니다.
    protected abstract R processResponse(ChatResponse response);

    protected CustomException mapToGptException(Exception e) {
        String errorType = getErrorType(e.getMessage());

        ErrorCode errorCode = switch (errorType) {
            case "rate_limit_error" -> ErrorCode.GPT_OVERLOADED;
            default                 -> ErrorCode.GPT_ERROR;
        };

        return new CustomException(errorCode);
    }

    protected String getErrorType(String responseBody) {
        try {
            // 메시지 앞에 붙은 HTTP 상태 코드 제거
            int braceIndex = responseBody.indexOf("{");
            String jsonPart = braceIndex >= 0 ? responseBody.substring(braceIndex) : responseBody;

            return new ObjectMapper()
                    .readTree(jsonPart)
                    .path("error")
                    .path("type")
                    .asText("unknown");

        } catch (Exception e) {
            return "unknown";
        }
    }
}


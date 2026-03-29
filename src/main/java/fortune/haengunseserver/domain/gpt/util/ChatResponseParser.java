package fortune.haengunseserver.domain.gpt.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;

@Slf4j
public class ChatResponseParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T parse(ChatResponse response, Class<T> className) {
        String raw = response.getResult().getOutput().getText();
        String cleaned = raw.replace("```json", "").replace("```", "").trim();

        try {
            return mapper.readValue(cleaned, className);
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: {}", cleaned, e);
            throw new CustomException(ErrorCode.PARSE_ERROR);
        }
    }

    public static <T> T parse(ChatResponse response, TypeReference<T> typeRef) {
        String raw = response.getResult().getOutput().getText();
        String cleaned = raw.replace("```json", "").replace("```", "").trim();

        try {
            return mapper.readValue(cleaned, typeRef);
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: {}", cleaned, e);
            throw new CustomException(ErrorCode.PARSE_ERROR);
        }
    }
}


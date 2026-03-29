package fortune.haengunseserver.domain.fortune.today.service.dream;

import fortune.haengunseserver.domain.fortune.today.dto.response.dream.DreamResponse;
import fortune.haengunseserver.domain.gpt.service.FortuneRequestService;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class DreamService extends FortuneRequestService<List<String>, DreamResponse> {

    public DreamService(OpenAiChatModel chatModel) {
        super(chatModel);
    }

    @Override
    public Mono<DreamResponse> getFortune(List<String> questions) {
        // 입력 검증
        if (questions.size() > 3) {
            return Mono.error(new CustomException(ErrorCode.TOO_MANY_QUESTIONS));
        }
        return super.getFortune(questions);
    }

    @Override
    public Prompt generatePrompt(List<String> questions) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("다음을 보고 꿈을 해석해주세요:\n\n");

        for (int i = 0; i < questions.size(); i++) {
            if (i == 0) {
                contentBuilder.append("기존 꿈:\n");
            } else {
                contentBuilder.append("추가 꿈:\n");
            }
            contentBuilder.append(questions.get(i)).append("\n\n");
        }

        contentBuilder.append("""
        [답변 규칙]
        - 존댓말로 답변할 것
        - 종합해석만 대답할 것 (너무 길지 않게 부탁해요)
        - 모두 하나의 꿈으로 생각하되, 추가 꿈에 대한 답변을 해주세요.
        - "interpretation" : ".." 의 json 형식으로 답해주세요.
        """);

        return new Prompt(contentBuilder.toString());
    }

    @Override
    protected DreamResponse parseResponse(String jsonText) {
        try {
            return objectMapper.readValue(jsonText, DreamResponse.class);
        } catch (Exception e) {
            log.error("JSON 파싱 실패: {}", jsonText, e);
            throw new CustomException(ErrorCode.PARSE_ERROR);
        }
    }
}

package fortune.haengunseserver.domain.fortune.today.service.todaysaju;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.common.util.GenderConverter;
import fortune.haengunseserver.domain.fortune.common.util.ManseInfoFormatter;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.TodaySajuResponse;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 오늘의 운세 서비스 클래스
 * GPT 1회 호출로 세부운 5개 + 총운을 통합 조회
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TodaySajuService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 오늘의 사주 운세 조회
     * GPT 1회 호출로 세부운 5개 + 총운을 통합 생성
     */
    public Mono<TodaySajuResponse> getFortune(SajuRequest request) {
        String manseInfo = ManseInfoFormatter.format(request.getManseInfo());
        String gender = GenderConverter.toKorean(request.getGender());
        String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        log.info("오늘의 운세 조회 시작 - 만세력: {}", manseInfo);

        return callGpt(buildUnifiedPrompt(manseInfo, gender, todayDate))
                .map(json -> parseJson(json, TodaySajuResponse.class))
                .elapsed()
                .doOnNext(tuple -> log.info("오늘의 운세 전체 조회 완료 - 소요시간: {}ms", tuple.getT1()))
                .map(tuple -> tuple.getT2())
                .onErrorMap(e -> {
                    if (e instanceof CustomException) {
                        return e;
                    }
                    if (e instanceof WebClientResponseException wce) {
                        log.error("OpenAI API 오류 - Status: {}, Body: {}", wce.getStatusCode(), wce.getResponseBodyAsString());
                    }
                    log.error("오늘의 운세 조회 오류: {}", e.getMessage(), e);
                    return new CustomException(ErrorCode.GPT_ERROR);
                });
    }

    /**
     * GPT 스트리밍 호출
     */
    private Mono<String> callGpt(Prompt prompt) {
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
     * 통합 프롬프트 생성 (세부운 5개 + 총운)
     */
    private Prompt buildUnifiedPrompt(String manseInfo, String gender, String todayDate) {
        String content = String.format("""
            사주 전문 역술가로서 오늘의 운세를 존댓말로 분석해 주세요. 해석할 때 영어 사용은 하지 않습니다.

            성별: %s / 만세력: %s / 오늘: %s

            * 일간과 오늘의 일진(천간·지지) 관계를 중심으로 분석하되, 오행의 생극 관계, 천간 합충, 지지 합충형파해를 고려해 주세요.
            * 각 세부운에서 사주 구성 요소를 언급하며 구체적으로 설명해 주세요.
            * 전문적인 분석을 하되, 사주를 모르는 사람도 쉽게 이해할 수 있도록 풀어서 설명해 주세요.
            * 각 세부운 설명 마지막에 오늘 실천할 수 있는 구체적인 조언을 포함해 주세요.

            다음 항목을 모두 작성해 주세요:
            1. 세부운 5가지(재물운/연애운/건강운/학업운/직장운): 각각 점수(5점 만점, 0.5 단위)와 설명(5문장 이내)
            2. 총운: totalScore(100점 만점), generalFortune(3문장 이내), dailyMessage(25자 이내)

            JSON만 출력:
            {"totalScore": , "generalFortune": "", "wealthFortune": {"score": , "description": ""}, "loveFortune": {"score": , "description": ""}, "healthFortune": {"score": , "description": ""}, "studyFortune": {"score": , "description": ""}, "careerFortune": {"score": , "description": ""}, "dailyMessage": ""}
            """, gender, manseInfo, todayDate);

        return new Prompt(content);
    }

    /**
     * JSON 응답 정리
     */
    private String cleanJsonResponse(String raw) {
        return raw.replace("```json", "").replace("```", "").trim();
    }

    /**
     * JSON 파싱
     */
    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("JSON 파싱 실패: {}", json, e);
            throw new CustomException(ErrorCode.PARSE_ERROR);
        }
    }
}

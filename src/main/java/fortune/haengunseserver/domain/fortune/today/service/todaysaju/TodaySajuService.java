package fortune.haengunseserver.domain.fortune.today.service.todaysaju;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.common.util.GenderConverter;
import fortune.haengunseserver.domain.fortune.common.util.ManseInfoFormatter;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.DetailFortuneResult;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.GeneralFortuneResult;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.TodaySajuResponse;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 오늘의 운세 서비스 클래스
 * 세부운 먼저 조회 → 세부운 결과를 기반으로 총운 생성 (일관성 보장)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TodaySajuService {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 오늘의 사주 운세 조회
     * 1단계: 세부운(재물/연애/건강/학업/직장) GPT 호출
     * 2단계: 세부운 결과를 기반으로 총운 GPT 호출
     */
    public Mono<TodaySajuResponse> getFortune(SajuRequest request) {
        String manseInfo = ManseInfoFormatter.format(request.getManseInfo());
        String gender = GenderConverter.toKorean(request.getGender());
        String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        // 1단계: 세부운 먼저 조회
        return callGpt(buildDetailPrompt(manseInfo, gender, todayDate))
                .map(json -> parseJson(json, DetailFortuneResult.class))
                // 2단계: 세부운 결과로 총운 생성
                .flatMap(detailResult ->
                    callGpt(buildGeneralPrompt(manseInfo, gender, todayDate, detailResult))
                        .map(json -> parseJson(json, GeneralFortuneResult.class))
                        .map(generalResult -> combineResults(generalResult, detailResult))
                )
                .onErrorMap(e -> {
                    if (e instanceof CustomException) {
                        return e;
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
                .map(this::cleanJsonResponse);
    }

    /**
     * 세부운 프롬프트 생성
     */
    private Prompt buildDetailPrompt(String manseInfo, String gender, String todayDate) {
        String content = String.format("""
            당신은 엄격하고 정확한 사주명리학자입니다.
            다음 사주 정보와 오늘의 일진을 분석하여 세부 운세를 판단해주세요.

            [사주 정보]
            성별: %s
            만세력:
            %s
            오늘 날짜: %s

            [채점 기준 - 반드시 아래 기준에 따라 점수를 부여할 것]

            ★ 점수별 기준 (5점 만점):
            - 5.0점: 천을귀인, 삼합, 육합 등 대길한 조합이 있을 때
            - 4.0~4.5점: 생(生)의 관계, 용신이 힘을 받을 때
            - 3.0~3.5점: 특별한 길흉이 없는 평범한 날
            - 2.0~2.5점: 극(剋)의 관계, 형(刑)이 있을 때
            - 1.0~1.5점: 충(沖), 파(破), 해(害)가 있을 때
            - 0.5~1.0점: 복합적인 흉살이 겹칠 때

            ★ 운세별 주요 판단 요소:
            - 재물운: 재성(편재/정재)과 일진의 관계, 식상생재 여부
            - 연애운: 관성(편관/정관), 인성과의 관계, 도화살/홍염살 여부
            - 건강운: 일간의 강약, 비겁 및 인성의 힘, 병부(病符) 여부
            - 학업운: 인성(편인/정인)의 상태, 문창귀인 여부
            - 직장운: 관성의 상태, 역마살, 천을귀인 여부

            [작성 규칙]
            - 각 운세별로 3~4줄로 작성
            - 존댓말로 답변
            - 사주 용어(충, 합, 형, 생, 극 등)를 사용하되 쉽게 풀어서 설명
            - 점수가 낮으면 주의사항과 조언을 구체적으로 제시
            - 모든 운세가 좋을 수 없음. 사주에 따라 냉정하게 평가할 것

            응답은 아래 JSON 형식으로 출력해주세요 (설명 없이 JSON만 출력)
            {
                "wealthFortune": {"score": , "description": "..."},
                "loveFortune": {"score": , "description": "..."},
                "healthFortune": {"score": , "description": "..."},
                "studyFortune": {"score": , "description": "..."},
                "careerFortune": {"score": , "description": "..."}
            }
            """, gender, manseInfo, todayDate);

        OpenAiChatOptions chatOptions = new OpenAiChatOptions();
        chatOptions.setMaxTokens(800);
        chatOptions.setTemperature(0.8);

        return new Prompt(content, chatOptions);
    }

    /**
     * 총운 프롬프트 생성 (세부운 결과 포함)
     */
    private Prompt buildGeneralPrompt(String manseInfo, String gender, String todayDate, DetailFortuneResult detail) {
        // 세부운 평균 점수 계산 (100점 만점 환산)
        double avgScore = (detail.getWealthFortune().getScore() +
                          detail.getLoveFortune().getScore() +
                          detail.getHealthFortune().getScore() +
                          detail.getStudyFortune().getScore() +
                          detail.getCareerFortune().getScore()) / 5.0 * 20.0;

        String detailSummary = String.format("""
            - 재물운: %.1f점 - %s
            - 연애운: %.1f점 - %s
            - 건강운: %.1f점 - %s
            - 학업운: %.1f점 - %s
            - 직장운: %.1f점 - %s
            (세부운 평균: %.0f점/100점)
            """,
            detail.getWealthFortune().getScore(), truncate(detail.getWealthFortune().getDescription(), 50),
            detail.getLoveFortune().getScore(), truncate(detail.getLoveFortune().getDescription(), 50),
            detail.getHealthFortune().getScore(), truncate(detail.getHealthFortune().getDescription(), 50),
            detail.getStudyFortune().getScore(), truncate(detail.getStudyFortune().getDescription(), 50),
            detail.getCareerFortune().getScore(), truncate(detail.getCareerFortune().getDescription(), 50),
            avgScore
        );

        String content = String.format("""
            당신은 엄격하고 정확한 사주명리학자입니다.
            세부 운세 결과를 종합하여 오늘의 총운을 판단해주세요.

            [사주 정보]
            성별: %s
            만세력:
            %s
            오늘 날짜: %s

            [세부 운세 결과]
            %s

            [총점 채점 기준]
            - 총점은 세부 운세 평균을 기반으로 하되, 사주와 일진의 전체적인 조화를 고려
            - 충(沖)이나 형(刑)이 여러 곳에서 발생하면 감점
            - 천을귀인, 삼합 등 길신이 있으면 가점
            - 점수 분포: 20~30점(매우 나쁨), 40~50점(나쁨), 50~60점(보통), 70~80점(좋음), 85점 이상(매우 좋음)

            [작성 규칙]
            - 4줄 이상의 문장으로 작성
            - 세부 운세 중 특히 주의할 부분과 활용할 부분을 언급
            - 존댓말로 답변
            - 낮은 점수의 운세가 있다면 반드시 주의사항 포함
            - dailyMessage는 오늘의 핵심 조언 (25자 이내)

            응답은 아래 JSON 형식으로 출력해주세요 (설명 없이 JSON만 출력)
            {
                "totalScore": ,
                "generalFortune": "...",
                "dailyMessage": "..."
            }
            """, gender, manseInfo, todayDate, detailSummary);

        OpenAiChatOptions chatOptions = new OpenAiChatOptions();
        chatOptions.setMaxTokens(450);
        chatOptions.setTemperature(0.8);

        return new Prompt(content, chatOptions);
    }

    /**
     * 문자열 자르기 (프롬프트 길이 제한용)
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * 두 결과를 합쳐서 최종 응답 생성
     */
    private TodaySajuResponse combineResults(GeneralFortuneResult general, DetailFortuneResult detail) {
        return new TodaySajuResponse(
                general.getTotalScore(),
                general.getGeneralFortune(),
                detail.getWealthFortune(),
                detail.getLoveFortune(),
                detail.getHealthFortune(),
                detail.getStudyFortune(),
                detail.getCareerFortune(),
                general.getDailyMessage()
        );
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

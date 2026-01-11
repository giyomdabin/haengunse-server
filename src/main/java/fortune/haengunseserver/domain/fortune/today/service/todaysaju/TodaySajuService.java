package fortune.haengunseserver.domain.fortune.today.service.todaysaju;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.common.util.GenderConverter;
import fortune.haengunseserver.domain.fortune.common.util.ManseInfoFormatter;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.DetailFortuneResult;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.FortuneDetail;
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
     * 1단계: 5개 세부운(재물/연애/건강/학업/직장)을 병렬로 GPT 호출
     * 2단계: 세부운 결과를 기반으로 총운 GPT 호출
     */
    public Mono<TodaySajuResponse> getFortune(SajuRequest request) {
        String manseInfo = ManseInfoFormatter.format(request.getManseInfo());
        String gender = GenderConverter.toKorean(request.getGender());
        String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        // 1단계: 5개 세부운을 병렬로 호출
        Mono<FortuneDetail> wealthMono = getWealthFortune(manseInfo, gender, todayDate);
        Mono<FortuneDetail> loveMono = getLoveFortune(manseInfo, gender, todayDate);
        Mono<FortuneDetail> healthMono = getHealthFortune(manseInfo, gender, todayDate);
        Mono<FortuneDetail> studyMono = getStudyFortune(manseInfo, gender, todayDate);
        Mono<FortuneDetail> careerMono = getCareerFortune(manseInfo, gender, todayDate);

        // 5개 병렬 호출 후 결과를 합침
        return Mono.zip(wealthMono, loveMono, healthMono, studyMono, careerMono)
                .flatMap(tuple -> {
                    DetailFortuneResult detailResult = new DetailFortuneResult(
                            tuple.getT1(), // 재물운
                            tuple.getT2(), // 연애운
                            tuple.getT3(), // 건강운
                            tuple.getT4(), // 학업운
                            tuple.getT5()  // 직장운
                    );

                    // 2단계: 세부운 결과로 총운 생성
                    return callGpt(buildGeneralPrompt(manseInfo, gender, todayDate, detailResult))
                            .map(json -> parseJson(json, GeneralFortuneResult.class))
                            .map(generalResult -> combineResults(generalResult, detailResult));
                })
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
     * 재물운 조회
     */
    private Mono<FortuneDetail> getWealthFortune(String manseInfo, String gender, String todayDate) {
        return callGpt(buildFortunePrompt("재물운", manseInfo, gender, todayDate))
                .map(json -> parseFortuneDetail(json));
    }

    /**
     * 연애운 조회
     */
    private Mono<FortuneDetail> getLoveFortune(String manseInfo, String gender, String todayDate) {
        return callGpt(buildFortunePrompt("연애운", manseInfo, gender, todayDate))
                .map(json -> parseFortuneDetail(json));
    }

    /**
     * 건강운 조회
     */
    private Mono<FortuneDetail> getHealthFortune(String manseInfo, String gender, String todayDate) {
        return callGpt(buildFortunePrompt("건강운", manseInfo, gender, todayDate))
                .map(json -> parseFortuneDetail(json));
    }

    /**
     * 학업운 조회
     */
    private Mono<FortuneDetail> getStudyFortune(String manseInfo, String gender, String todayDate) {
        return callGpt(buildFortunePrompt("학업운", manseInfo, gender, todayDate))
                .map(json -> parseFortuneDetail(json));
    }

    /**
     * 직장운 조회
     */
    private Mono<FortuneDetail> getCareerFortune(String manseInfo, String gender, String todayDate) {
        return callGpt(buildFortunePrompt("직장운", manseInfo, gender, todayDate))
                .map(json -> parseFortuneDetail(json));
    }

    /**
     * 개별 운세 프롬프트 생성 (재물/연애/건강/학업/직장)
     */
    private Prompt buildFortunePrompt(String fortuneType, String manseInfo, String gender, String todayDate) {
        String content = String.format("""
            당신은 20년 경력의 사주 카페를 운영하는 전문 역술가입니다.
            손님에게 오늘의 %s을 친절하고 이해하기 쉽게 설명해 주세요.
            말투는 존댓말을 사용하고, 사주 카페에서 직접 말해주는 느낌으로 작성하세요.

            [사주 정보]
            - 성별: %s
            - 만세력: %s
            - 오늘 날짜: %s

            [점수 기준 / 5점 만점]
            - 5.0: 천을귀인·삼합·육합 등 매우 좋은 조합
            - 4.0~4.5: 기운이 도와주는 날(생, 용신 도움)
            - 3.0~3.5: 무난한 흐름
            - 2.0~2.5: 마찰·부담 주의(극, 형)
            - 1.0~1.5: 충돌·어긋남(충, 파, 해)
            - 0.5~1.0: 전반적으로 조심이 필요한 날

            [작성 규칙]
            - %s에 대해서만 작성
            - 점수 + 6문장의 설명
            - 첫 1~2줄: 오늘 운의 흐름 요약
            - 나머지: 실제 행동에 도움이 되는 조언
            - 점수가 낮으면 대처법, 높으면 활용법 제시

            [출력 형식 — JSON만]
            {
              "score": ,
              "description": ""
            }

            """, fortuneType, gender, manseInfo, todayDate, fortuneType);

        OpenAiChatOptions chatOptions = new OpenAiChatOptions();
        chatOptions.setMaxTokens(300);
        chatOptions.setTemperature(0.7);

        return new Prompt(content, chatOptions);
    }

    /**
     * 개별 운세 JSON 파싱
     */
    private FortuneDetail parseFortuneDetail(String json) {
        return parseJson(json, FortuneDetail.class);
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
            당신은 20년 경력의 사주 카페를 운영하는 전문 역술가입니다.
            손님에게 오늘의 총운을 친절하고 이해하기 쉽게 설명해 주세요.
            말투는 존댓말을 사용하고, 사주 카페에서 직접 말해주는 느낌으로 작성하세요.

            [사주 정보]
            - 성별: %s
            - 만세력: %s
            - 오늘 날짜: %s

            [세부 운세 결과]
            %s

            [총점 기준 / 100점 만점]
            - 85~100점: 천을귀인·삼합·육합 등 매우 좋은 조합
            - 70~84점: 기운이 도와주는 날(생, 용신 도움)
            - 50~69점: 무난한 흐름
            - 40~49점: 마찰·부담 주의(극, 형)
            - 20~39점: 충돌·어긋남(충, 파, 해)
            - 0~19점: 전반적으로 조심이 필요한 날

            [작성 규칙]
            - 총운은 6문장으로 작성
            - 첫 1~2줄: 오늘 전체적인 운의 흐름 요약
            - 나머지: 세부 운세를 종합하여 실제 행동에 도움이 되는 조언
            - 점수가 높은 운세는 어떻게 활용할지, 낮은 운세는 어떻게 대처할지 제시
            - dailyMessage는 오늘의 핵심 조언 (25자 이내)

            [출력 형식 — JSON만]
            {
              "totalScore": ,
              "generalFortune": "",
              "dailyMessage": ""
            }

            """, gender, manseInfo, todayDate, detailSummary);

        OpenAiChatOptions chatOptions = new OpenAiChatOptions();
        chatOptions.setMaxTokens(700);
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

package fortune.haengunseserver.domain.fortune.today.service.todaysaju;

import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.common.util.GenderConverter;
import fortune.haengunseserver.domain.fortune.common.util.ManseInfoFormatter;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.*;
import fortune.haengunseserver.domain.gpt.service.MindLogicChatClient;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private final MindLogicChatClient chatClient;
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

        log.info("오늘의 운세 조회 시작 - 만세력: {}", manseInfo);

        // 1단계: 5개 세부운을 병렬로 호출
        Mono<FortuneDetail> wealthMono = getDetailFortune("재물운", manseInfo, gender, todayDate);
        Mono<FortuneDetail> loveMono = getDetailFortune("연애운", manseInfo, gender, todayDate);
        Mono<FortuneDetail> healthMono = getDetailFortune("건강운", manseInfo, gender, todayDate);
        Mono<FortuneDetail> studyMono = getDetailFortune("학업운", manseInfo, gender, todayDate);
        Mono<FortuneDetail> careerMono = getDetailFortune("직장운", manseInfo, gender, todayDate);

        // 5개 병렬 호출 후 결과를 합침
        return Mono.zip(wealthMono, loveMono, healthMono, studyMono, careerMono)
                .elapsed()
                .doOnNext(tuple -> log.info("세부운 5개 병렬 조회 완료 - 소요시간: {}ms", tuple.getT1()))
                .map(tuple -> tuple.getT2())
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
                .elapsed()
                .doOnNext(tuple -> log.info("오늘의 운세 전체 조회 완료 - 총 소요시간: {}ms", tuple.getT1()))
                .map(tuple -> tuple.getT2())
                .onErrorMap(e -> {
                    if (e instanceof CustomException) {
                        return e;
                    }
                    log.error("오늘의 운세 조회 오류: {}", e.getMessage(), e);
                    return new CustomException(ErrorCode.GPT_ERROR);
                });
    }

    private Mono<String> callGpt(String content) {
        return chatClient.call(content, null, null)
                .map(this::cleanJsonResponse);
    }

    private Mono<FortuneDetail> getDetailFortune(String fortuneType, String manseInfo, String gender, String todayDate) {
        return callGpt(buildFortunePrompt(fortuneType, manseInfo, gender, todayDate))
                .map(json -> parseJson(json, FortuneDetail.class));
    }

    private String buildFortunePrompt(String fortuneType, String manseInfo, String gender, String todayDate) {
        return String.format("""
            사주 전문 역술가로서 오늘의 %s을 존댓말로 설명해 주세요.

            성별: %s / 만세력: %s / 오늘: %s

            - %s만 작성
            - 점수: 5점 만점 (0.5 단위)
            - description: 6문장, 300자 이내로 간결하게
            - 첫 1~2문장은 흐름 요약, 나머지는 실용적 조언

            JSON만 출력: {"score": , "description": ""}
            """, fortuneType, gender, manseInfo, todayDate, fortuneType);
    }

    private String buildGeneralPrompt(String manseInfo, String gender, String todayDate, DetailFortuneResult detail) {
        String detailSummary = String.format(
            "재물운: %.1f / 연애운: %.1f / 건강운: %.1f / 학업운: %.1f / 직장운: %.1f",
            detail.getWealthFortune().getScore(),
            detail.getLoveFortune().getScore(),
            detail.getHealthFortune().getScore(),
            detail.getStudyFortune().getScore(),
            detail.getCareerFortune().getScore()
        );

        return String.format("""
            사주 전문 역술가로서 오늘의 총운을 존댓말로 설명해 주세요.

            성별: %s / 만세력: %s / 오늘: %s
            세부운 점수(5점 만점): %s

            - totalScore: 100점 만점, 세부운 결과를 반영
            - generalFortune: 6문장, 300자 이내로 간결하게. 세부운을 종합한 실용적 조언
            - dailyMessage: 핵심 조언 25자 이내

            JSON만 출력: {"totalScore": , "generalFortune": "", "dailyMessage": ""}
            """, gender, manseInfo, todayDate, detailSummary);
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

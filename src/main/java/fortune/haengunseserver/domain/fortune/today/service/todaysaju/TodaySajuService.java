package fortune.haengunseserver.domain.fortune.today.service.todaysaju;

import fortune.haengunseserver.domain.fortune.common.dto.request.SajuRequest;
import fortune.haengunseserver.domain.fortune.common.util.GenderConverter;
import fortune.haengunseserver.domain.fortune.common.util.ManseInfoFormatter;
import fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune.TodaySajuResponse;
import fortune.haengunseserver.domain.gpt.service.FortuneRequestService;
import fortune.haengunseserver.domain.gpt.util.ChatResponseParser;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 오늘의 운세 서비스 클래스
 * FortuneService 인터페이스를 구현하여 운세 조회 기능 제공
 */
@Service
public class TodaySajuService extends FortuneRequestService<SajuRequest, TodaySajuResponse> {

    public TodaySajuService(OpenAiChatModel chatModel) {
        super(chatModel);
    }

    //  사용자의 생년월일을 만세력으로 변환하여 GPT에 요청할 프롬프트 생성
    @Override
    public Prompt generatePrompt(SajuRequest input) {
        String manseInfo = ManseInfoFormatter.format(input.getManseInfo());
        String gender = GenderConverter.toKorean(input.getGender());
        String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        String content = String.format("""
            
            당신은 유능한 사주명리학자입니다.
            다음 정보를 참고하여 오늘의 운세를 분석해주세요:
                            
            [사주 정보]
            성별: %s
            만세력: 
            %s
            오늘 날짜: %s

            [포함할 내용]
            - 총 점수 (0점 ~ 100점 사이, 해석에 따라 점수를 채점할 것: 항상 높은 점수를 부여할 필요는 없음)
            - 총운 해석 (하루의 전체적인 흐름)
            - 재물운, 연애운, 건강운, 학업운, 직장운

            [작성 규칙]
            - 4줄 이상의 문장으로 작성 (오늘 흐름, 주의할 점, 충/형/합 등 사주 기반 해석을 포함하는데 쉽게 설명할 것)
            - 존댓말로 답변할 것
            - 해석할 때는 부정적인 말을 포함해도 됨
            - 마지막에 25자 이하의 짧은 조언을 'dailyMessage' 필드로 포함

            응답은 아래 JSON 형식으로 출력해주세요 (설명 없이 JSON만 출력)
            {
                "totalScore": ,
                "generalFortune": "...",
                "wealthFortune": {"score": , "description": "..."},
                "loveFortune": {"score": , "description": "..."},
                "healthFortune": {"score": , "description": "..."},
                "studyFortune": {"score": , "description": "..."},
                "careerFortune": {"score": , "description": "..."},
                "dailyMessage": "..."
            }
            """, gender, manseInfo, todayDate);

        OpenAiChatOptions chatOptions = new OpenAiChatOptions();
        chatOptions.setMaxTokens(800);
        chatOptions.setTemperature(0.7);

        return new Prompt(content, chatOptions);
    }

    /**
     * GPT 응답을 처리하여 TodayLuckyResponse 객체로 변환
     */
    @Override
    protected TodaySajuResponse processResponse(ChatResponse response) {
        return ChatResponseParser.parse(response, TodaySajuResponse.class);
    }
}

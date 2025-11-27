package fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 세부운 GPT 응답용 내부 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailFortuneResult {
    private FortuneDetail wealthFortune;
    private FortuneDetail loveFortune;
    private FortuneDetail healthFortune;
    private FortuneDetail studyFortune;
    private FortuneDetail careerFortune;
}

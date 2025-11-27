package fortune.haengunseserver.domain.fortune.today.dto.response.sajufortune;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 총운 GPT 응답용 내부 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneralFortuneResult {
    private int totalScore;
    private String generalFortune;
    private String dailyMessage;
}

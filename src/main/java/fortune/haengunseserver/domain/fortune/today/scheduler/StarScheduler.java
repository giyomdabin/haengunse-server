package fortune.haengunseserver.domain.fortune.today.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.domain.fortune.today.dto.response.starfortune.StarResponseDto;
import fortune.haengunseserver.domain.fortune.today.service.starfortune.StarFortuneService;
import fortune.haengunseserver.domain.fortune.today.service.starfortune.StarFortuneStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class StarScheduler {

    private final StarFortuneService starFortuneService;
    private final StarFortuneStore starFortuneStore; // 내부 저장소
    private final ObjectMapper mapper;

    private List<List<StarResponseDto>> dummyVariants = null;

    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Seoul")
    public void updateStarFortune() {

        String tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .plusDays(1)
                .format(DateTimeFormatter.ISO_DATE);

        List<StarResponseDto> fortunes;

        try {
            fortunes = starFortuneService.getFortune(tomorrow);   // GPT 호출
        } catch (Exception e) {
            fortunes = randomDummy();                            // 실패 → 더미
            log.warn("더미 운세 적용: {}", fortunes);
        }

        starFortuneStore.update(fortunes);
    }

    private List<StarResponseDto> randomDummy() {
        if (dummyVariants == null) {
            try (InputStream in = new ClassPathResource("data/star_fortune/star_fortune_dummy.json").getInputStream()) {
                TypeReference<Map<String, List<List<StarResponseDto>>>> type = new TypeReference<>() {};
                Map<String, List<List<StarResponseDto>>> parsed = mapper.readValue(in, type);

                dummyVariants = parsed.get("variants");

            } catch (IOException e) {
                log.error("더미 로딩 실패", e);
                return List.of(); // fallback 처리
            }
        }

        int idx = ThreadLocalRandom.current().nextInt(dummyVariants.size());
        return dummyVariants.get(idx);
    }
}

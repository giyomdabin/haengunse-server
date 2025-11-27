package fortune.haengunseserver.domain.fortune.today.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fortune.haengunseserver.domain.fortune.today.dto.response.agefortune.AgeResponseDto;
import fortune.haengunseserver.domain.fortune.today.service.agefortune.AgeFortuneService;
import fortune.haengunseserver.domain.fortune.today.service.agefortune.AgeFortuneStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
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
public class AgeScheduler {

    private final AgeFortuneService ageFortuneService;
    private final AgeFortuneStore ageFortuneStore;
    private final ObjectMapper mapper;

    private List<List<AgeResponseDto>> dummyVariants = null;

    @Async
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Seoul")
    public void updateAgeFortune() {

        String tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .plusDays(1)
                .format(DateTimeFormatter.ISO_DATE);

        List<AgeResponseDto> fortunes;

        try {
            // 스케줄러는 백그라운드 작업이므로 block() 사용 허용
            fortunes = ageFortuneService.getFortune(tomorrow).block();
        } catch (Exception e) {
            fortunes = randomDummy();
            log.warn("더미 운세 적용: {}", fortunes);
        }

        ageFortuneStore.update(fortunes);
    }

    private List<AgeResponseDto> randomDummy() {
        if (dummyVariants == null) {
            try (InputStream in = new ClassPathResource("data/age_fortune/age_fortune_dummy.json").getInputStream()) {
                TypeReference<Map<String, List<List<AgeResponseDto>>>> type = new TypeReference<>() {};
                Map<String, List<List<AgeResponseDto>>> parsed = mapper.readValue(in, type);

                dummyVariants = parsed.get("variants");

            } catch (IOException e) {
                log.error("더미 로딩 실패", e);
                return List.of();
            }
        }

        int idx = ThreadLocalRandom.current().nextInt(dummyVariants.size());
        return dummyVariants.get(idx);
    }
}

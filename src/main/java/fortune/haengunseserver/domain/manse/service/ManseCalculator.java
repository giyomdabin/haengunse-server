package fortune.haengunseserver.domain.manse.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import fortune.haengunseserver.domain.manse.enums.HourBranch;
import fortune.haengunseserver.domain.manse.enums.Jiji;
import fortune.haengunseserver.domain.manse.enums.SolarTerm;
import fortune.haengunseserver.domain.manse.enums.Tenkan;
import fortune.haengunseserver.domain.manse.dto.request.ManseRequest;
import fortune.haengunseserver.domain.manse.dto.response.ManseResponse;
import fortune.haengunseserver.domain.calendar.service.CalendarService;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ManseCalculator {

    private final CalendarService calendarService;

    // 기준일: 1900년 1월 1일 (己亥년, 丁丑월, 甲戌일)
    private static final LocalDate BASE_DATE = LocalDate.of(1900, 1, 1);

    /**
     * 사주 계산 (리액티브)
     */
    public Mono<ManseResponse> calculateManse(ManseRequest input) {
        String birthDate = input.getBirthDate();
        boolean isSolar = input.isSolar();
        String birthTime = input.getBirthTime();

        if (isSolar) {
            // 양력인 경우 바로 계산
            try {
                LocalDate solDate = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return Mono.just(buildManseResponse(solDate, birthTime, input));
            } catch (DateTimeParseException e) {
                return Mono.error(new CustomException(ErrorCode.INVALID_DATE_FORMAT));
            }
        } else {
            // 음력인 경우 KASI API 호출 후 계산
            try {
                LocalDate lunDate = LocalDate.parse(birthDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                String lunYear = String.valueOf(lunDate.getYear());
                String lunMonth = pad2(lunDate.getMonthValue());
                String lunDay = pad2(lunDate.getDayOfMonth());

                return calendarService.convertToSolar(lunYear, lunMonth, lunDay)
                        .map(response -> {
                            LocalDate solDate = response.toLocalDate();
                            return buildManseResponse(solDate, birthTime, input);
                        })
                        .onErrorMap(e -> {
                            if (e instanceof CustomException) {
                                return e;
                            }
                            return new CustomException(ErrorCode.KASI_ERROR);
                        });
            } catch (DateTimeParseException e) {
                return Mono.error(new CustomException(ErrorCode.INVALID_DATE_FORMAT));
            }
        }
    }

    /**
     * 만세력 응답 생성
     */
    private ManseResponse buildManseResponse(LocalDate solDate, String birthTime, ManseRequest input) {
        String yearGanZhi = getYearGanZhi(solDate);
        String monthGanZhi = getMonthGanZhi(solDate);

        String dayGanZhi = birthTime.equals("모름")
                ? getDayGanZhiWithoutTime(solDate) : getDayGanZhi(solDate);

        String hourGanZhi = birthTime.equals("모름")
                ? "모름 모름" : getHourGanZhi(solDate, birthTime);

        String manseInfo = String.format(
                "%s %s %s %s",
                yearGanZhi, monthGanZhi, dayGanZhi, hourGanZhi
        );

        return new ManseResponse(manseInfo, input.getGender(), input.getName());
    }

    public static String pad2(int n) {
        return String.format("%02d", n);
    }

    // 연주 계산
    private String getYearGanZhi(LocalDate date) {
        int year = date.getYear();
        year = isBeforeIpchun(year, date.getMonthValue(), date.getDayOfMonth()) ? year - 1 : year;

        Tenkan yearGan = Tenkan.fromIndex((year - 4) % 10);
        Jiji yearZhi = Jiji.fromIndex((year - 4) % 12);

        return formatGanZhi(yearGan, yearZhi);
    }

    // 월주 계산
    private String getMonthGanZhi(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        String zhiHanja = SolarTerm.getZhiForDate(month, day);
        Jiji monthZhi = Jiji.fromHanja(zhiHanja);

        int yearGanIndex = (year - 4) % 10;
        int monthGanIndex = (yearGanIndex * 2 + monthZhi.ordinal()) % 10;

        return formatGanZhi(Tenkan.fromIndex(monthGanIndex), monthZhi);
    }

    // 일주 계산
    public String getDayGanZhi(LocalDate date) {
        long daysElapsed = ChronoUnit.DAYS.between(BASE_DATE, date);
        int ganIndex = (int) (daysElapsed % 10);
        int zhiIndex = (10 + (int) (daysElapsed % 12)) % 12;

        return getGanZhi(ganIndex, zhiIndex);
    }

    // 태어난 시간 모를 경우
    public String getDayGanZhiWithoutTime(LocalDate date) {
        long daysElapsed = ChronoUnit.DAYS.between(BASE_DATE, date);
        int ganIndex = (int) (daysElapsed % 10);
        int zhiIndex = (10 + (int) (daysElapsed % 12)) % 12;

        return getGanZhi(ganIndex, zhiIndex);
    }

    // 시주(時柱) 계산
    private String getHourGanZhi(LocalDate date, String hourLabelWithTime) {
        int totalDays = (int) ChronoUnit.DAYS.between(BASE_DATE, date) % 60;
        int dayGanIndex = totalDays % 10;

        HourBranch hour = HourBranch.fromLabel(hourLabelWithTime);
        int hourIndex = hour.getIndex();

        Tenkan hourGan = Tenkan.fromIndex((dayGanIndex * 2 + hourIndex) % 10);
        Jiji hourZhi = Jiji.fromIndex(hourIndex);

        return formatGanZhi(hourGan, hourZhi);
    }

    // 입춘 이전인지 확인
    private boolean isBeforeIpchun(int year, int month, int day) {
        LocalDate ipchunDate = LocalDate.of(year, 2, 4);
        LocalDate birthDate = LocalDate.of(year, month, day);
        return birthDate.isBefore(ipchunDate);
    }

    // 천간과 지지를 포맷하여 문자열 반환
    private String getGanZhi(int ganIndex, int zhiIndex) {
        Tenkan gan = Tenkan.fromIndex(ganIndex);
        Jiji zhi = Jiji.fromIndex(zhiIndex);

        return formatGanZhi(gan, zhi);
    }

    // 데이터 포맷하여 결과 반환
    private String formatGanZhi(Tenkan gan, Jiji zhi) {
        return String.format("%s(%s/%s) %s(%s/%s)",
                gan.getName(), gan.getElement(), gan.getYinYang(),
                zhi.getName(), zhi.getElement(), zhi.getYinYang());
    }
}

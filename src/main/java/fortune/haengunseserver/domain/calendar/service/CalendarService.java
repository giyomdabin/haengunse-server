package fortune.haengunseserver.domain.calendar.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fortune.haengunseserver.domain.calendar.dto.response.LunToSolResponse;
import fortune.haengunseserver.global.exception.CustomException;
import fortune.haengunseserver.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final WebClient kasiClient;

    @Value("${kasi.api-key}")
    private String serviceKey;

    /**
     * 음력을 양력으로 변환 (리액티브)
     */
    public Mono<LunToSolResponse> convertToSolar(String lunYear, String lunMonth, String lunDay) {
        return kasiClient.get()
                .uri(uriBuilder -> uriBuilder.path("/getSolCalInfo")
                        .queryParam("lunYear", lunYear)
                        .queryParam("lunMonth", lunMonth)
                        .queryParam("lunDay", lunDay)
                        .queryParam("ServiceKey", serviceKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::parseResponse)
                .onErrorMap(e -> {
                    if (e instanceof CustomException) {
                        return e;
                    }
                    return new CustomException(ErrorCode.KASI_ERROR);
                });
    }

    private Mono<LunToSolResponse> parseResponse(String xml) {
        try {
            int start = xml.indexOf("<item>");
            int end = xml.indexOf("</item>");
            if (start == -1 || end == -1) {
                return Mono.error(new CustomException(ErrorCode.XML_PARSE_ERROR));
            }

            String itemXml = xml.substring(start, end + "</item>".length());

            XmlMapper xmlMapper = new XmlMapper();
            LunToSolResponse result = xmlMapper.readValue(itemXml, LunToSolResponse.class);

            return Mono.just(result);
        } catch (Exception e) {
            return Mono.error(new CustomException(ErrorCode.XML_PARSE_ERROR));
        }
    }
}

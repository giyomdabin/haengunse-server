package fortune.haengunseserver.domain.gpt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MindLogicChatRequest(
        String model,
        List<Message> messages,
        Double temperature
) {
    public record Message(String role, String content) {}
}

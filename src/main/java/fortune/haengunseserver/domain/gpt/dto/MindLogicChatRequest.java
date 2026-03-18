package fortune.haengunseserver.domain.gpt.dto;

import java.util.List;

public record MindLogicChatRequest(
        String model,
        List<Message> messages
) {
    public record Message(String role, String content) {}
}

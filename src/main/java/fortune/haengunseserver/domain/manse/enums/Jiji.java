package fortune.haengunseserver.domain.manse.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Jiji {

    자("자", "子", "수", "음"),
    축("축", "丑", "토", "음"),
    인("인", "寅", "목", "양"),
    묘("묘", "卯", "목", "음"),
    진("진", "辰", "토", "양"),
    사("사", "巳", "화", "양"),
    오("오", "午", "화", "음"),
    미("미", "未", "토", "음"),
    신("신", "申", "금", "양"),
    유("유", "酉", "금", "음"),
    술("술", "戌", "토", "양"),
    해("해", "亥", "수", "양");

    private final String name;
    private final String hanja;
    private final String element;
    private final String yinYang;

    public static Jiji fromIndex(int index) {
        return values()[index % 12];
    }

    public static Jiji fromHanja(String hanja) {
        for (Jiji j : values()) {
            if (j.getHanja().equals(hanja)) {
                return j;
            }
        }
        throw new IllegalArgumentException("No matching Jiji for hanja: " + hanja);
    }
}

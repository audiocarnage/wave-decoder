package ch.georgiou.audio.wavedecoder.decoder;

import javax.annotation.Nonnull;
import java.util.Arrays;

public enum AudioCodingFormat {

    LINEAR_PCM(1, "linear pulse-code modulation");

    private final int code;
    private final String name;

    AudioCodingFormat(int code, @Nonnull String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public static AudioCodingFormat from(int value) {
        return Arrays.stream(values())
                .filter(audioCodingFormat -> audioCodingFormat.code == value)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unknown audio coding format code provided: " + value));
    }
}

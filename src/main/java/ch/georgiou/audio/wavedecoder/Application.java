package ch.georgiou.audio.wavedecoder;

import ch.georgiou.audio.wavedecoder.decoder.Decoder;
import ch.georgiou.audio.wavedecoder.decoder.RIFFWaveHeader;
import ch.georgiou.audio.wavedecoder.player.AudioPlayer;
import lombok.extern.log4j.Log4j2;
import org.jooq.lambda.Unchecked;

import javax.annotation.Nonnull;
import java.io.FileInputStream;
import java.util.Objects;
import java.util.function.Consumer;

@Log4j2
public class Application {

    private static final int PCM_SAMPLE_BLOCK_SIZE = 64;

    public static void main(String[] args) {
        log.debug("Available CPU cores: {}", Runtime.getRuntime().availableProcessors());

        if (args.length < 1) {
            usage();
            throw new IllegalArgumentException();
        }

        start(args[0]);
    }

    private static void start(@Nonnull String fileName) {
        AudioPlayer audioPlayer = new AudioPlayer();
        decode(Unchecked.consumer(decoder -> {
            decoder.setStream(new FileInputStream(fileName));
            RIFFWaveHeader riffWaveHeader = Objects.requireNonNull(decoder.getRIFFWaveHeader());
            log.info(riffWaveHeader);
            audioPlayer.playback(riffWaveHeader, line -> {
                byte[] sampleBlock;
                log.debug("PCM sample block size: " + PCM_SAMPLE_BLOCK_SIZE);
                log.info("starting playback...");
                while ((sampleBlock = decoder.readNextNSamples(PCM_SAMPLE_BLOCK_SIZE)) != null) {
                    line.write(sampleBlock, 0, sampleBlock.length);
                }
                log.info("playback stopped");
            });
        }));
    }

    private static void decode(@Nonnull Consumer<Decoder> decoderConsumer) {
        try (Decoder decoder = new Decoder()) {
            decoderConsumer.accept(decoder);
        }
    }


    private static void usage() {
        log.error("Program argument is missing. Please provide an absolute file path.");
    }
}

package ch.georgiou.audio.wavedecoder.player;

import ch.georgiou.audio.wavedecoder.decoder.RIFFWaveHeader;

import javax.annotation.Nonnull;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.function.Consumer;

public class AudioPlayer {

    public void playback(@Nonnull RIFFWaveHeader riffWaveHeader, @Nonnull Consumer<SourceDataLine> lineConsumer)
            throws LineUnavailableException {
        AudioFormat audioFormat = getAudioFormat(riffWaveHeader);
        try (SourceDataLine line = getSourceDataLine(audioFormat)) {
            line.open(audioFormat);
            line.start();
            lineConsumer.accept(line);
            line.drain();
            line.stop();
        }
    }

    @Nonnull
    private SourceDataLine getSourceDataLine(@Nonnull AudioFormat audioFormat) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
        if (!AudioSystem.isLineSupported(info)) {
            throw new RuntimeException("Audio format is not supported by audio system.");
        }
        return AudioSystem.getSourceDataLine(audioFormat);
    }

    @Nonnull
    private AudioFormat getAudioFormat(@Nonnull RIFFWaveHeader riffWaveHeader) {
        return new AudioFormat(getEncoding(riffWaveHeader),
                riffWaveHeader.getSampleRate(),
                riffWaveHeader.getBitsPerSample(),
                riffWaveHeader.getChannels(),
                riffWaveHeader.getBytesPerSample(),
                riffWaveHeader.getBytesPerSecond(),
                false);
    }

    /**
     * Inconsistency in RIFF WAVE file format.<br>
     * 8-bit samples are stored as unsigned bytes [0, 255].<br>
     * 16-bit samples are stored as 2's-complement signed shorts [-32768, 32767].
     * @param riffWaveHeader RIFF WAVE header
     * @return the audio format
     */
    @Nonnull
    private AudioFormat.Encoding getEncoding(@Nonnull RIFFWaveHeader riffWaveHeader) {
        return riffWaveHeader.getBitsPerSample() == 8 ?
                AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED;
    }
}

package ch.georgiou.audio.wavedecoder.decoder;

import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>This decoder handles uncompressed linear PCM audio files, the most common type of RIFF (Resource Interchange File Format) files.
 * The header contains the format of the sound file and provides therefore information about file type, audio coding format,
 * sample rate, quantisation bits per sample, number of channels, byte rate, as well as the length of audio data.</p>
 * <p>The header of a RIFF WAVE file is 44 bytes in size and has the following format:</p>
 *
 * <table>
 *     <caption> </caption>
 * <thead>
 * <tr>
 *      <th>Position</th>
 *      <th>Example value</th>
 *      <th>Description</th>
 * </tr>
 * </thead>
 * <tr>
 *      <td>0 - 3</td>
 *      <td>"RIFF"</td>
 *      <td>RIFF chunk descriptor containing the characters "RIFF" in ASCII. Marks the file as a RIFF file container. 0x46464952 in little endian.</td>
 * </tr>
 * <tr>
 *      <td>4 - 7</td>
 *      <td>6991680</td>
 *      <td>RIFF chunk size representing the overall file size - 8 bytes (RIFF chunk descriptor and this value).</td>
 * </tr>
 * <tr>
 *      <td>8 - 11</td>
 *      <td>"WAVE"</td>
 *      <td>WAVE format descriptor containing the characters "WAVE" in ASCII. 0x45564157 in little endian.</td>
 * </tr>
 * <tr>
 *      <td>12 - 15</td>
 *      <td>"fmt "</td>
 *      <td>Format sub-chunk descriptor containing the characters "fmt " (including trailing whitespace) describing the sound format. 0x20746d66 in little endian.</td>
 * </tr>
 * <tr>
 *      <td>16 - 19</td>
 *      <td>16</td>
 *      <td>Length of format data as listed above. This value is always 16 for a RIFF WAVE audio file.</td>
 * </tr>
 * <tr>
 *      <td>20 - 21</td>
 *      <td>1</td>
 *      <td>Audio coding format. E.g. 1 is linear pulse-code modulation.</td>
 * </tr>
 * <tr>
 *      <td>22 - 23</td>
 *      <td>2</td>
 *      <td>Number of channels.</td>
 * </tr>
 * <tr>
 *      <td>24 - 27</td>
 *      <td>48000</td>
 *      <td>Sample rate in Hz (number of samples per second)</td>
 * </tr>
 * <tr>
 *      <td>28 - 31</td>
 *      <td>176400</td>
 *      <td>The byte rate is the speed of the audio data stream. Sample Rate * bits per sample * channels / 8</td>
 * </tr>
 * <tr>
 *      <td>32 - 33</td>
 *      <td>4</td>
 *      <td>Block alignment expressed in bytes. The number of bytes for one sample including all channels.</td>
 * </tr>
 * <tr>
 *      <td>34 - 35</td>
 *      <td>16</td>
 *      <td>Quantisation bits per sample</td>
 * </tr>
 * <tr>
 *      <td>36 - 39</td>
 *      <td>"data"</td>
 *      <td>data sub-chunk descriptor containing the characters "data". 0x61746164 in little endian. Marks the beginning of the data section.</td>
 * </tr>
 * <tr>
 *      <td>40 - 43</td>
 *      <td>6991448</td>
 *      <td>Size of the audio data in bytes.</td>
 * </tr>
 * </table>
 *
 * <p>8-bit audio is stored as unsigned bytes in the range [0, 255].<br>
 *    16-bit audio is usually represented as signed 2s'-complement short values in the range [-32768, 32767]</p>
 *
 * @author Rémi Georgiou
 */
@Log4j2
public class Decoder implements AutoCloseable {

    private InputStream audioStream;
    private RIFFWaveHeader riffWaveHeader;

    /**
     * Initialise the decoder with the {@link InputStream} of a RIFF WAVE sound file.<br>
     * @param audioStream the audio stream
     */
    public synchronized void setStream(@Nonnull InputStream audioStream) {
        this.audioStream = audioStream;
    }

    /**
     * Reads a sample block of size N samples or the minimum of available bytes of the audio stream.<br>
     * This method returns N samples <b>including all channels.</b><br>
     * Returns null when the end of the stream is reached.
     * @param samples the number of samples to be read from the audio stream.
     * @return A sample block of size N samples.
     */
    @Nullable
    public byte[] readNextNSamples(int samples) {
        try {
            if (audioStream == null || audioStream.available() == 0) {
                log.info("reached end of stream");
                return null;
            }
            byte[] sampleBlock = new byte[Math.min(riffWaveHeader.getChannels() * samples, audioStream.available())];
            audioStream.read(sampleBlock);
            return sampleBlock;
        } catch (IOException e) {
            log.error(e);
        }
        return null;
    }

    /**
     * The decoder attempts to extract the RIFF WAVE file header from the audio stream.
     * @return The RIFF WAVE file header
     */
    @Nonnull
    public RIFFWaveHeader getRIFFWaveHeader() {
        if (riffWaveHeader == null) {
            riffWaveHeader = extractRIFFWaveHeader();
        }
        return riffWaveHeader;
    }

    /**
     * Close the underlying audio stream of the decoder instance.
     */
    @Override
    public void close() {
        try {
            audioStream.close();
        } catch (IOException e) {
            log.error("failed to close audio stream");
        }
    }

    /**
     * Read first 4 bytes from the byte array and transform it to a 32-bit integer in little endian form.
     * @param bytes a byte array
     * @return integer value
     */
    int getIntLittleEndian(@Nonnull byte[] bytes) {
        return (bytes[0] & 0xff)
                | ((bytes[1] & 0xff) << 8)
                | ((bytes[2] & 0xff) << 16)
                | ((bytes[3] & 0xff) << 24);
    }

    /**
     * Read first 2 bytes from the byte array and transform it to a 16-bit short in little endian form.
     * @param bytes a byte array
     * @return short value
     */
    short getShortLittleEndian(@Nonnull byte[] bytes) {
        return (short) ((bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8));
    }

    /**
     * Extract the RIFF WAVE file header from the audio stream containing the format of the sound information
     * @return the RIFF WAVE file header
     */
    @Nonnull
    private RIFFWaveHeader extractRIFFWaveHeader() {
        try {
            // Check if file container format is RIFF (Resource Interchange File Format).
            int riffChunkId = getIntLittleEndian(audioStream.readNBytes(4));
            if (riffChunkId != RIFFWaveHeader.RIFF_CHUNK_DESCRIPTOR) {
                throw new DecoderException("Not a RIFF file.");
            }

            // This is the file size minus 8 bytes (RIFF chunk ID and RIFF chunk size) - ignored
            int chunkSize = getIntLittleEndian(audioStream.readNBytes(4));

            // Check if the file format is WAVE.
            int format = getIntLittleEndian(audioStream.readNBytes(4));
            if (format != RIFFWaveHeader.WAVE_CHUNK_DESCRIPTOR) {
                throw new DecoderException("Unknown file format - not WAVE.");
            }

            // Check the format sub-chunk.
            int formatChunkId = getIntLittleEndian(audioStream.readNBytes(4));
            if (formatChunkId != RIFFWaveHeader.FORMAT_CHUNK_DESCRIPTOR) {
                throw new DecoderException("Illegal format sub-chunk.");
            }

            // Size of the PCM sub-chunk - should be 16 bytes.
            int pcmSubChunkSize = getIntLittleEndian(audioStream.readNBytes(4));
            if (pcmSubChunkSize != RIFFWaveHeader.PCM_SUB_CHUNK1_SIZE) {
                throw new DecoderException("Illegal PCM sub-chunk size: " + pcmSubChunkSize);
            }

            // Read two bytes at position 20 and check if the audio coding format is linear PCM.
            short encodingFormat = getShortLittleEndian(audioStream.readNBytes(2));
            if (encodingFormat != AudioCodingFormat.LINEAR_PCM.getCode()) {
                throw new DecoderException("Unsupported audio coding format");
            }
            log.debug("audio coding format: " + encodingFormat);

            // Read two bytes at position 22 to get the number of channels.
            short channels = getShortLittleEndian(audioStream.readNBytes(2));
            log.debug("number of channels: " + channels);

            // Read four bytes at position 24 and to get the sample rate.
            int sampleRate = getIntLittleEndian(audioStream.readNBytes(4));
            log.debug("sample rate: " + sampleRate);

            /*
                Read four bytes at position 28 to get the byte rate.
                Bytes per second is the speed of the audio data stream:
                sample rate * number of channels * bits per sample / 8
             */
            int bytesPerSecond = getIntLittleEndian(audioStream.readNBytes(4));
            log.debug("audio data stream speed: {} bytes/second", bytesPerSecond);

            /*
                Read two bytes at position 32 to get the block alignment
                (the number of bytes for one sample including all channels.)
             */
            short blockAlignment = getShortLittleEndian(audioStream.readNBytes(2));
            log.debug("block alignment: " + blockAlignment);

            // Read two bytes at position 34 to get the quantisation bits per sample
            short bitsPerSample = getShortLittleEndian(audioStream.readNBytes(2));
            log.debug("quantisation bits per sample: " + bitsPerSample);

            /*
                In general the RIFF WAVE header size is 44 bytes. There may be additional sub-chunks. If so,
                each will have a 4 bytes SubChunkID, 4 bytes of SubChunkSize and SubChunkSize amount of data.
                The rest is audio data.
                "data" marker should follow at position 36 - skip over any padding/junk data.
             */
            int junkData = 0;
            while (getIntLittleEndian(audioStream.readNBytes(4)) != RIFFWaveHeader.DATA_CHUNK_DESCRIPTOR) {
                int subChunkSize = getIntLittleEndian(audioStream.readNBytes(4));
                junkData += audioStream.skip(subChunkSize);
            }
            log.debug("skipped over {} bytes of junk data", junkData);

            int dataSize = getIntLittleEndian(audioStream.readNBytes(4));
            return new RIFFWaveHeader(AudioCodingFormat.from(encodingFormat), channels, sampleRate, bitsPerSample, dataSize);
        } catch (IOException | DecoderException ex) {
            log.error("Error while extracting RIFF WAVE header. " + ex.getMessage());
            throw new RuntimeException();
        }
    }
}

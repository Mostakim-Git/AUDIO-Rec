package com.mostakim.audiorec.audio;

import java.io.File;
import java.io.IOException;

/**
 * Where captured audio goes.
 *
 * Implementations receive interleaved 32-bit float frames (already gained and
 * clamped) and are responsible for the target bit depth, container headers and
 * finalising sizes on close.
 */
public interface AudioSink {

    /** create the file and write a header that can be patched at close */
    void open(File file, int sampleRate, int channels, int bitDepth) throws IOException;

    /** interleaved float samples; `samples` = frames * channels */
    void write(float[] interleaved, int samples) throws IOException;

    /** finalise headers; `frames` is the authoritative sample count */
    void close(long frames) throws IOException;

    /** bytes on disk so far (header included) */
    long bytesWritten();

    /** the file this sink is writing */
    File file();

    /** true when the container can be inspected/repaired by FormatProbe */
    String container();

    /** encoder statistics for the take report; may return null */
    String stats();
}

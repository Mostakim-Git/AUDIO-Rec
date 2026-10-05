package com.mostakim.audiorec.audio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mostakim.audiorec.db.Models.Track;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Renders a take into another container/depth/rate.
 *
 * Sources are read with the same two paths playback uses (native reader for
 * WAV/AIFF, platform decoder for FLAC/OGG), and written through the same sinks
 * the recorder uses, so an export is sample-identical to the take whenever no
 * resampling is requested.
 */
public class ExportTask {

    private static final String TAG = "ExportTask";

    public interface Done {
        void onDone(Result result);
    }

    public interface Fail {
        void onError(String message);
    }

    public static class Result {
        public String container;
        public int sampleRate, bitDepth, channels;
        public long bytes, frames, durationMs;
        public String note = "";
    }

    private final Context mContext;
    private final Track mTrack;
    private final File mOut;
    private final String mContainer;
    private final int mDepth;
    private final int mRate;
    private final Done mDone;
    private final Fail mFail;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public ExportTask(Context ctx, Track track, File out, String container, int depth, int rate,
                      Done done, Fail fail) {
        mContext = ctx.getApplicationContext();
        mTrack = track;
        mOut = out;
        mContainer = container;
        mDepth = depth;
        mRate = rate;
        mDone = done;
        mFail = fail;
    }

    public void start() {
        new Thread(this::run, "audiorec-export").start();
    }

    private void run() {
        File src = new File(mTrack.filePath);
        if (!src.exists()) {
            fail("Source file is missing");
            return;
        }
        AudioSink sink = null;
        try {
            RawPcmReader raw = RawPcmReader.open(src);
            int srcRate, srcChannels;
            if (raw != null) {
                srcRate = raw.sampleRate;
                srcChannels = raw.channels;
            } else {
                srcRate = mTrack.sampleRate;
                srcChannels = mTrack.channels;
            }
            if (srcRate <= 0) srcRate = 48000;
            if (srcChannels <= 0) srcChannels = 2;

            sink = openSink();
            sink.open(mOut, mRate > 0 ? mRate : srcRate, srcChannels, mDepth);

            Result r = new Result();
            r.container = sink.container();       // may differ from the request when Opus is absent
            r.sampleRate = mRate > 0 ? mRate : srcRate;
            r.bitDepth = mDepth;
            r.channels = srcChannels;

            long frames;
            if (raw != null) {
                frames = copyRaw(raw, sink, srcRate, srcChannels);
                raw.close();
            } else {
                frames = copyDecoded(src, sink, srcRate, srcChannels);
            }
            sink.close(frames);
            r.frames = frames;
            r.bytes = mOut.length();
            r.durationMs = r.sampleRate > 0 ? frames * 1000L / r.sampleRate : 0;
            r.note = sink.stats();
            final Result fr = r;
            if (mDone != null) mMain.post(() -> mDone.onDone(fr));
        } catch (Exception e) {
            Log.w(TAG, "export failed", e);
            try {
                if (sink != null) sink.close(0);
            } catch (Exception ignored) {
            }
            fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private AudioSink openSink() {
        if ("flac".equals(mContainer)) return new FlacWriter();
        if ("aiff".equals(mContainer)) return new AiffWriter();
        if ("ogg".equals(mContainer)) {
            if (OggOpusWriter.encoderAvailable()) return new OggOpusWriter();
            else return new WavWriter();
        }
        WavWriter w = new WavWriter();
        return w;
    }

    private long copyRaw(RawPcmReader raw, AudioSink sink, int srcRate, int srcChannels)
            throws Exception {
        int block = 4096;
        float[] buf = new float[block * srcChannels];
        double ratio = mRate > 0 ? (double) mRate / srcRate : 1.0;
        long outFrames = 0;
        int n;
        Resampler resampler = Math.abs(ratio - 1.0) > 1e-9
                ? new Resampler(srcChannels, srcRate, mRate) : null;
        while ((n = raw.read(buf, block)) > 0) {
            if (resampler != null) {
                float[] out = resampler.process(buf, n);
                if (out.length > 0) {
                    sink.write(out, out.length);
                    outFrames += out.length / srcChannels;
                }
            } else {
                sink.write(buf, n * srcChannels);
                outFrames += n;
            }
        }
        return outFrames;
    }

    private long copyDecoded(File src, AudioSink sink, int srcRate, int srcChannels)
            throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(src.getAbsolutePath());
            int index = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    index = i;
                    fmt = f;
                    break;
                }
            }
            if (index < 0 || fmt == null) throw new Exception("no audio track in source");
            ex.selectTrack(index);
            int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            Resampler resampler = (mRate > 0 && mRate != rate)
                    ? new Resampler(channels, rate, mRate) : null;
            long outFrames = 0;

            while (!outputDone) {
                if (!inputDone) {
                    int inIdx = codec.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        ByteBuffer in = codec.getInputBuffer(inIdx);
                        int size = in == null ? -1 : ex.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int outIdx = codec.dequeueOutputBuffer(info, 10000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) continue;
                if (outIdx < 0) continue;
                boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                ByteBuffer out = codec.getOutputBuffer(outIdx);
                if (out != null && info.size > 0 && !config) {
                    out.position(info.offset);
                    out.limit(info.offset + info.size);
                    float[] floats = toFloat(out, info.size, channels);
                    int frames = floats.length / channels;
                    if (resampler != null) {
                        float[] rs = resampler.process(floats, frames);
                        if (rs.length > 0) {
                            sink.write(rs, rs.length);
                            outFrames += rs.length / channels;
                        }
                    } else {
                        sink.write(floats, floats.length);
                        outFrames += frames;
                    }
                }
                codec.releaseOutputBuffer(outIdx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
            }
            return outFrames;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
                codec.release();
            }
            ex.release();
        }
    }

    private float[] toFloat(ByteBuffer buf, int size, int channels) {
        int samples = size / 2;
        float[] out = new float[samples];
        for (int i = 0; i < samples; i++) {
            int lo = buf.get() & 0xFF;
            int hi = buf.get() & 0xFF;
            int v = (hi << 8) | lo;
            if (v > 32767) v -= 65536;
            out[i] = v * (1f / 32768f);
        }
        return out;
    }

    private void fail(final String msg) {
        if (mFail != null) mMain.post(() -> mFail.onError(msg));
    }

    /** simple linear resampler with fractional state carried between blocks */
    private static class Resampler {
        private final int channels;
        private final double ratio;
        private double pos;
        private float[] lastBlock;

        Resampler(int channels, int srcRate, int dstRate) {
            this.channels = channels;
            this.ratio = (double) dstRate / srcRate;
        }

        float[] process(float[] src, int frames) {
            if (lastBlock == null) lastBlock = new float[0];
            int total = frames + lastBlock.length / channels;
            float[] all = new float[total * channels];
            System.arraycopy(lastBlock, 0, all, 0, lastBlock.length);
            System.arraycopy(src, 0, all, lastBlock.length, frames * channels);

            int outFrames = 0;
            float[] tmp = new float[(int) (total / ratio + 4) * channels];
            double p = pos;
            while (p < total - 1) {
                int i0 = (int) p;
                double frac = p - i0;
                int i1 = i0 + 1;
                for (int c = 0; c < channels; c++) {
                    float a = all[i0 * channels + c];
                    float b = all[i1 * channels + c];
                    tmp[outFrames * channels + c] = (float) (a + (b - a) * frac);
                }
                outFrames++;
                p += 1.0 / ratio;
            }
            pos = p - (total - 1);                 // carry the fraction
            if (pos < 0) pos = 0;
            // keep the last frame for interpolation continuity
            lastBlock = new float[channels];
            System.arraycopy(all, (total - 1) * channels, lastBlock, 0, channels);

            float[] out = new float[outFrames * channels];
            System.arraycopy(tmp, 0, out, 0, out.length);
            return out;
        }
    }

    public static String describe(String container, int depth, int rate) {
        return String.format(Locale.US, "%s %d-bit %d Hz", container.toUpperCase(), depth, rate);
    }
}

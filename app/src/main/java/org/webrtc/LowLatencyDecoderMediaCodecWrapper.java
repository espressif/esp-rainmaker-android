// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.webrtc;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;

/**
 * MediaCodec wrapper that injects KEY_LOW_LATENCY, KEY_PRIORITY (realtime), and
 * KEY_OPERATING_RATE into the {@link MediaFormat} for H.264 <em>decoder</em>
 * configure() calls.
 *
 * <p>Mitigates the Unisoc (and several other mid-tier vendor) decoder "backlog
 * death spiral": a single slow frame causes MediaCodec's input queue to fill,
 * after which every subsequent frame takes ~1 second to surface. WebRTC's
 * playout algorithm then drops 90%+ of incoming frames because
 * current_delay &gt;&gt; target_delay, and the pipeline locks at 1-2 fps.
 *
 * <ul>
 *   <li>{@code KEY_LOW_LATENCY=1} (API 30+) tells the codec to prioritize
 *       per-frame output latency over throughput batching.</li>
 *   <li>{@code KEY_OPERATING_RATE} hints the codec scheduler at the expected
 *       frame rate so vendor implementations allocate enough CPU/DSP cycles.</li>
 *   <li>{@code KEY_PRIORITY=0} requests realtime priority for codec scheduling.</li>
 * </ul>
 *
 * <p>Only applied when {@code flags & CONFIGURE_FLAG_ENCODE == 0} so the
 * encoder path is unaffected.
 */
final class LowLatencyDecoderMediaCodecWrapper implements MediaCodecWrapper {
    private static final String TAG = "LowLatencyDec";
    private static final String MIME_AVC = "video/avc";

    private final MediaCodecWrapper delegate;

    LowLatencyDecoderMediaCodecWrapper(MediaCodecWrapper delegate) {
        this.delegate = delegate;
    }

    @Override
    public void configure(MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
        boolean isEncoder = (flags & MediaCodec.CONFIGURE_FLAG_ENCODE) != 0;
        if (!isEncoder && MIME_AVC.equals(format.getString(MediaFormat.KEY_MIME))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }
            format.setInteger(MediaFormat.KEY_OPERATING_RATE, 120);
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            Log.i(TAG, "H.264 decoder low-latency hints injected: " + format);
        }
        delegate.configure(format, surface, crypto, flags);
    }

    @Override public void start() { delegate.start(); }
    @Override public void flush() { delegate.flush(); }
    @Override public void stop() { delegate.stop(); }
    @Override public void release() { delegate.release(); }
    @Override public int dequeueInputBuffer(long timeoutUs) { return delegate.dequeueInputBuffer(timeoutUs); }
    @Override public void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags) {
        delegate.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
    }
    @Override public int dequeueOutputBuffer(MediaCodec.BufferInfo info, long timeoutUs) {
        return delegate.dequeueOutputBuffer(info, timeoutUs);
    }
    @Override public void releaseOutputBuffer(int index, boolean render) { delegate.releaseOutputBuffer(index, render); }
    @Override public MediaFormat getInputFormat() { return delegate.getInputFormat(); }
    @Override public MediaFormat getOutputFormat() { return delegate.getOutputFormat(); }
    @Override public MediaFormat getOutputFormat(int index) { return delegate.getOutputFormat(index); }
    @Override public ByteBuffer getInputBuffer(int index) { return delegate.getInputBuffer(index); }
    @Override public ByteBuffer getOutputBuffer(int index) { return delegate.getOutputBuffer(index); }
    @Override public Surface createInputSurface() { return delegate.createInputSurface(); }
    @Override public void setParameters(Bundle params) { delegate.setParameters(params); }
    @Override public MediaCodecInfo getCodecInfo() { return delegate.getCodecInfo(); }
}

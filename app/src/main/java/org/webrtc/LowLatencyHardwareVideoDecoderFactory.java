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

import android.util.Log;
import androidx.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * {@link HardwareVideoDecoderFactory} subclass that injects low-latency hints
 * (KEY_LOW_LATENCY, KEY_PRIORITY, KEY_OPERATING_RATE) on every produced H.264
 * decoder by swapping the {@link MediaCodecWrapperFactory} field of the
 * underlying {@link AndroidVideoDecoder} with a
 * {@link LowLatencyDecoderMediaCodecWrapperFactory} wrapper.
 *
 * <p>The reflection pattern matches what
 * {@link BaselineHardwareVideoEncoderFactory} does for the encoder side --
 * stream-webrtc-android 1.3.10's {@code AndroidVideoDecoder} does not expose
 * any configuration knob for these flags, so we reach in via reflection.
 *
 * <p>VP8/VP9/AV1 decoders are left untouched.
 */
public class LowLatencyHardwareVideoDecoderFactory extends HardwareVideoDecoderFactory {
    private static final String TAG = "LowLatencyDecFactory";
    private static final String FIELD_NAME = "mediaCodecWrapperFactory";

    public LowLatencyHardwareVideoDecoderFactory(EglBase.Context sharedContext) {
        super(sharedContext);
    }

    @Nullable
    @Override
    public VideoDecoder createDecoder(VideoCodecInfo input) {
        VideoDecoder decoder = super.createDecoder(input);
        if (decoder instanceof AndroidVideoDecoder && "H264".equalsIgnoreCase(input.name)) {
            injectLowLatencyWrapper((AndroidVideoDecoder) decoder);
        }
        return decoder;
    }

    private static void injectLowLatencyWrapper(AndroidVideoDecoder decoder) {
        try {
            Field field = AndroidVideoDecoder.class.getDeclaredField(FIELD_NAME);
            field.setAccessible(true);
            Object current = field.get(decoder);
            if (current instanceof MediaCodecWrapperFactory
                && !(current instanceof LowLatencyDecoderMediaCodecWrapperFactory)) {
                field.set(decoder, new LowLatencyDecoderMediaCodecWrapperFactory((MediaCodecWrapperFactory) current));
                Log.i(TAG, "H.264 decoder patched: MediaCodecWrapperFactory wrapped with LowLatencyDecoderMediaCodecWrapperFactory");
            }
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "Could not patch H.264 decoder; decoder may run with default latency profile", e);
        }
    }
}

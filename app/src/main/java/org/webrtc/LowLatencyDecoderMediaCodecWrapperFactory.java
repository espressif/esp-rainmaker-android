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

import java.io.IOException;

/**
 * {@link MediaCodecWrapperFactory} decorator that wraps every produced
 * {@link MediaCodecWrapper} with {@link LowLatencyDecoderMediaCodecWrapper}
 * so H.264 decoder configure calls get the low-latency hints injected.
 */
final class LowLatencyDecoderMediaCodecWrapperFactory implements MediaCodecWrapperFactory {
    private final MediaCodecWrapperFactory delegate;

    LowLatencyDecoderMediaCodecWrapperFactory(MediaCodecWrapperFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public MediaCodecWrapper createByCodecName(String name) throws IOException {
        return new LowLatencyDecoderMediaCodecWrapper(delegate.createByCodecName(name));
    }
}

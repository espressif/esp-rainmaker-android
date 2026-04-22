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

package com.espressif.ui.webrtc

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import com.espressif.webrtc.WebRtcConstants
import org.webrtc.JavaI420Buffer
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import java.nio.ByteBuffer

/**
 * SurfaceViewRenderer subclass that can vertically flip the displayed video.
 * Controlled by [WebRtcConstants.FLIP_VIDEO_VERTICAL].
 *
 * For TextureBuffer frames (hardware-decoded, the common path), the flip is
 * zero-copy — only the sampling transform matrix is modified.
 * For I420 frames (rare software-decode fallback), rows are copied in reverse.
 */
class FlippableSurfaceViewRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceViewRenderer(context, attrs) {

    override fun onFrame(frame: VideoFrame) {
        if (!WebRtcConstants.FLIP_VIDEO_VERTICAL) {
            super.onFrame(frame)
            return
        }

        val buffer = frame.buffer
        val flippedBuffer: VideoFrame.Buffer = if (buffer is VideoFrame.TextureBuffer) {
            flipTextureBuffer(buffer)
        } else {
            flipViaI420(buffer)
        }

        val flippedFrame = VideoFrame(flippedBuffer, frame.rotation, frame.timestampNs)
        super.onFrame(flippedFrame)
        flippedFrame.release()
    }

    /**
     * Wrap a TextureBuffer with a modified transform matrix that flips Y.
     * Zero-copy: the underlying texture is shared.
     */
    private fun flipTextureBuffer(buf: VideoFrame.TextureBuffer): VideoFrame.TextureBuffer {
        buf.retain()
        return object : VideoFrame.TextureBuffer {
            override fun getType() = buf.type
            override fun getTextureId() = buf.textureId
            override fun getWidth() = buf.width
            override fun getHeight() = buf.height

            override fun getTransformMatrix(): Matrix {
                val m = Matrix(buf.transformMatrix)
                // Scale Y by -1 around center (0.5, 0.5) in texture coords:
                // maps (x, y) → (x, 1-y), i.e. vertical flip.
                m.preScale(1f, -1f, 0.5f, 0.5f)
                return m
            }

            override fun toI420(): VideoFrame.I420Buffer? = buf.toI420()
            override fun retain() = buf.retain()
            override fun release() = buf.release()
            override fun cropAndScale(
                cx: Int, cy: Int, cw: Int, ch: Int, sw: Int, sh: Int
            ): VideoFrame.Buffer = buf.cropAndScale(cx, cy, cw, ch, sw, sh)
        }
    }

    /**
     * Convert any non-texture buffer to I420 and flip rows vertically.
     */
    private fun flipViaI420(buffer: VideoFrame.Buffer): VideoFrame.I420Buffer {
        val src = buffer.toI420()!!
        val w = src.width
        val h = src.height
        val cw = (w + 1) / 2
        val ch = (h + 1) / 2

        val dstY = ByteBuffer.allocateDirect(w * h)
        val dstU = ByteBuffer.allocateDirect(cw * ch)
        val dstV = ByteBuffer.allocateDirect(cw * ch)

        flipPlane(src.dataY, src.strideY, w, h, dstY)
        flipPlane(src.dataU, src.strideU, cw, ch, dstU)
        flipPlane(src.dataV, src.strideV, cw, ch, dstV)

        dstY.rewind(); dstU.rewind(); dstV.rewind()
        src.release()

        return JavaI420Buffer.wrap(w, h, dstY, w, dstU, cw, dstV, cw, null)
    }

    private fun flipPlane(
        src: ByteBuffer, srcStride: Int,
        width: Int, height: Int,
        dst: ByteBuffer
    ) {
        val row = ByteArray(width)
        for (i in 0 until height) {
            src.position((height - 1 - i) * srcStride)
            src.get(row, 0, width)
            dst.put(row, 0, width)
        }
    }
}

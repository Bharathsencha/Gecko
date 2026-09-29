/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.browser.adfree.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object VideoAudioMuxer {

    private const val TAG = "VideoAudioMuxer"

    /**
     * Losslessly muxes a separate video stream file and audio stream file into a single MP4 container
     * using Android's native MediaExtractor and MediaMuxer APIs.
     *
     * @param videoFile Raw downloaded video stream file (e.g. 1080p H.264 / AV1)
     * @param audioFile Raw downloaded audio stream file (e.g. AAC / M4A)
     * @param outputFile Final destination file (.mp4)
     * @return true if muxing succeeded, false otherwise
     */
    fun mux(videoFile: File, audioFile: File, outputFile: File): Boolean {
        if (!videoFile.exists() || videoFile.length() <= 0) {
            Log.e(TAG, "Video file missing or empty: ${videoFile.absolutePath}")
            return false
        }
        if (!audioFile.exists() || audioFile.length() <= 0) {
            Log.e(TAG, "Audio file missing or empty: ${audioFile.absolutePath}")
            return false
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            // 1. Locate Video Track
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            // 2. Locate Audio Track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                Log.e(TAG, "No valid video track found in ${videoFile.name}")
                return false
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.e(TAG, "No valid audio track found in ${audioFile.name}")
                return false
            }

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            // 3. Initialize MP4 MediaMuxer
            if (outputFile.exists()) outputFile.delete()
            outputFile.parentFile?.mkdirs()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxVideoBuffer = try {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (e: Exception) {
                4 * 1024 * 1024
            }
            val maxAudioBuffer = try {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (e: Exception) {
                1024 * 1024
            }
            val bufferSize = maxOf(maxVideoBuffer, maxAudioBuffer, 4 * 1024 * 1024)
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // 4. Mux Video Track Samples
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // 5. Mux Audio Track Samples
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            Log.i(TAG, "Successfully muxed ${outputFile.name} (${outputFile.length()} bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mux video and audio: ${e.message}", e)
            try { if (outputFile.exists()) outputFile.delete() } catch (ex: Exception) {}
            return false
        } finally {
            try { videoExtractor?.release() } catch (e: Exception) {}
            try { audioExtractor?.release() } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
        }
    }
}

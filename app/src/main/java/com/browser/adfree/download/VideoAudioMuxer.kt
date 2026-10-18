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
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless remuxing of separately downloaded media streams.
 *
 * Container selection follows the official AOSP MediaMuxer capability matrix:
 *
 * | Codec | MP4 | WebM | minSdk |
 * |-------|-----|------|--------|
 * | AVC (H.264) / MPEG-4 / H.263 | yes | -   | 16 |
 * | HEVC                           | yes | -   | 24 |
 * | AAC / AMR                      | yes | -   | 16 |
 * | VP8 / VP9                      | -   | yes | 21 / 24 |
 * | Vorbis / Opus                  | -   | yes | 21 / 29 |
 * | AV1| yes | -   | 34 |
 *
 * Forcing MP4 for a VP9+Opus pair therefore fails on *every* API level, which is
 * why this object picks the container from the actual codec of each track
 * instead of from the file name.
 *
 * Note: MediaMuxer cannot write MPEG-TS, so an HLS transport stream is read with
 * MediaExtractor and re-emitted as MP4 (or WebM when it carries VP9/Opus).
 */
object VideoAudioMuxer {

    private const val TAG = "VideoAudioMuxer"

    /** Target container for the remuxed output. */
    enum class Container(val extension: String, val mimeType: String) {
        MP4("mp4", "video/mp4"),
        WEBM("webm", "video/webm");

    }

    sealed class Result {
        /** Both streams were combined into a single playable file. */
        data class Success(val file: File, val container: Container) : Result()

        /**
         * No single container can hold this codec pair. Both streams were written
         * out separately and correctly named for their own codec, so the user
         * still receives playable media instead of a silent file.
         */
        data class Split(val videoFile: File?, val audioFile: File?, val reason: String) : Result()

        /** Nothing usable could be produced. */
        data class Failure(val reason: String) : Result()
    }

    private fun MediaFormat.mime(): String =
        runCatching { getString(MediaFormat.KEY_MIME) }.getOrNull().orEmpty()

    /** True when this codec may legally be carried by the given container on this OS. */
    private fun supports(container: Container, mime: String): Boolean {
        val m = mime.lowercase()
        return when (container) {
            Container.MP4 -> when {
                m == "video/avc" || m == "video/hevc" || m == "video/mpeg4" ||
                    m == "video/3gpp" || m == "video/h263" -> true
                m == "audio/mp4a-latm" || m.startsWith("audio/amr") -> true
                // AV1 in MP4 only became available in MediaMuxer on Android 14.
                m == "video/av01" -> Build.VERSION.SDK_INT >= 34
                else -> false
            }
            Container.WEBM -> when {
                m.startsWith("video/x-vnd.on2.vp8") || m.startsWith("video/x-vnd.on2.vp9") ||
                    m == "video/vp8" || m == "video/vp9" -> true
                m == "audio/opus" || m == "audio/vorbis" -> true
                else -> false
            }
        }
    }

    private fun allContainers(): List<Container> = listOf(Container.MP4, Container.WEBM)

    /** The container that can carry *both* tracks, or null when there is none. */
    private fun pickContainer(videoMime: String, audioMime: String): Container? =
        allContainers().firstOrNull { supports(it, videoMime) && supports(it, audioMime) }

    /** The container a lone track can be written to. */
    private fun nativeContainerFor(mime: String): Container =
        allContainers().firstOrNull { supports(it, mime) } ?: Container.MP4

    private fun Container.outputFormat(): Int = when (this) {
        Container.MP4 -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        Container.WEBM -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
    }

    /**
     * Muxes [videoFile] and [audioFile] into [outputFile].
     *
     * The container is derived from the real codecs of the two inputs, so a
     * VP9+Opus pair lands in WebM and an AVC+AAC pair lands in MP4. When the pair
     * has no common container the streams are written out separately and reported
     * as [Result.Split] — audio is never silently discarded.
     */
    fun mux(videoFile: File, audioFile: File, outputFile: File): Result {
        if (!videoFile.exists() || videoFile.length() <= 0) {
            return Result.Failure("Video stream file is missing or empty")
        }
        if (!audioFile.exists() || audioFile.length() <= 0) {
            return Result.Failure("Audio stream file is missing or empty")
        }

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            val ve = MediaExtractor()
            videoExtractor = ve
            try {
                ve.setDataSource(videoFile.absolutePath)
            } catch (e: Exception) {
                return Result.Failure("Video stream is not a readable container: ${e.message}")
            }

            val ae = MediaExtractor()
            audioExtractor = ae
            try {
                ae.setDataSource(audioFile.absolutePath)
            } catch (e: Exception) {
                return Result.Failure("Audio stream is not a readable container: ${e.message}")
            }

            val videoTrack = ve.findTrack { it.mime().startsWith("video/") }
                ?: return Result.Failure("No video track in ${videoFile.name}")
            val audioTrack = ae.findTrack { it.mime().startsWith("audio/") }
                ?: return Result.Failure("No audio track in ${audioFile.name}")

            val videoFormat = ve.getTrackFormat(videoTrack)
            val audioFormat = ae.getTrackFormat(audioTrack)
            val videoMime = videoFormat.mime()
            val audioMime = audioFormat.mime()

            // Android 10+ can also write Opus into MP4 (needed for AV1/H.264 + Opus).
            val opusInMp4 = pickContainer(videoMime, audioMime) == null &&
                supports(Container.MP4, videoMime) && audioMime.equals("audio/opus", true) &&
                Build.VERSION.SDK_INT >= 29
            val container = pickContainer(videoMime, audioMime)
                ?: (if (opusInMp4) Container.MP4 else null)
                ?: return writeSeparately(videoFile, audioFile, videoMime, audioMime, outputFile)

            ve.selectTrack(videoTrack)
            ae.selectTrack(audioTrack)

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val m = MediaMuxer(outputFile.absolutePath, container.outputFormat())
            muxer = m

            val muxerVideoTrack = m.addTrack(videoFormat)
            val muxerAudioTrack = m.addTrack(audioFormat)
            m.start()

            val bufferSize = maxOf(maxInputSize(videoFormat), maxInputSize(audioFormat), 4 * 1024 * 1024)
            val videoBuffer = ByteBuffer.allocateDirect(bufferSize)
            val audioBuffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var lastVideoPts = -1L
            var lastAudioPts = -1L
            var videoDone = false
            var audioDone = false
            var samplesWritten = 0

            while (!videoDone || !audioDone) {
                val videoTime = if (videoDone) Long.MAX_VALUE else ve.sampleTime
                val audioTime = if (audioDone) Long.MAX_VALUE else ae.sampleTime

                if (!videoDone && videoTime <= -1L) videoDone = true
                if (!audioDone && audioTime <= -1L) audioDone = true
                if (videoDone && audioDone) break

                if (!videoDone && (audioDone || videoTime <= audioTime)) {
                    bufferInfo.offset = 0
                    bufferInfo.size = ve.readSampleData(videoBuffer, 0)
                    if (bufferInfo.size < 0) {
                        videoDone = true
                    } else {
                        val pts = ve.sampleTime
                        bufferInfo.presentationTimeUs = if (pts <= lastVideoPts) lastVideoPts + PTS_STEP_US else pts
                        lastVideoPts = bufferInfo.presentationTimeUs
                        bufferInfo.flags = ve.sampleFlags
                        m.writeSampleData(muxerVideoTrack, videoBuffer, bufferInfo)
                        ve.advance()
                        samplesWritten++
                    }
                } else {
                    bufferInfo.offset = 0
                    bufferInfo.size = ae.readSampleData(audioBuffer, 0)
                    if (bufferInfo.size < 0) {
                        audioDone = true
                    } else {
                        val pts = ae.sampleTime
                        bufferInfo.presentationTimeUs = if (pts <= lastAudioPts) lastAudioPts + PTS_STEP_US else pts
                        lastAudioPts = bufferInfo.presentationTimeUs
                        bufferInfo.flags = ae.sampleFlags
                        m.writeSampleData(muxerAudioTrack, audioBuffer, bufferInfo)
                        ae.advance()
                        samplesWritten++
                    }
                }
            }

            if (samplesWritten == 0) {
                runCatching { m.stop() }
                runCatching { m.release() }
                muxer = null
                outputFile.delete()
                return Result.Failure("Muxer produced no samples")
            }

            try {
                m.stop()
            } catch (e: Exception) {
                Log.w(TAG, "MediaMuxer.stop() failed: ${e.message}")
                runCatching { m.release() }
                muxer = null
                outputFile.delete()
                return Result.Failure("Muxer could not finalise the file: ${e.message}")
            }
            m.release()
            muxer = null

            if (!outputFile.exists() || outputFile.length() <= 0) {
                outputFile.delete()
                return Result.Failure("Muxer produced an empty file")
            }

            Log.i(TAG, "Muxed $videoMime + $audioMime into ${outputFile.name} (${container.name})")
            return Result.Success(outputFile, container)

        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed for ${videoFile.name} + ${audioFile.name}", e)
            runCatching { muxer?.release() }
            muxer = null
            runCatching { outputFile.delete() }
            // Last resort: keep both streams as separate playable files.
            return runCatching {
                val vm = MediaExtractor().run { setDataSource(videoFile.absolutePath); val m = (0 until trackCount).map { getTrackFormat(it).mime() }.first { it.startsWith("video/") }; release(); m }
                val am = MediaExtractor().run { setDataSource(audioFile.absolutePath); val m = (0 until trackCount).map { getTrackFormat(it).mime() }.first { it.startsWith("audio/") }; release(); m }
                writeSeparately(videoFile, audioFile, vm, am, outputFile)
            }.getOrElse { Result.Failure("Muxing failed: ${e.message}") }
        } finally {
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            runCatching { muxer?.release() }
        }
    }

    /**
     * When no container can carry both streams, emit them as two individually
     * valid files rather than producing a silent video.
     */
    private fun writeSeparately(
        videoFile: File,
        audioFile: File,
        videoMime: String,
        audioMime: String,
        outputFile: File
    ): Result {
        val parent = outputFile.parentFile ?: return Result.Failure("Cannot resolve output directory")
        val base = outputFile.nameWithoutExtension
        val videoTarget = File(parent, "$base.video.${nativeContainerFor(videoMime).extension}")
        val audioTarget = File(parent, "$base.audio.${nativeContainerFor(audioMime).extension}")

        return try {
            videoFile.copyTo(videoTarget, overwrite = true)
            audioFile.copyTo(audioTarget, overwrite = true)
            Result.Split(
                videoFile = videoTarget.takeIf { it.exists() && it.length() > 0 },
                audioFile = audioTarget.takeIf { it.exists() && it.length() > 0 },
                reason = "$videoMime video and $audioMime audio cannot share one file format; " +
                    "saved as ${videoTarget.name} and ${audioTarget.name}"
            )
        } catch (e: Exception) {
            Result.Failure("Could not write streams separately: ${e.message}")
        }
    }

    /**
     * Re-wraps a single elementary stream (for example concatenated HLS
     * transport-stream segments) into a real container instead of renaming the
     * raw bytes. Picks the first container that can carry every track.
     */
    fun remux(sourceFile: File, outputFile: File, preferred: Container? = null): Result {
        if (!sourceFile.exists() || sourceFile.length() <= 0) {
            return Result.Failure("Source stream is missing or empty")
        }

        val extractor = MediaExtractor()
        val tracks: List<Pair<Int, MediaFormat>>
        try {
            extractor.setDataSource(sourceFile.absolutePath)
        } catch (e: Exception) {
            runCatching { extractor.release() }
            return Result.Failure("Stream is not a readable container: ${e.message}")
        }

        return try {
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            if (formats.isEmpty()) return Result.Failure("Stream contains no tracks")

            val candidates = if (preferred != null) listOf(preferred) else allContainers()
            val container = candidates.firstOrNull { candidate ->
                formats.all { supports(candidate, it.mime()) }
            } ?: return Result.Failure(
                "No output format supports ${formats.joinToString { it.mime() }}"
            )

            val selected = formats.withIndex().filter { supports(container, it.value.mime()) }
            if (selected.isEmpty()) return Result.Failure("No usable tracks to write")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            val m = MediaMuxer(outputFile.absolutePath, container.outputFormat())
            var samples = 0
            try {
                val mapped = selected.map { (index, format) -> m.addTrack(format) to index }
                m.start()

                val buffer = ByteBuffer.allocateDirect(
                    maxOf(selected.maxOf { maxInputSize(it.value) }, 4 * 1024 * 1024)
                )
                val bufferInfo = MediaCodec.BufferInfo()
                val lastPts = mutableMapOf<Int, Long>()

                for ((muxTrack, extractorTrack) in mapped) {
                    extractor.selectTrack(extractorTrack)
                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break
                        val pts = extractor.sampleTime
                        val previous = lastPts[muxTrack] ?: -1L
                        bufferInfo.presentationTimeUs = if (pts <= previous) previous + PTS_STEP_US else pts
                        lastPts[muxTrack] = bufferInfo.presentationTimeUs
                        bufferInfo.flags = extractor.sampleFlags
                        m.writeSampleData(muxTrack, buffer, bufferInfo)
                        extractor.advance()
                        samples++
                    }
                    extractor.unselectTrack(extractorTrack)
                }

                if (samples == 0) {
                    runCatching { m.stop() }
                    outputFile.delete()
                    return Result.Failure("Remuxer produced no samples")
                }
                try {
                    m.stop()
                } catch (e: Exception) {
                    outputFile.delete()
                    return Result.Failure("Remuxer could not finalise the file: ${e.message}")
                }
            } finally {
                runCatching { m.release() }
            }

            if (!outputFile.exists() || outputFile.length() <= 0) {
                outputFile.delete()
                return Result.Failure("Remuxer produced an empty file")
            }
            Log.i(
                TAG,
                "Remuxed ${sourceFile.name} into ${outputFile.name} (${container.name}) " +
                    "sourceTracks=[${formats.joinToString(",") { it.mime() }}] " +
                    "keptTracks=[${selected.joinToString(",") { it.value.mime() }}]"
            )
            Result.Success(outputFile, container)

        } catch (e: Exception) {
            Log.e(TAG, "Remux failed for ${sourceFile.name}", e)
            runCatching { outputFile.delete() }
            Result.Failure("Remux failed: ${e.message}")
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** A finished audio-only file and the media type it should be registered with. */
    data class AudioOutput(val file: File, val mimeType: String)

    /**
     * Writes the audio track of [sourceFile] into a file music apps recognise:
     * AAC becomes a non-fragmented .m4a, Opus becomes .opus (Ogg, API 29+),
     * MP3 is kept as is. DASH audio from YouTube is fragmented MP4 or WebM,
     * which galleries file as video and players cannot seek, hence the remux.
     *
     * [outputBase] is the target path without extension; a free name is picked.
     */
    fun remuxAudio(sourceFile: File, outputBase: File): AudioOutput? {
        if (!sourceFile.exists() || sourceFile.length() <= 0) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            val track = extractor.findTrack { it.mime().startsWith("audio/") } ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.mime().lowercase()

            val (extension, outMime, outputFormat) = when {
                mime == "audio/mp4a-latm" -> Triple("m4a", "audio/mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                mime == "audio/opus" && Build.VERSION.SDK_INT >= 29 ->
                    Triple("opus", "audio/ogg", MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
                mime == "audio/opus" || mime == "audio/vorbis" ->
                    Triple("webm", "audio/webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
                mime == "audio/mpeg" -> {
                    val target = freeFile(outputBase, "mp3")
                    sourceFile.copyTo(target, overwrite = true)
                    return AudioOutput(target, "audio/mpeg")
                }
                else -> return null
            }

            val target = freeFile(outputBase, extension)
            extractor.selectTrack(track)
            val muxer = MediaMuxer(target.absolutePath, outputFormat)
            var samples = 0
            try {
                val muxTrack = muxer.addTrack(format)
                muxer.start()
                val buffer = ByteBuffer.allocateDirect(maxOf(maxInputSize(format), 1024 * 1024))
                val info = MediaCodec.BufferInfo()
                var lastPts = -1L
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    val pts = extractor.sampleTime
                    info.presentationTimeUs = if (pts <= lastPts) lastPts + PTS_STEP_US else pts
                    lastPts = info.presentationTimeUs
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, info)
                    extractor.advance()
                    samples++
                }
                muxer.stop()
            } finally {
                runCatching { muxer.release() }
            }
            if (samples == 0 || target.length() <= 0) {
                target.delete()
                return null
            }
            Log.i(TAG, "Audio remuxed $mime into ${target.name}")
            return AudioOutput(target, outMime)
        } catch (e: Exception) {
            Log.w(TAG, "Audio remux failed for ${sourceFile.name}: ${e.message}")
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun freeFile(base: File, extension: String): File {
        val parent = base.parentFile ?: File(".")
        parent.mkdirs()
        var candidate = File(parent, "${base.name}.$extension")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(parent, "${base.name} ($counter).$extension")
            counter++
        }
        return candidate
    }

    private const val PTS_STEP_US = 500L

    private fun maxInputSize(format: MediaFormat): Int = try {
        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
    } catch (e: Exception) {
        2 * 1024 * 1024
    }

    private fun MediaExtractor.findTrack(predicate: (MediaFormat) -> Boolean): Int? {
        for (i in 0 until trackCount) {
            val format = try { getTrackFormat(i) } catch (e: Exception) { continue }
            if (predicate(format)) return i
        }
        return null
    }
}
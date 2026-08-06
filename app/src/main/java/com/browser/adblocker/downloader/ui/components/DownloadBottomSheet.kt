package com.browser.adblocker.downloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adblocker.downloader.detection.VideoStreamInfo
import com.browser.adblocker.downloader.download.M3u8Stream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadBottomSheet(
    videoTitle: String,
    detectedStreams: List<VideoStreamInfo>,
    hlsStreams: List<M3u8Stream>,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadSegmentText: String,
    onDismissRequest: () -> Unit,
    onDownloadSelected: (url: String, quality: String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1E1E2C),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFF00FFCC),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Video Detected",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = videoTitle,
                        fontSize = 14.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Divider(color = Color(0xFF2E2E3E), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            if (isDownloading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Text(
                        text = "Downloading video stream...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = downloadProgress / 100f,
                        color = Color(0xFF00FFCC),
                        trackColor = Color(0xFF2E2E3E),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$downloadProgress% $downloadSegmentText",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                }
            } else {
                Text(
                    text = "Select Quality to Download:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hlsStreams.isNotEmpty()) {
                        items(hlsStreams) { stream ->
                            QualityItem(
                                resolution = stream.resolution,
                                format = "HLS (.ts)",
                                onClick = { onDownloadSelected(stream.url, stream.resolution) }
                            )
                        }
                    } else {
                        items(detectedStreams) { stream ->
                            QualityItem(
                                resolution = stream.resolution,
                                format = stream.format,
                                onClick = { onDownloadSelected(stream.url, stream.resolution) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun QualityItem(
    resolution: String,
    format: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF252538))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resolution,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Format: $format",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = Color(0xFF00FFCC),
            modifier = Modifier.size(24.dp)
        )
    }
}

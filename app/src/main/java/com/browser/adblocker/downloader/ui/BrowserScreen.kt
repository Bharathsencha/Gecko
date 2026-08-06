package com.browser.adblocker.downloader.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adblocker.downloader.ui.components.BrowserGeckoView
import org.mozilla.geckoview.GeckoSession

@Composable
fun BrowserScreen(
    session: GeckoSession,
    urlInput: String,
    onUrlInputChanged: (String) -> Unit,
    onSearch: () -> Unit,
    adBlockCount: Int,
    detectedVideosCount: Int,
    loadingProgress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onHomeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    // Pulsing animation for the Video Download FAB
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F15))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Address Bar Area
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            ) {
                // Address Bar Container with glowing border
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xFF1B1B27))
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                colors = listOf(Color(0x8000FFCC), Color(0x33FFFFFF))
                            ),
                            RoundedCornerShape(23.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        BasicTextField(
                            value = urlInput,
                            onValueChange = onUrlInputChanged,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearch() }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (urlInput.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onUrlInputChanged("") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Compact Ad Block Shield inside the address bar (like Brave)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x26FF3B30))
                                .border(1.dp, Color(0x4DFF3B30), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFFF3B30),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = adBlockCount.toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Web Loading Progress Bar
            if (loadingProgress in 1..99) {
                LinearProgressIndicator(
                    progress = loadingProgress / 100f,
                    color = Color(0xFF00FFCC),
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // GeckoView Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                BrowserGeckoView(
                    session = session,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Premium Floating Glassmorphic Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xD91B1B27)) // Semi-transparent glass background
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(32.dp)) // Crisp white border line
                    .padding(horizontal = 8.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = onBackClick,
                    enabled = canGoBack,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.DarkGray
                    )
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }

                // Refresh Button
                IconButton(
                    onClick = { session.reload() },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }

                // Home Button (with standard browser navigation styling)
                IconButton(
                    onClick = onHomeClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF00FFCC))
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                }

                // History Clock Button
                IconButton(
                    onClick = onHistoryClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = "History")
                }

                // Forward Button
                IconButton(
                    onClick = onForwardClick,
                    enabled = canGoForward,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.DarkGray
                    )
                ) {
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Forward")
                }
            }
        }

        // Floating Action Button for Video Downloads (only visible when videos are detected)
        if (detectedVideosCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 24.dp)
            ) {
                FloatingActionButton(
                    onClick = onDownloadClick,
                    containerColor = Color(0xFF00FFCC),
                    contentColor = Color(0xFF0F0F15),
                    shape = CircleShape,
                    modifier = Modifier
                        .scale(scale)
                        .size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Video",
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Badging count of detected streams
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(20.dp)
                        .background(Color(0xFFFF3B30), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = detectedVideosCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

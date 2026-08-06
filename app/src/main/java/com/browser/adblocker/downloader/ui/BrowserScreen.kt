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

@OptIn(ExperimentalMaterial3Api::class)
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
    onDownloadClick: () -> Unit
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
            .background(Color(0xFF13131A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Address bar, search, adblock shield
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Home Button
                IconButton(
                    onClick = onHomeClick,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                }

                // Address Input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF252538))
                        .border(1.dp, Color(0xFF38385C), RoundedCornerShape(22.dp))
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
                            tint = Color.Gray,
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
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // AdBlock uBlock-style Shield
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x33FF3B30))
                        .border(1.dp, Color(0x66FF3B30), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Blocked Ads",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = adBlockCount.toString(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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

            // WebView viewport (GeckoView)
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

            // Bottom Navigation Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color(0xFF1E1E2C))
                    .padding(vertical = 8.dp)
            ) {
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
                
                IconButton(
                    onClick = { session.reload() },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
                
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
                    .padding(bottom = 80.dp, end = 24.dp)
            ) {
                FloatingActionButton(
                    onClick = onDownloadClick,
                    containerColor = Color(0xFF00FFCC),
                    contentColor = Color(0xFF13131A),
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
                        .background(Color.Red, CircleShape),
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

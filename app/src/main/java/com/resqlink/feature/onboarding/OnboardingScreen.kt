package com.resqlink.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqlink.R
import com.resqlink.core.ui.OrbitMark
import com.resqlink.core.ui.theme.Cyan
import com.resqlink.core.ui.theme.Mint

private data class OnboardingPage(val title: Int, val body: Int, val icon: ImageVector)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        OnboardingPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body, Icons.Rounded.Shield),
        OnboardingPage(R.string.onboarding_offline_title, R.string.onboarding_offline_body, Icons.Rounded.OfflineBolt),
        OnboardingPage(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body, Icons.Rounded.Lock),
    )
    var page by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Mint.copy(alpha = 0.16f), Cyan.copy(alpha = 0.05f), MaterialTheme.colorScheme.background), radius = 1100f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OrbitMark(Modifier.size(38.dp))
            Text("RESQLINK", Modifier.padding(start = 10.dp), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
            modifier = Modifier.align(Alignment.Center),
            label = "onboarding_page",
        ) { index ->
            val item = pages[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(112.dp), shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(item.icon, null, Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(34.dp))
                Text(stringResource(item.title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(item.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (index == page) 26.dp else 8.dp, 8.dp)
                            .background(if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape),
                    )
                }
            }
            Button(
                onClick = { if (page < pages.lastIndex) page++ else onComplete() },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(if (page == pages.lastIndex) R.string.finish_setup else R.string.continue_label), fontWeight = FontWeight.Bold)
            }
        }
    }
}

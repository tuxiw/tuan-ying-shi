package com.example.tuanyingshi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.tuanyingshi.R
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.ui.theme.TuanyingPink
import com.example.tuanyingshi.util.OnboardingPrefs
import kotlinx.coroutines.launch

/**
 * 新手引导：多页左右滑动的图文引导，首次启动自动展示。
 *
 * - 顶部右侧「跳过」可直接结束（但会写入已展示标记，之后不再自动弹出）；
 * - 底部圆点指示当前页，末页按钮变为「立即体验」；
 * - 期间拦截系统返回键，必须看完或主动跳过才能离开。
 */
@Composable
fun OnboardingScreen(navController: NavController) {
    OnboardingContent(
        onFinish = {
            OnboardingPrefs.markShown()
            // 首次启动（startDestination）无上一级，结束直接进首页；
            // 从设置「再看一次」进入则有上一级，返回即可。
            if (navController.previousBackStackEntry == null) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            } else {
                navController.popBackStack()
            }
        },
    )
}

@Composable
private fun OnboardingContent(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Filled.PlayCircle,
                title = "欢迎使用 团影视",
                desc = "聚合多数据源的追番看剧工具，弹幕、收藏、历史一站搞定。",
            ),
            OnboardingPage(
                icon = Icons.Filled.Home,
                title = "首页 · 发现好番",
                desc = "热播推荐、番剧分类与分类浏览，滑一滑就能找到想看的番剧和剧集。",
            ),
            OnboardingPage(
                icon = Icons.Filled.Subtitles,
                title = "看番 · 弹幕收藏",
                desc = "流畅播放、实时弹幕、一键收藏与观看历史，追番不掉队。",
            ),
            OnboardingPage(
                icon = Icons.Filled.Tune,
                title = "数据源 · 个性化",
                desc = "自由切换数据源、按需调校播放与弹幕设置，打造你的专属观影体验。",
            ),
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val isLastPage by remember { derivedStateOf { currentPage == pages.lastIndex } }
    val scope = rememberCoroutineScope()

    // 引导期间拦截返回键，必须看完或主动跳过才能离开。
    androidx.activity.compose.BackHandler(enabled = true) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 跳过
        if (!isLastPage) {
            Text(
                text = "跳过",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 品牌标识
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.splash_branding),
                contentDescription = "团影视",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(88.dp)
                    .padding(top = 16.dp, bottom = 8.dp),
            )
            Text(
                text = "团影视",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "追番看剧，从这里开始",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                OnboardingPageContent(page = pages[page])
            }

            // 页码圆点
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { index, _ ->
                    val selected = index == currentPage
                    Box(
                        modifier = Modifier
                            .size(if (selected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    TuanyingPink
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TuanyingPink),
            ) {
                Text(
                    text = if (isLastPage) "立即体验" else "下一步",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(120.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = TuanyingPink,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.desc,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val desc: String,
)

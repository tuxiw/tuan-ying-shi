package com.example.tuanyingshi.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.tuanyingshi.domain.model.Anime
import com.example.tuanyingshi.ui.components.AnimeCard
import com.example.tuanyingshi.ui.components.CenteredMessage
import com.example.tuanyingshi.ui.components.effectiveHomeColumns
import com.example.tuanyingshi.ui.components.maxContentWidth
import com.example.tuanyingshi.ui.navigation.Screen
import com.example.tuanyingshi.util.COLUMNS_AUTO_MAX
import com.example.tuanyingshi.util.sanitizeColumns

/**
 * 本地「我的收藏」：读取本机收藏表（详情页星标收藏即写入），不受后端登录影响。
 * 列表列数沿用「布局设置」中的首页列数，保证与首页观感一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalFavoritesScreen(
    navController: NavController,
    viewModel: LocalFavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val columns = effectiveHomeColumns()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("我的收藏", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        if (favorites.isEmpty()) {
            CenteredMessage(
                "还没有收藏，去番剧详情页点亮的星星吧",
                Modifier.fillMaxSize().padding(inner),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(sanitizeColumns(columns, COLUMNS_AUTO_MAX)),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxContentWidth)
                    .padding(inner),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(favorites, key = { it.detailUrl }) { item ->
                    AnimeCard(
                        anime = Anime(
                            title = item.title,
                            img = item.imgUrl,
                            detailUrl = item.detailUrl,
                        ),
                        onClick = { navController.navigate(Screen.Detail.create(item.detailUrl)) },
                    )
                }
            }
        }
    }
}

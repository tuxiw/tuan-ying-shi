package com.example.tuanyingshi.ui.personalization

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import com.example.tuanyingshi.ui.components.adaptiveMinCardWidth
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.tuanyingshi.util.BuiltInCovers
import com.example.tuanyingshi.util.SplashPrefs
import java.io.File

/**
 * 开屏封面设置页（个性化 → 开屏封面）。
 * - 开启/关闭开屏封面；
 * - 设置展示时间（3–10 秒，滑块调节）；
 * - 选择封面来源：选择图片（从手机相册选）/ 系统内置（缩略图单选）/ 自定义链接（输入 URL）。
 */

/**
 * 把用户从相册选择的图片拷贝进 App 私有存储（filesDir/splash_picked.jpg），
 * 返回绝对路径；任何进程/冷启动都可读取，避免 content:// 临时授权失效导致开屏加载不出来。
 * 拷贝失败返回 null（不更新偏好，保持在原来源）。
 */
private fun copyPickedImageToInternal(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val file = File(context.filesDir, "splash_picked.jpg")
            file.outputStream().use { output -> input.copyTo(output) }
            file.absolutePath
        }
    } catch (_: Exception) {
        null
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplashCoverSettingsScreen(navController: NavController) {
    val enabled by SplashPrefs.enabled.collectAsStateWithLifecycle()
    val durationMs by SplashPrefs.durationMs.collectAsStateWithLifecycle()
    val source by SplashPrefs.source.collectAsStateWithLifecycle()
    val pickedUri by SplashPrefs.pickedUri.collectAsStateWithLifecycle()
    val builtinId by SplashPrefs.builtinId.collectAsStateWithLifecycle()
    val url by SplashPrefs.url.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                val path = copyPickedImageToInternal(context, it)
                if (path != null) {
                    SplashPrefs.setPickedUri(path)
                    SplashPrefs.setSource(SplashPrefs.SOURCE_PICKED)
                }
            }
        },
    )

    val isPicked = source == SplashPrefs.SOURCE_PICKED
    val isBuiltIn = source == SplashPrefs.SOURCE_BUILTIN
    val isUrl = source == SplashPrefs.SOURCE_URL

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("开屏封面", color = MaterialTheme.colorScheme.onBackground) },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ===== 总开关 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开启开屏封面",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "关闭后启动直接进入主界面",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { SplashPrefs.setEnabled(it) })
            }

            if (!enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "开屏封面已关闭，启动 App 时将跳过启动图。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }

            Spacer(Modifier.height(8.dp))

            // ===== 显示时间（3–10 秒滑块） =====
            SectionTitle(icon = Icons.Filled.Schedule, title = "显示时间")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${durationMs / 1000}秒",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "3秒",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = " — ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "10秒",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Slider(
                    value = durationMs.toFloat(),
                    onValueChange = { SplashPrefs.setDurationMs(it.toLong()) },
                    valueRange = SplashPrefs.DURATION_MIN_MS.toFloat()..SplashPrefs.DURATION_MAX_MS.toFloat(),
                    steps = ((SplashPrefs.DURATION_MAX_MS - SplashPrefs.DURATION_MIN_MS) / 1000 - 1).toInt(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            // ===== 封面来源 =====
            SectionTitle(icon = Icons.Filled.Image, title = "封面来源")
            // 1) 选择图片（首选项）
            SourceOptionRow(
                selected = isPicked,
                label = "选择图片",
                desc = "从手机相册中选择一张图片作为开屏封面",
                icon = Icons.Filled.PhotoLibrary,
                onClick = {
                    SplashPrefs.setSource(SplashPrefs.SOURCE_PICKED)
                    pickLauncher?.launch("image/*")
                },
            )
            // 2) 系统内置
            SourceOptionRow(
                selected = isBuiltIn,
                label = "系统内置",
                desc = "使用 App 内置的开屏封面",
                icon = Icons.Filled.Image,
                onClick = { SplashPrefs.setSource(SplashPrefs.SOURCE_BUILTIN) },
            )
            // 3) 自定义链接
            SourceOptionRow(
                selected = isUrl,
                label = "自定义链接",
                desc = "使用网络图片链接作为开屏封面",
                icon = Icons.Filled.Link,
                onClick = { SplashPrefs.setSource(SplashPrefs.SOURCE_URL) },
            )

            Spacer(Modifier.height(12.dp))

            when {
                isPicked -> {
                    // 已选图片预览
                    if (pickedUri.isNotBlank()) {
                        val pickedModel = remember(pickedUri) {
                            SplashPrefs.resolvePickedModel(pickedUri)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = pickedModel,
                                contentDescription = "已选图片",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton(
                                text = "重新选择",
                                modifier = Modifier.weight(1f),
                                onClick = { pickLauncher?.launch("image/*") },
                            )
                            ActionButton(
                                text = "使用系统内置",
                                modifier = Modifier.weight(1f),
                                onClick = { SplashPrefs.setSource(SplashPrefs.SOURCE_BUILTIN) },
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "尚未选择图片，点击上方「选择图片」从手机相册选取。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                isBuiltIn -> {
                    // 系统内置封面缩略图单选（每项带「系统内置」标签）
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = adaptiveMinCardWidth),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(BuiltInCovers.covers) { cover ->
                            BuiltInCoverItem(
                                cover = cover,
                                selected = builtinId == cover.id,
                                onClick = { SplashPrefs.setBuiltinId(cover.id) },
                            )
                        }
                    }
                }

                isUrl -> {
                    // 自定义链接输入 + 预览
                    OutlinedTextField(
                        value = url,
                        onValueChange = { SplashPrefs.setUrl(it) },
                        label = { Text("封面链接") },
                        placeholder = { Text("https://… 支持 .png/.jpg 等图片地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (url.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "封面预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "请输入封面链接后预览",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 分组标题（图标 + 文案）。 */
@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** 通用按钮（文字胶囊）。 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 封面来源单选行。 */
@Composable
private fun SourceOptionRow(
    selected: Boolean,
    label: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 系统内置封面缩略图项（可单选，选中显示边框 + 勾选角标 + 「系统内置」标签）。 */
@Composable
private fun BuiltInCoverItem(
    cover: com.example.tuanyingshi.util.BuiltInCover,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(id = cover.drawableRes),
            contentDescription = cover.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // 「系统内置」标签
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "系统内置",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
        // 名称条
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = cover.name,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

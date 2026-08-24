package com.emberinn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** 提供商头像：优先品牌 SVG（assets/icons），无图标时用首字母圆形兜底（参照命理2 AutoAIIcon）。 */
@Composable
fun ProviderIcon(
    icon: String,
    name: String,
    modifier: Modifier = Modifier,
) {
    if (icon.isBlank()) {
        TextAvatar(name, modifier)
        return
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = EmberTheme.colors.surface2,
    ) {
        AsyncImage(
            model = "file:///android_asset/icons/$icon",
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(7.dp),
        )
    }
}

@Composable
fun TextAvatar(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(CircleShape).background(EmberTheme.colors.surfaceSink),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = EmberTheme.colors.ink,
        )
    }
}

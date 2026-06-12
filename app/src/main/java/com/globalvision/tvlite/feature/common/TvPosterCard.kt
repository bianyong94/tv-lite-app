package com.globalvision.tvlite.feature.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.globalvision.tvlite.core.model.TvPosterItem
import com.globalvision.tvlite.ui.theme.TvDivider

@Composable
fun TvPosterCard(
    item: TvPosterItem,
    width: androidx.compose.ui.unit.Dp = 240.dp, // 适当加宽 4K 屏下的默认海报宽度
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // 采用更富现代感的圆角
    val cardShape = RoundedCornerShape(14.dp)
    
    // 灵动的拟物悬浮缩放与阴影层级
    val scale by animateFloatAsState(
        targetValue = if (focused) if (compact) 1.015f else 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "tv_poster_scale",
    )

    // 严防死守颜色撞车：在这里指定绝对的卡片深暗背景，从而确保大屏白字清晰度
    val containerColor = if (focused) {
        Color(0xFF2D2D2D) // 获焦时稍稍提亮，增加厚重感
    } else {
        Color(0xFF1E1E1E) // 默认沉浸式深灰黑
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .consumeRepeatedDpadEvents()
            .width(width)
            .ensureVisibleOnFocus()
            .onFocusChanged { focused = it.isFocused }
            // 【关键修改】删除了原来的 .focusable()，规避遥控器双重焦点导致的随机跳动
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .tvFocusBorder(
                focused = focused,
                shape = cardShape,
                unfocusedWidth = 1.dp,
                unfocusedColor = TvDivider.copy(alpha = 0.15f),
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = cardShape
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 海报封面大图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (compact) 0.88f else 0.7f) // 搜索页更紧凑，避免整体过高
                    .clip(cardShape),
            ) {
                AsyncImage(
                    model = item.posterUrl.takeIf { it.isNotBlank() },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                
                // 图片底部半透明微光遮罩层，提升整体电影质感
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 240f
                            )
                        ),
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

            // 影视主标题：针对 4K 电视重塑字号
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (compact) 16.sp else 18.sp, // 搜索页用更紧凑字号
                    fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.85f)
                ),
                modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 底部备注标签及年份信息
            val subInfo = listOf(item.year, item.remark).filter { it.isNotBlank() }.joinToString(" · ")
            if (subInfo.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (compact) 2.dp else 4.dp))
                Text(
                    text = subInfo,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = if (compact) 12.sp else 14.sp, // 搜索页更紧凑
                        color = if (focused) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.padding(start = if (compact) 12.dp else 14.dp, end = if (compact) 12.dp else 14.dp, bottom = if (compact) 10.dp else 14.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))
            }
        }
    }
}

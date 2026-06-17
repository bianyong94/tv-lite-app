package com.globalvision.tvlite.feature.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.globalvision.tvlite.ui.theme.TvBackground
import com.globalvision.tvlite.ui.theme.TvBackgroundElevated
import com.globalvision.tvlite.ui.theme.TvDivider

@Composable
fun TvScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    showTitle: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0B1426),
                            Color(0xFF10203A),
                            Color(0xFF142946),
                            Color(0xFF0E1D34),
                        ),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 30.dp, vertical = 30.dp)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(
                if ((showTitle && title.isNotBlank()) || actions != null || onBack != null) 22.dp else 0.dp,
            ),
        ) {
            if ((showTitle && title.isNotBlank()) || actions != null || onBack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        Surface(
                            onClick = onBack,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = TvDivider,
                                shape = RoundedCornerShape(18.dp),
                            ),
                        ) {
                            Text(
                                text = "返回",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                    }
                    if (showTitle && title.isNotBlank()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.displaySmall,
                            )
                            Text(
                                text = "Global Vision Lounge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions?.invoke()
                    }
                }
            }
            content()
        }
    }
}

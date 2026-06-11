package com.globalvision.tvlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.globalvision.tvlite.ui.theme.GlobalVisionTvTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlobalVisionTvTheme {
                TvApp()
            }
        }
    }
}

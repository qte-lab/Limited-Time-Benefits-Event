package com.chronie.gift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.chronie.gift.ui.GiftApp
import com.chronie.gift.ui.theme.GiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GiftApp()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GiftAppPreview() {
    GiftTheme {
        GiftApp()
    }
}
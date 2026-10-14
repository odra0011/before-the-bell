package com.example.beforethebell.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beforethebell.theme.*

@Composable
fun CharacterPortraitView(
    name: String,
    avatarBase64: String?,
    size: Dp? = 56.dp,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarBase64) {
        if (!avatarBase64.isNullOrBlank()) {
            try {
                val cleanBase64 = if (avatarBase64.contains(",")) {
                    avatarBase64.substringAfter(",")
                } else {
                    avatarBase64
                }
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val shape = RoundedCornerShape(4.dp)
    val baseModifier = if (size != null) modifier.size(size) else modifier

    Box(
        modifier = baseModifier
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    listOf(MetalBorderHighlight, MetalBorder, MetalBorderHighlight)
                ),
                shape = shape
            )
            .padding(1.dp)
            .border(width = 1.dp, color = DarkSurface, shape = shape)
            .clip(shape)
            .background(DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            // Medieval heraldic monogram fallback
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "V"
            val fallbackTextSize = if (size != null) (size.value * 0.42f).sp else 36.sp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF24201A), NearBlack)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = AntiqueGold,
                    fontSize = fallbackTextSize,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

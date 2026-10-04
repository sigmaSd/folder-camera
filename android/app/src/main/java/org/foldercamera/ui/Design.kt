package org.foldercamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class Glyph { CAMERA, FOLDER, SETTINGS, SEARCH, ARROW, BACK, CHEVRON, HOME, FLASH, REFRESH, PLUS, PHOTO, CLOSE }
val Ink = Color(0xff202c2a)
val Muted = Color(0xff6f7974)
val Stone = Color(0xfff5f4ef)
val Accent = Color(0xff227466)
val Line = Color(0xffe3e7df)
val CameraColors = darkColorScheme(primary = Color(0xffb9efda), onPrimary = Color(0xff092d23), background = Color(0xff0c1210), surface = Color(0xff0c1210), onSurface = Color(0xfff4f6f1), surfaceVariant = Color(0xff212a25), onSurfaceVariant = Color(0xffaebbb2))
val AppColors = lightColorScheme(primary = Accent, onPrimary = Color.White, secondary = Accent, background = Stone, surface = Color(0xfffffff9), surfaceVariant = Color(0xffeaf0e8), onSurface = Ink, onSurfaceVariant = Muted, outline = Color(0xffb7c3b8), outlineVariant = Line)

@Composable fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier.size(22.dp), tint: Color = LocalContentColor.current, description: String? = null) {
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.7f)
            fun shape(vararg p: Float) { val path = Path().apply { moveTo(p[0], p[1]); for (i in 2 until p.size step 2) lineTo(p[i], p[i+1]) }; drawPath(path, tint, style = stroke) }
            when (glyph) {
                Glyph.FOLDER -> { shape(3f,7f,3f,19f,21f,19f,21f,7f,12f,7f,10f,4f,3f,4f,3f,7f); line(3f,9f,21f,9f) }
                Glyph.CAMERA -> { shape(3f,7f,7f,7f,9f,4f,15f,4f,17f,7f,21f,7f,21f,20f,3f,20f,3f,7f); drawCircle(tint,4f,Offset(12f,13f),style=stroke) }
                Glyph.SETTINGS -> { drawCircle(tint,7f,Offset(12f,12f),style=stroke); drawCircle(tint,2.5f,Offset(12f,12f),style=stroke); line(12f,2f,12f,5f); line(12f,19f,12f,22f); line(2f,12f,5f,12f); line(19f,12f,22f,12f); line(5f,5f,7f,7f); line(17f,17f,19f,19f); line(5f,19f,7f,17f); line(17f,7f,19f,5f) }
                Glyph.SEARCH -> { drawCircle(tint,6.5f,Offset(10f,10f),style=stroke); line(15f,15f,21f,21f) }
                Glyph.ARROW -> { line(4f,12f,20f,12f); shape(14f,6f,20f,12f,14f,18f) }
                Glyph.BACK -> { line(4f,12f,20f,12f); shape(10f,6f,4f,12f,10f,18f) }
                Glyph.CHEVRON -> shape(9f,5f,16f,12f,9f,19f)
                Glyph.HOME -> { shape(3f,11f,12f,3f,21f,11f); shape(5f,10f,5f,21f,19f,21f,19f,10f); shape(10f,21f,10f,15f,14f,15f,14f,21f) }
                Glyph.FLASH -> shape(13f,2f,5f,13f,11f,13f,10f,22f,19f,10f,13f,10f,13f,2f)
                Glyph.REFRESH -> { drawArc(tint,45f,290f,false,Offset(4f,4f),Size(16f,16f),style=stroke); shape(19f,3f,20f,9f,14f,8f) }
                Glyph.PLUS -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
                Glyph.PHOTO -> { drawRoundRect(tint,Offset(3f,3f),Size(18f,18f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(2f),style=stroke); drawCircle(tint,2f,Offset(16f,8f),style=stroke); shape(3f,18f,9f,11f,15f,18f,18f,15f,21f,18f) }
                Glyph.CLOSE -> { line(5f,5f,19f,19f); line(5f,19f,19f,5f) }
            }
        }
    }
}
@Composable fun SectionCard(title: String, icon: Glyph, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlyphIcon(icon, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

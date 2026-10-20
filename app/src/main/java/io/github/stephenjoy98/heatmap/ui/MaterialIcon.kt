package io.github.stephenjoy98.heatmap.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * One of Google's Material icons (outlined), from the vector drawables in res/drawable (ic_*.xml), so the
 * app's pictures match the rest of Android rather than using emoji.
 */
@Composable
fun MaterialIcon(@DrawableRes icon: Int, size: Int = 24, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription = null, modifier = modifier.size(size.dp), tint = tint)
}

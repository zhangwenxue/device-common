package android.boot.ecg.view

import android.boot.ecg.parser.api.ECGPoints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.Flow


@Composable
fun ECGView(
    modifier: Modifier = Modifier,
    spanCount: Int = 2,
    baseLineStable: Boolean,
    flow: Flow<ECGPoints>
) {
    val scope = rememberCoroutineScope()
    val density = Density(LocalContext.current)

}

@Composable
fun DrawScope.DrawOverlayGraph() {
    val pathBefore by remember {
        mutableStateOf(Path())
    }
    val pathAfter by remember {
        mutableStateOf(Path())
    }

    drawPath(pathBefore, Color.Gray, style = Stroke(width = 2f))
    drawPath(pathAfter,Color.LightGray)

}

fun DrawScope.drawScene() {
    val topLeft = Offset(0f, 0f)

}
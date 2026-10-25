package com.bruce.left

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ---------------------------------------------------------------- Thème */

@Composable
fun LeftTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        secondary = Color.White,
        onSecondary = Color.Black,
        primaryContainer = Color(0xFF2A2A2A),
        onPrimaryContainer = Color.White,
        secondaryContainer = Color(0xFF2A2A2A),
        onSecondaryContainer = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF0B0B0B),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFFBBBBBB),
        surfaceContainerHigh = Color(0xFF141414),
        surfaceContainerHighest = Color(0xFF1C1C1C),
        outline = Color(0x55FFFFFF),
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

/* ---------------------------------------------------------------- Fond liquide */

private fun DrawScope.blob(center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), center, radius),
        radius,
        center,
    )
}

@Composable
fun LiquidBackground() {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label = "bgA",
    )
    Canvas(Modifier.fillMaxSize().background(Color.Black)) {
        val w = size.width
        val h = size.height
        val ang = a * 2f * PI.toFloat()
        blob(Offset(w * (0.18f + 0.18f * cos(ang)), h * (0.16f + 0.08f * sin(ang))), w * 0.75f, 0.14f)
        blob(Offset(w * (0.88f + 0.10f * sin(ang)), h * (0.52f + 0.12f * cos(ang))), w * 0.80f, 0.09f)
        blob(Offset(w * (0.35f + 0.22f * sin(ang + 1f)), h * (0.92f + 0.06f * cos(ang + 2f))), w * 0.90f, 0.08f)
        blob(Offset(w * (0.60f + 0.15f * cos(2f * ang)), h * (0.30f + 0.10f * sin(2f * ang))), w * 0.40f, 0.05f)
    }
}

/* ---------------------------------------------------------------- Pression */

@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val cb by rememberUpdatedState(onClick)
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { cb() },
            )
        }
}

/* ---------------------------------------------------------------- Carte en verre */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 28.dp,
    inner: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val t = rememberInfiniteTransition(label = "sheen")
    val shift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    val base = if (onClick != null) modifier.pressable(onClick) else modifier
    Column(
        base
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.13f),
                        Color.White.copy(alpha = 0.035f),
                        Color.White.copy(alpha = 0.07f),
                    )
                )
            )
            .drawWithContent {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f,
                    )
                )
                drawContent()
                val sw = 1.dp.toPx()
                val x = size.width * (shift * 1.4f - 0.2f)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.30f),
                        ),
                        start = Offset(x - size.width, 0f),
                        end = Offset(x + size.width * 0.6f, size.height),
                    ),
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(corner.toPx() - sw / 2f),
                    style = Stroke(sw),
                )
            }
            .padding(inner),
        content = content,
    )
}

/* ---------------------------------------------------------------- Grille de points animée */

private fun DrawScope.dot(c: Offset, r: Float, color: Color, square: Boolean) {
    if (square) {
        drawRoundRect(color, Offset(c.x - r, c.y - r), Size(2f * r, 2f * r), CornerRadius(r * 0.35f))
    } else {
        drawCircle(color, r, c)
    }
}

@Composable
fun DotGrid(
    total: Int,
    elapsed: Int,
    columns: Int,
    square: Boolean,
    modifier: Modifier = Modifier,
    maxDot: Dp = 18.dp,
) {
    if (total <= 0) return
    val cols = columns.coerceIn(1, total)
    val rows = (total + cols - 1) / cols
    val appear = remember(total) { Animatable(0f) }
    LaunchedEffect(total) {
        appear.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }
    val inf = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p",
    )
    Canvas(modifier.fillMaxWidth().aspectRatio(cols.toFloat() / rows.toFloat())) {
        val cell = size.width / cols
        val r = min(cell * 0.34f, maxDot.toPx() / 2f)
        val prog = appear.value * (total + 24)
        for (i in 0 until total) {
            val a = ((prog - i) / 24f).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val center = Offset((i % cols) * cell + cell / 2f, (i / cols) * cell + cell / 2f)
            when {
                i < elapsed -> dot(center, r * a, Color.White.copy(alpha = 0.17f * a), square)
                i == elapsed -> {
                    blob(center, r * 3f, 0.45f * pulse * a)
                    dot(center, r * (0.85f + 0.25f * pulse) * a, Color.White.copy(alpha = (0.55f + 0.45f * pulse) * a), square)
                }
                else -> dot(center, r * a, Color.White.copy(alpha = 0.92f * a), square)
            }
        }
    }
}

/* ---------------------------------------------------------------- Textes */

@Composable
fun AnimatedPercent(fraction: Float, decimals: Int = 1, size: TextUnit = 30.sp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        anim.animateTo(
            fraction,
            tween(if (anim.value == 0f) 1400 else 500, easing = FastOutSlowInEasing),
        )
    }
    Text(
        String.format(Locale.FRENCH, "%.${decimals}f%%", anim.value * 100f),
        color = Color.White,
        fontSize = size,
        fontWeight = FontWeight.ExtraLight,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}

@Composable
fun Header(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 8.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.5).sp,
        )
        Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.FRENCH),
        modifier = modifier,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Fait apparaître un élément en glissant vers le haut, avec un léger décalage selon [index]. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        a.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * 70f
            val s = 0.96f + 0.04f * a.value
            scaleX = s
            scaleY = s
        }
    ) { content() }
}

/* ---------------------------------------------------------------- Boutons */

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .pressable(onClick)
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (filled) 0f else 0.28f), shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, s ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Color.White else Color.Transparent, tween(250), label = "segBg")
            val fg by animateColorAsState(if (sel) Color.Black else Color.White.copy(alpha = 0.7f), tween(250), label = "segFg")
            Box(
                Modifier
                    .clip(shape)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(s, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf("Temps", "Décomptes", "Réglages")
    val itemW = 104.dp
    val shape = RoundedCornerShape(50)
    val indicatorX by animateDpAsState(
        targetValue = itemW * selected,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "nav",
    )
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .clip(shape)
            .background(Color(0xCC0A0A0A))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.3f),
                    )
                ),
                shape,
            )
            .padding(5.dp)
    ) {
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(itemW)
                .height(44.dp)
                .clip(shape)
                .background(Color.White)
        )
        Row {
            labels.forEachIndexed { i, l ->
                val color by animateColorAsState(
                    if (i == selected) Color.Black else Color.White.copy(alpha = 0.65f),
                    tween(250),
                    label = "navC",
                )
                Box(
                    Modifier
                        .width(itemW)
                        .height(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(l, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Champs */

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = Color.White,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeftDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color.White.copy(alpha = 0.6f))
            }
        },
    ) {
        DatePicker(state = st)
    }
}

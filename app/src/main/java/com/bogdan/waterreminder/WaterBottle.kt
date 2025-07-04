package com.bogdan.waterreminder

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun WatterBottle(
    modifier: Modifier = Modifier,
    totalWaterAmount: Int,
    usedWaterAmount: Int,
    capColor: Color = Color(0xFF0065B9),
    drankWaterTrigger: Int,
    viewModel: WaterBottleViewModel
) {
    val darkTheme = isSystemInDarkTheme()

    // --------- CULORI ADAPTIVE ---------
    val glassHighlight =
        if (darkTheme) Color(0xFFB3E5FC).copy(alpha = 0.16f) else Color(0xFF64B5F6).copy(alpha = 0.16f)
    val glassInner =
        if (darkTheme) Color(0xFF01579B).copy(alpha = 0.12f) else Color(0xFFE1F5FE).copy(alpha = 0.12f)
    val glassOutline = if (darkTheme) Color(0xFF90CAF9) else Color(0xFF1565C0)
    val capMain = if (darkTheme) capColor else Color(0xFF1976D2)
    val capShadow = if (darkTheme) Color(0xFF1976D2) else Color(0xFF01579B)
    val bottomEffect =
        if (darkTheme) Color(0xFF64B5F6).copy(alpha = 0.25f) else Color(0xFF1976D2).copy(alpha = 0.19f)

    // --------- ANIMATIE UMPLERE STICLA ---------
    val targetLevel = (usedWaterAmount.toFloat() / totalWaterAmount.toFloat()).coerceIn(0f, 1f)
    val waterPercentage by animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = tween(
            durationMillis = 1500,
            easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
        ),
        label = "Water Fill Animation"
    )

    // --------- VALURI ANIMATE ---------
    val infiniteTransition = rememberInfiniteTransition(label = "waves")
    val waveShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing)
        ),
        label = "wave"
    )

    // --------- CONFIG BULE ---------
    val bubbleMinRadius = 6f
    val bubbleMaxRadius = 16f
    val bubbleMinSpeed = 18f
    val bubbleMaxSpeed = 48f

    val widthDp = 90.dp
    val heightDp = 210.dp

    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.toPx() }
    val heightPx = with(density) { heightDp.toPx() }

    // NUMĂRUL DE BULE VARIABIL ÎN FUNCȚIE DE CÂTĂ APĂ E ÎN STICLĂ
    val maxBubbles = 12
    val fillPercent = usedWaterAmount.coerceIn(0, 2000).toFloat() / 2000f
    val bubbleCount = (fillPercent * maxBubbles).toInt().coerceIn(0, maxBubbles)

    // Folosește lista de bule din ViewModel pentru persistență între taburi!
    val bubbles = viewModel.bubbles

    // --------- GESTIONARE ADAUGARE/ELIMINARE TREPTATĂ DE BULE (inițializare rapidă la creștere, eliminare marcată la scădere) ---------
    LaunchedEffect(bubbleCount, widthPx, heightPx) {
        val currentCount = bubbles.size
        val toAdd = bubbleCount - currentCount
        if (toAdd > 0) {
            // Adaugă bule noi instant (inactive la început)
            repeat(toAdd) {
                bubbles.add(
                    Bubble(
                        x = Random.nextFloat() * widthPx * 0.8f + widthPx * 0.1f,
                        y = heightPx + Random.nextFloat() * 20f,
                        radius = Random.nextFloat() * (bubbleMaxRadius - bubbleMinRadius) + bubbleMinRadius,
                        speed = Random.nextFloat() * (bubbleMaxSpeed - bubbleMinSpeed) + bubbleMinSpeed,
                        alpha = Random.nextFloat() * 0.4f + 0.2f,
                        active = false,
                        toRemove = false
                    )
                )
            }
        } else if (toAdd < 0) {
            // Marchează pentru dispariție ultimele bule (cele mai noi), treptat
            val indices = bubbles.withIndex()
                .filter { !it.value.toRemove }
                .map { it.index }
                .takeLast(-toAdd)
            for (idx in indices) {
                bubbles[idx].toRemove = true
            }
        }
    }

    // --------- ACTIVARE TREPTATĂ BULE NOI ---------
    LaunchedEffect(drankWaterTrigger, bubbles.size) {
        delay(1000L + Random.nextLong(0L, 1000L))
        for (bubble in bubbles) {
            if (!bubble.active && !bubble.toRemove) {
                bubble.active = true
                delay(100L + Random.nextLong(0L, 600L))
            }
        }
    }

    // --------- ANIMATIE FADE-OUT PENTRU BULELE CARE TREBUIE SĂ DISPARĂ ---------
    LaunchedEffect(bubbles.size) {
        while (true) {
            val disappearing = bubbles.filter { it.toRemove }
            if (disappearing.isNotEmpty()) {
                for (bubble in disappearing) {
                    bubble.alpha -= 0.04f
                }
                // elimină bulele complet fade-out
                bubbles.removeAll { it.toRemove && it.alpha <= 0f }
            }
            delay(16L)
        }
    }

    // --------- SPAWN CU DELAY DE 800MS ÎNTRE BULE ---------
    // Aceasta coroutine va verifica periodic dacă lipsesc bule (față de bubbleCount) și va pune una nouă doar la 800ms distanță de ultima spawn-uită
    LaunchedEffect(bubbleCount, widthPx, heightPx) {
        while (true) {
            // numărul de bule active (nu toRemove)
            val activeCount = bubbles.count { !it.toRemove }
            if (activeCount < bubbleCount) {
                // adaugă o bulă nouă (inactive la început)
                bubbles.add(
                    Bubble(
                        x = Random.nextFloat() * widthPx * 0.8f + widthPx * 0.1f,
                        y = heightPx + Random.nextFloat() * 20f,
                        radius = Random.nextFloat() * (bubbleMaxRadius - bubbleMinRadius) + bubbleMinRadius,
                        speed = Random.nextFloat() * (bubbleMaxSpeed - bubbleMinSpeed) + bubbleMinSpeed,
                        alpha = Random.nextFloat() * 0.4f + 0.2f,
                        active = false,
                        toRemove = false
                    )
                )
                delay(800L) // DELAY DE 800MS ÎNTRE SPAWN-URI
            } else {
                delay(50L)
            }
        }
    }

    // --------- ANIMATIE BULE ---------
    LaunchedEffect(bubbles.size, waterPercentage) {
        while (true) {
            withFrameNanos {
                val waterY = (1 - waterPercentage) * heightPx * 1.08f
                for (bubble in bubbles) {
                    if (bubble.active && !bubble.toRemove) {
                        bubble.y -= bubble.speed * (1 / 90f)
                        if (bubble.y + bubble.radius < waterY) {
                            // Când o bulă ajunge la suprafață, marcheaz-o pentru despawn (spawn se va face de coroutine-ul cu delay)
                            bubble.toRemove = true
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .width(widthDp)
            .height(heightDp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // --------- BODY ---------
            val bodyPath = Path().apply {
                moveTo(width * 0.3f, height * 0.1f)
                lineTo(width * 0.3f, height * 0.2f)
                quadraticBezierTo(
                    0f, height * 0.3f,
                    0f, height * 0.4f
                )
                lineTo(0f, height * 0.95f)
                quadraticBezierTo(
                    0f, height,
                    width * 0.05f, height
                )
                lineTo(width * 0.95f, height)
                quadraticBezierTo(
                    width, height,
                    width, height * 0.95f
                )
                lineTo(width, height * 0.4f)
                quadraticBezierTo(
                    width, height * 0.3f,
                    width * 0.7f, height * 0.2f
                )
                lineTo(width * 0.7f, height * 0.2f)
                lineTo(width * 0.7f, height * 0.1f)
                close()
            }

            // ---------- APA + BULE (totul în clipPath) ----------
            clipPath(path = bodyPath) {
                // --------- TEXTURĂ STICLĂ GOALĂ ---------
                // Amplitudinea e 0 dacă nu e apă, altfel val fix, doar shift pe orizontală
                val amplitude = if (waterPercentage == 0f) 0f else height * 0.01f
                val waterWavesYPosition = (1 - waterPercentage) * height

                // Gradient subtil translucid pe toată sticla
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = if (darkTheme)
                            listOf(
                                glassHighlight,
                                glassInner,
                                Color.Transparent
                            )
                        else
                            listOf(
                                Color(0xFFB3E5FC).copy(alpha = 0.20f),
                                Color(0xFFE1F5FE).copy(alpha = 0.17f),
                                Color.Transparent
                            ),
                        startY = 0f,
                        endY = height
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height)
                )

                // --------- APA ȘI BULELE ---------
                // Valurile merg doar pe orizontală, fără variație verticală
                val frequency = 2f

                val wavePath = Path().apply {
                    moveTo(0f, waterWavesYPosition)
                    var x = 0f
                    while (x <= width) {
                        val y = waterWavesYPosition +
                                if (waterPercentage == 0f) 0f else amplitude * sin(
                                    frequency * 2 * PI * x / width - waveShift
                                ).toFloat()
                        lineTo(x, y)
                        x += 6f
                    }
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                // Gradient apă
                val waterBrush = Brush.verticalGradient(
                    colors = if (darkTheme)
                        listOf(
                            Color(0xFF64B5F6),
                            Color(0xFF1565C0),
                            Color(0xFF01579B)
                        )
                    else
                        listOf(
                            Color(0xFFB3E5FC),
                            Color(0xFF90CAF9),
                            Color(0xFF1976D2)
                        ),
                    startY = waterWavesYPosition,
                    endY = height
                )

                // Animare umplere/scădere apă cu alpha, apă dispare lin
                drawPath(
                    path = wavePath,
                    brush = waterBrush,
                    alpha = if (waterPercentage == 0f) 0f else 0.97f
                )

                // Bule animate
                for (bubble in bubbles) {
                    if (bubble.active && bubble.y + bubble.radius > waterWavesYPosition && bubble.y < height) {
                        drawCircle(
                            color = Color.White.copy(alpha = if (darkTheme) bubble.alpha else (bubble.alpha * 0.89f + 0.09f)),
                            radius = bubble.radius,
                            center = Offset(bubble.x, bubble.y)
                        )
                    }
                }

                // --------- REFLEXII ARTISTICE/GLASS TEXTURE ---------
                // 1. Reflexie principală curbă (arc subtil, nu dreptunghi)
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        start = Offset(width * 0.12f, height * 0.15f),
                        end = Offset(width * 0.25f, height * 0.65f)
                    ),
                    startAngle = 185f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(width * 0.08f, height * 0.12f),
                    size = Size(width * 0.19f, height * 0.49f),
                    style = Stroke(width = width * 0.06f)
                )

                // 2. Reflexie laterală subtilă, verticală, cu gradient
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.095f),
                            Color.Transparent
                        )
                    ),
                    topLeft = Offset(width * 0.71f, height * 0.13f),
                    size = Size(width * 0.05f, height * 0.50f),
                    cornerRadius = CornerRadius(width * 0.025f, width * 0.025f)
                )

                // 3. Reflexii fine ca linii pe diagonală (pentru efect artistic de sticlă zgâriată/lucioasă)
                for (i in 0..3) {
                    val alphaLine = 0.08f + i * 0.03f
                    val start = Offset(width * (0.22f + i * 0.04f), height * (0.18f + i * 0.08f))
                    val end = Offset(width * (0.32f + i * 0.07f), height * (0.58f + i * 0.09f))
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0f),
                                Color.White.copy(alpha = alphaLine),
                                Color.White.copy(alpha = 0f)
                            ),
                        ),
                        start = start,
                        end = end,
                        strokeWidth = width * (0.0085f + i * 0.002f),
                        cap = StrokeCap.Round
                    )
                }
                // 4. Reflexie verticală centrală, subțire, cu gradient doar pe mijloc
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.065f),
                            Color.Transparent
                        ),
                        startY = height * 0.20f,
                        endY = height * 0.75f
                    ),
                    topLeft = Offset(width * 0.47f, height * 0.20f),
                    size = Size(width * 0.035f, height * 0.55f),
                    cornerRadius = CornerRadius(width * 0.015f, width * 0.015f)
                )

                // 5. Reflexii punctuale (ca highlights) pentru aspect lucios
                for (i in 0..2) {
                    val px = width * (0.34f + i * 0.19f)
                    val py = height * (0.21f + i * 0.19f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f - i * 0.03f),
                                Color.Transparent
                            )
                        ),
                        radius = width * (0.016f + i * 0.008f),
                        center = Offset(px, py)
                    )
                }
            }

            // --------- CONTUR STICLĂ ---------
            drawPath(
                path = bodyPath,
                color = glassOutline,
                style = Stroke(width = if (darkTheme) 5f else 4f)
            )

            // --------- CAPAC OPAC ARTISTIC CU REFLEXII ---------
            val capWidth = width * 0.55f
            val capHeight = height * 0.13f
            val capTop = Offset(width / 2 - capWidth / 2f, 0f)

            // 1. Corp capac opac (fără nicio transparență în gradient!)
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        capMain,
                        capMain,
                        capShadow,
                        capShadow
                    ),
                    center = Offset(capTop.x + capWidth * 0.62f, capTop.y + capHeight * 0.4f),
                    radius = capWidth * 0.85f
                ),
                size = Size(capWidth, capHeight),
                topLeft = capTop,
                cornerRadius = CornerRadius(45f, 45f)
            )

            // 2. Reflexie principală curbată (arc, nu dreptunghi)
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.19f),
                        Color.Transparent
                    ),
                    start = Offset(capTop.x + capWidth * 0.1f, capTop.y + capHeight * 0.17f),
                    end = Offset(capTop.x + capWidth * 0.45f, capTop.y + capHeight * 0.85f)
                ),
                startAngle = 196f,
                sweepAngle = 98f,
                useCenter = false,
                topLeft = Offset(capTop.x + capWidth * 0.08f, capTop.y + capHeight * 0.14f),
                size = Size(capWidth * 0.42f, capHeight * 0.7f),
                style = Stroke(width = capWidth * 0.09f)
            )

            // 3. Reflexie subțire verticală centrală
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    startY = capTop.y + capHeight * 0.23f,
                    endY = capTop.y + capHeight * 0.93f
                ),
                topLeft = Offset(capTop.x + capWidth * 0.495f, capTop.y + capHeight * 0.23f),
                size = Size(capWidth * 0.04f, capHeight * 0.7f),
                cornerRadius = CornerRadius(capWidth * 0.018f, capWidth * 0.018f)
            )

            // 4. Reflexii punctuale lucioase pe capac (highlights)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.13f),
                        Color.Transparent
                    )
                ),
                radius = capWidth * 0.06f,
                center = Offset(capTop.x + capWidth * 0.23f, capTop.y + capHeight * 0.28f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.09f),
                        Color.Transparent
                    )
                ),
                radius = capWidth * 0.05f,
                center = Offset(capTop.x + capWidth * 0.74f, capTop.y + capHeight * 0.41f)
            )

            // 5. Linii fine diagonale pentru efect de plastic/sticlă lucioasă
            for (i in 0..1) {
                val alphaLine = 0.08f + i * 0.04f
                val start = Offset(
                    capTop.x + capWidth * (0.22f + i * 0.13f),
                    capTop.y + capHeight * (0.20f + i * 0.18f)
                )
                val end = Offset(
                    capTop.x + capWidth * (0.39f + i * 0.16f),
                    capTop.y + capHeight * (0.78f + i * 0.12f)
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = alphaLine),
                            Color.White.copy(alpha = 0f)
                        ),
                    ),
                    start = start,
                    end = end,
                    strokeWidth = capWidth * (0.013f + i * 0.003f),
                    cap = StrokeCap.Round
                )
            }

            // --------- FUND STICLĂ TEXTURAT ---------
            // oval grosime sticlă
            drawOval(
                brush = Brush.radialGradient(
                    0.85f to bottomEffect,
                    1f to Color.Transparent
                ),
                topLeft = Offset(width * 0.10f, height * 0.96f),
                size = Size(width * 0.8f, height * 0.035f)
            )
            // linii curbe ca "striații" pe fund
            drawArc(
                color = glassHighlight,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(width * 0.17f, height * 0.97f),
                size = Size(width * 0.66f, height * 0.03f),
                style = Stroke(width = if (darkTheme) 2f else 1.5f)
            )
        }
    }
}
from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V24 reference style: fixed four-column grid with consistent spacing.
s = s.replace("columns = GridCells.Fixed(columns),", "columns = GridCells.Fixed(4),")
s = s.replace("verticalArrangement = Arrangement.spacedBy(16.dp),", "verticalArrangement = Arrangement.spacedBy(18.dp),")
s = s.replace("horizontalArrangement = Arrangement.spacedBy(12.dp)", "horizontalArrangement = Arrangement.spacedBy(14.dp)")

# Add graphics imports used by the uniform icon treatment.
anchor = "import androidx.compose.ui.graphics.graphicsLayer\n"
extra = "import androidx.compose.ui.graphics.ColorFilter\nimport androidx.compose.ui.graphics.BlendMode\nimport androidx.compose.ui.graphics.toPixelMap\n"
if "import androidx.compose.ui.graphics.ColorFilter\n" not in s:
    s = s.replace(anchor, anchor + extra)

start_marker = "@Composable\nprivate fun AppTile("
end_marker = "\n@Composable\nprivate fun AppActionDialog("
start = s.find(start_marker)
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("AppTile block not found; source changed")

new_tile = r'''@Composable
private fun AppTile(
    app: AppEntry,
    displayName: String,
    iconCache: LruCache<String, ImageBitmap>,
    iconSize: Int,
    isDragging: Boolean,
    editMode: Boolean,
    requestOptions: Boolean,
    onOptionsConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunch: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val visualSize = iconSize.coerceIn(58, 76)
    val iconPx = remember(visualSize, density.density) {
        (visualSize * density.density).toInt().coerceIn(96, 256)
    }
    val cacheKey = remember(app.packageName, iconPx) { "${app.packageName}@$iconPx" }
    var icon by remember(cacheKey) { mutableStateOf(iconCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, iconPx).asImageBitmap() }.getOrNull()
            }
            loaded?.let {
                iconCache.put(cacheKey, it)
                icon = it
            }
        }
    }

    LaunchedEffect(requestOptions) {
        if (requestOptions) {
            showMenu = true
            onOptionsConsumed()
        }
    }

    val targetScale = when {
        isDragging -> 1.07f
        editMode -> 0.98f
        else -> 1f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f),
        label = "appScale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (editMode) 0.96f else 1f,
        animationSpec = tween(160),
        label = "editAlpha"
    )
    val wiggleAngle = if (editMode && !isDragging) {
        val reverse = app.packageName.hashCode() and 1 == 0
        val transition = rememberInfiniteTransition(label = "wiggle")
        val angle by transition.animateFloat(
            initialValue = if (reverse) -1.0f else 1.0f,
            targetValue = if (reverse) 1.0f else -1.0f,
            animationSpec = infiniteRepeatable(animation = tween(125), repeatMode = RepeatMode.Reverse),
            label = "wiggleAngle"
        )
        angle
    } else 0f

    val iconBackground = remember(icon, app.packageName) {
        icon?.let { extractLauncherColor(it, app.packageName) }
            ?: fallbackLauncherColor(app.packageName)
    }
    val monochrome = remember(icon) { icon?.let(::prefersMonochromeTreatment) ?: false }
    val corner = (visualSize * 0.235f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
                rotationZ = wiggleAngle
            }
            .clickable(enabled = !isDragging) {
                if (editMode) showMenu = true else onLaunch()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(visualSize.dp),
                shape = RoundedCornerShape(corner),
                color = iconBackground,
                tonalElevation = 0.dp,
                shadowElevation = if (isDragging) 7.dp else 3.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (icon != null) {
                        Image(
                            bitmap = icon!!,
                            contentDescription = displayName,
                            modifier = Modifier.size((visualSize * 0.58f).dp),
                            colorFilter = if (monochrome) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null
                        )
                    } else {
                        Text(
                            displayName.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = (visualSize * .34f).sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                displayName,
                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    if (showMenu) {
        AppActionDialog(
            title = displayName,
            onDismiss = { showMenu = false },
            onRename = { showMenu = false; showRename = true },
            onRemove = { showMenu = false; onRemove() }
        )
    }
    if (showRename) {
        RenameAppDialog(
            currentName = displayName,
            originalName = app.label,
            onDismiss = { showRename = false },
            onSave = { name -> showRename = false; onRename(name) }
        )
    }
}

private fun fallbackLauncherColor(key: String): Color {
    val palette = listOf(
        Color(0xFF1976D2), Color(0xFF2EAD5B), Color(0xFFF39C12),
        Color(0xFF8E44AD), Color(0xFFE53935), Color(0xFF3949AB),
        Color(0xFF00A6D6), Color(0xFFEF6C00)
    )
    return palette[(key.hashCode() and Int.MAX_VALUE) % palette.size]
}

private fun extractLauncherColor(bitmap: ImageBitmap, key: String): Color {
    return runCatching {
        val pixels = bitmap.toPixelMap()
        val stepX = (bitmap.width / 12).coerceAtLeast(1)
        val stepY = (bitmap.height / 12).coerceAtLeast(1)
        var r = 0f
        var g = 0f
        var b = 0f
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = pixels[x, y]
                val max = maxOf(c.red, c.green, c.blue)
                val min = minOf(c.red, c.green, c.blue)
                val saturation = max - min
                val luminance = c.red * .299f + c.green * .587f + c.blue * .114f
                if (c.alpha > .55f && saturation > .10f && luminance in .16f..0.90f) {
                    r += c.red; g += c.green; b += c.blue; count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count < 3) fallbackLauncherColor(key)
        else {
            val rr = (r / count).coerceIn(.12f, .88f)
            val gg = (g / count).coerceIn(.12f, .88f)
            val bb = (b / count).coerceIn(.12f, .88f)
            Color(rr, gg, bb, 1f)
        }
    }.getOrElse { fallbackLauncherColor(key) }
}

private fun prefersMonochromeTreatment(bitmap: ImageBitmap): Boolean {
    return runCatching {
        val pixels = bitmap.toPixelMap()
        val points = listOf(
            0 to 0,
            (bitmap.width - 1).coerceAtLeast(0) to 0,
            0 to (bitmap.height - 1).coerceAtLeast(0),
            (bitmap.width - 1).coerceAtLeast(0) to (bitmap.height - 1).coerceAtLeast(0)
        )
        val openCorners = points.count { (x, y) ->
            val c = pixels[x, y]
            val nearWhite = c.red > .92f && c.green > .92f && c.blue > .92f
            c.alpha < .35f || nearWhite
        }
        openCorners >= 3
    }.getOrDefault(false)
}
'''

s = s[:start] + new_tile + s[end:]
p.write_text(s, encoding="utf-8")

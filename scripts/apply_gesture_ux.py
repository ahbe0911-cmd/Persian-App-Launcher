from pathlib import Path

path = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Remove the old manual gesture implementation and obsolete imports.
for obsolete in [
    "import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n",
    "import androidx.compose.foundation.gestures.awaitEachGesture\n",
    "import androidx.compose.foundation.gestures.awaitFirstDown\n",
    "import androidx.compose.ui.input.pointer.awaitFirstDown\n",
    "import androidx.compose.ui.input.pointer.pointerInput\n",
    "import androidx.compose.material.icons.rounded.MoreVert\n",
]:
    text = text.replace(obsolete, "")

# Imports required by the stable Compose reorderable implementation and animations.
imports_anchor = "import androidx.compose.foundation.ExperimentalFoundationApi\n"
required_imports = """import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
"""
if "import androidx.compose.animation.core.RepeatMode\n" not in text:
    text = text.replace(imports_anchor, required_imports + imports_anchor)

text = text.replace(
    "import androidx.compose.foundation.lazy.grid.LazyVerticalGrid\n",
    "import androidx.compose.foundation.lazy.grid.LazyVerticalGrid\nimport androidx.compose.foundation.lazy.grid.rememberLazyGridState\n"
)
text = text.replace(
    "import androidx.compose.ui.platform.LocalContext\n",
    "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalHapticFeedback\n"
)
text = text.replace(
    "import androidx.compose.ui.graphics.asImageBitmap\n",
    "import androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.graphics.graphicsLayer\n"
)
if "import androidx.compose.ui.hapticfeedback.HapticFeedbackType\n" not in text:
    text = text.replace(
        "import androidx.compose.ui.input.pointer.pointerInput\n",
        "import androidx.compose.ui.hapticfeedback.HapticFeedbackType\n"
    )
    if "import androidx.compose.ui.hapticfeedback.HapticFeedbackType\n" not in text:
        text = text.replace(
            "import androidx.compose.ui.graphics.graphicsLayer\n",
            "import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.hapticfeedback.HapticFeedbackType\n"
        )
if "import sh.calvin.reorderable.ReorderableItem\n" not in text:
    text = text.replace(
        "import java.util.Locale\n",
        "import java.util.Locale\nimport sh.calvin.reorderable.ReorderableItem\nimport sh.calvin.reorderable.longPressDraggableHandle\nimport sh.calvin.reorderable.rememberReorderableLazyGridState\n"
    )

old_grid = r'''                    if (entries.isEmpty()) {
                        EmptyPage { showAppPicker = true }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = entries,
                                key = { _, item -> item.packageName },
                                contentType = { _, _ -> "app" }
                            ) { _, item ->
                                val displayName = page.customNames[item.packageName]
                                    ?.takeIf { it.isNotBlank() } ?: item.label
                                AppTile(
                                    app = item,
                                    displayName = displayName,
                                    iconCache = iconCache,
                                    iconSize = iconSize,
                                    columns = columns,
                                    onLaunch = { launchApp(context, item) },
                                    onRename = { newName ->
                                        val clean = newName.trim()
                                        if (clean.isBlank() || clean == item.label) page.customNames.remove(item.packageName)
                                        else page.customNames[item.packageName] = clean
                                        persist()
                                    },
                                    onDragMove = { delta ->
                                        movePackageBy(page.packages, item.packageName, delta)
                                    },
                                    onDragFinished = { persist() },
                                    onRemove = {
                                        page.packages.remove(item.packageName)
                                        page.customNames.remove(item.packageName)
                                        persist()
                                    }
                                )
                            }
                        }
                    }
'''

new_grid = r'''                    if (entries.isEmpty()) {
                        EmptyPage { showAppPicker = true }
                    } else {
                        val gridState = rememberLazyGridState()
                        val haptic = LocalHapticFeedback.current
                        var editMode by remember(pageIndex) { mutableStateOf(false) }
                        var movedDuringDrag by remember(pageIndex) { mutableStateOf(false) }
                        var requestedOptionsFor by remember(pageIndex) { mutableStateOf<String?>(null) }

                        val reorderableState = rememberReorderableLazyGridState(
                            lazyGridState = gridState,
                            scrollThresholdPadding = PaddingValues(top = 72.dp, bottom = 72.dp),
                            scrollMoveMode = sh.calvin.reorderable.ScrollMoveMode.INSERT
                        ) { from, to ->
                            val fromIndex = from.index
                            val toIndex = to.index
                            if (fromIndex in page.packages.indices && toIndex in page.packages.indices && fromIndex != toIndex) {
                                movedDuringDrag = true
                                val moved = page.packages.removeAt(fromIndex)
                                page.packages.add(toIndex, moved)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = entries,
                                key = { _, item -> item.packageName },
                                contentType = { _, _ -> "app" }
                            ) { _, item ->
                                val displayName = page.customNames[item.packageName]
                                    ?.takeIf { it.isNotBlank() } ?: item.label

                                ReorderableItem(
                                    state = reorderableState,
                                    key = item.packageName
                                ) { isDragging ->
                                    AppTile(
                                        app = item,
                                        displayName = displayName,
                                        iconCache = iconCache,
                                        iconSize = iconSize,
                                        isDragging = isDragging,
                                        editMode = editMode,
                                        requestOptions = requestedOptionsFor == item.packageName,
                                        onOptionsConsumed = { requestedOptionsFor = null },
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                movedDuringDrag = false
                                                editMode = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                editMode = false
                                                if (movedDuringDrag) persist()
                                                else requestedOptionsFor = item.packageName
                                            }
                                        ),
                                        onLaunch = { launchApp(context, item) },
                                        onRename = { newName ->
                                            val clean = newName.trim()
                                            if (clean.isBlank() || clean == item.label) page.customNames.remove(item.packageName)
                                            else page.customNames[item.packageName] = clean
                                            persist()
                                        },
                                        onRemove = {
                                            page.packages.remove(item.packageName)
                                            page.customNames.remove(item.packageName)
                                            persist()
                                        }
                                    )
                                }
                            }
                        }
                    }
'''

if old_grid not in text:
    raise SystemExit("grid block not found; source changed")
text = text.replace(old_grid, new_grid, 1)

start_marker = "@Composable\nprivate fun AppTile("
end_marker = "\n@Composable\nprivate fun AppActionDialog("
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("AppTile block not found; source changed")

new_app_tile = r'''@Composable
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
    val iconPx = remember(iconSize, density.density) {
        (iconSize * density.density).toInt().coerceIn(64, 256)
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
        isDragging -> 1.08f
        editMode -> 0.97f
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
            initialValue = if (reverse) -1.1f else 1.1f,
            targetValue = if (reverse) 1.1f else -1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(125),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wiggleAngle"
        )
        angle
    } else 0f

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
                modifier = Modifier
                    .size(iconSize.dp),
                shape = RoundedCornerShape((iconSize * 0.28f).dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isDragging) 6.dp else 1.dp,
                shadowElevation = if (isDragging) 8.dp else 2.dp
            ) {
                if (icon != null) {
                    Image(
                        icon!!,
                        displayName,
                        Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape((iconSize * 0.22f).dp))
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(displayName.take(1), fontWeight = FontWeight.Bold, fontSize = (iconSize * .34f).sp)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                displayName,
                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
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
'''

text = text[:start] + new_app_tile + text[end:]

# Cache-first startup: expensive PackageManager work only on first run or explicit refresh.
old_refresh = '''    LaunchedEffect(refreshKey) {
        if (storedPages.first.isNullOrBlank() || storedPages.third) persist()
        withContext(Dispatchers.IO) { runCatching { queryLauncherApps(appContext) } }
            .onSuccess { loaded ->
                installedApps = loaded
                prefs.edit().putString(KEY_APP_CACHE, encodeAppCache(loaded)).apply()
            }
    }
'''
new_refresh = '''    LaunchedEffect(refreshKey) {
        if (storedPages.first.isNullOrBlank() || storedPages.third) persist()
        if (installedApps.isEmpty() || refreshKey > 0) {
            withContext(Dispatchers.IO) { runCatching { queryLauncherApps(appContext) } }
                .onSuccess { loaded ->
                    if (loaded.isNotEmpty()) {
                        installedApps = loaded
                        prefs.edit().putString(KEY_APP_CACHE, encodeAppCache(loaded)).apply()
                    }
                }
        }
    }
'''
if old_refresh in text:
    text = text.replace(old_refresh, new_refresh, 1)

# Fresh launch intent prevents stale cached activity names from breaking taps.
old_launch = '''private fun launchApp(context: Context, app: AppEntry) {
    val intent = if (app.activityName.isNotBlank()) Intent.makeMainActivity(ComponentName(app.packageName, app.activityName))
    else context.packageManager.getLaunchIntentForPackage(app.packageName)
    intent?.let { runCatching { context.startActivity(it) } }
}'''
new_launch = '''private fun launchApp(context: Context, app: AppEntry) {
    if (app.packageName == context.packageName) return
    val pm = context.packageManager
    val canonical = runCatching { pm.getLaunchIntentForPackage(app.packageName) }.getOrNull()?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    val fallback = if (app.activityName.isNotBlank()) {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    } else null
    val target = canonical ?: fallback ?: return
    val appContext = context.applicationContext
    runCatching { appContext.startActivity(target) }
        .recoverCatching {
            val fresh = pm.getLaunchIntentForPackage(app.packageName) ?: throw it
            fresh.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            appContext.startActivity(fresh)
        }
}'''
if old_launch in text:
    text = text.replace(old_launch, new_launch, 1)

# Header polish: lighter Material 3 elevation and proper 48dp touch targets.
old_header_surface = '''        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)
'''
new_header_surface = '''        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
'''
if old_header_surface in text:
    text = text.replace(old_header_surface, new_header_surface, 1)

text = text.replace(
    '''                FilledTonalIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(40.dp)
''',
    '''                FilledTonalIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(48.dp)
''',
    1
)
text = text.replace(
    '''                FilledTonalIconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(40.dp)
''',
    '''                FilledTonalIconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(48.dp)
''',
    1
)

text = text.replace(
    '"لمس کوتاه: اجرای برنامه • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"',
    '"لمس کوتاه: اجرا • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی روان"'
)
text = text.replace(
    '"لمس کوتاه: اجرا • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"',
    '"لمس کوتاه: اجرا • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی روان"'
)

path.write_text(text, encoding="utf-8")
print("Applied v21: reorderable grid + auto-scroll + haptics + edit wiggle + UI/performance polish")

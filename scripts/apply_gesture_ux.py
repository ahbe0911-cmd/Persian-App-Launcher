from pathlib import Path

path = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
text = path.read_text(encoding="utf-8")

text = text.replace("import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress\n", "")
text = text.replace("import androidx.compose.material.icons.rounded.MoreVert\n", "")

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
    columns: Int,
    onLaunch: () -> Unit,
    onRename: (String) -> Unit,
    onDragMove: (Int) -> Unit,
    onDragFinished: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, 144).asImageBitmap() }.getOrNull()
            }
            loaded?.let { iconCache.put(app.packageName, it); icon = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (dragging) 1.07f else 1f)
            .pointerInput(app.packageName, columns) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedAt = android.os.SystemClock.uptimeMillis()
                    var previousPosition = down.position
                    var preLongX = 0f
                    var preLongY = 0f
                    var dragX = 0f
                    var dragY = 0f
                    var longPress = false
                    val longPressMs = 380L
                    val tapSlop = 18.dp.toPx()
                    val reorderStep = 46.dp.toPx()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val amount = change.position - previousPosition
                        previousPosition = change.position
                        val elapsed = android.os.SystemClock.uptimeMillis() - startedAt

                        if (!longPress) {
                            preLongX += amount.x
                            preLongY += amount.y
                            if (elapsed >= longPressMs && kotlin.math.abs(preLongX) < tapSlop && kotlin.math.abs(preLongY) < tapSlop) {
                                longPress = true
                                dragging = true
                            }
                        } else if (change.pressed) {
                            dragX += amount.x
                            dragY += amount.y
                            change.consume()
                        }

                        if (!change.pressed) {
                            dragging = false
                            if (longPress) {
                                val ax = kotlin.math.abs(dragX)
                                val ay = kotlin.math.abs(dragY)
                                if (maxOf(ax, ay) < tapSlop) {
                                    showMenu = true
                                } else {
                                    val delta = if (ay >= ax) {
                                        val rows = kotlin.math.max(1, kotlin.math.round(ay / reorderStep).toInt())
                                        if (dragY > 0f) rows * columns else -rows * columns
                                    } else {
                                        val cells = kotlin.math.max(1, kotlin.math.round(ax / reorderStep).toInt())
                                        if (dragX > 0f) -cells else cells
                                    }
                                    onDragMove(delta)
                                    onDragFinished()
                                }
                            } else if (kotlin.math.abs(preLongX) < tapSlop && kotlin.math.abs(preLongY) < tapSlop) {
                                onLaunch()
                            }
                            break
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(iconSize.dp)
                    .shadow(if (dragging) 9.dp else 3.dp, RoundedCornerShape((iconSize * 0.28f).dp)),
                shape = RoundedCornerShape((iconSize * 0.28f).dp),
                color = MaterialTheme.colorScheme.surfaceVariant
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
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

if "import androidx.compose.foundation.gestures.awaitEachGesture\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.isSystemInDarkTheme\n",
        "import androidx.compose.foundation.isSystemInDarkTheme\nimport androidx.compose.foundation.gestures.awaitEachGesture\n"
    )
if "import androidx.compose.foundation.gestures.awaitFirstDown\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.gestures.awaitEachGesture\n",
        "import androidx.compose.foundation.gestures.awaitEachGesture\nimport androidx.compose.foundation.gestures.awaitFirstDown\n"
    )
text = text.replace("import androidx.compose.ui.input.pointer.awaitFirstDown\n", "")

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

        // Cache-first startup: avoid a full PackageManager scan on every launch.
        // The cached list renders immediately; scan only on first run or manual refresh.
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
if old_refresh not in text:
    raise SystemExit("startup refresh block not found; source changed")
text = text.replace(old_refresh, new_refresh, 1)

old_launch = '''private fun launchApp(context: Context, app: AppEntry) {
    val intent = if (app.activityName.isNotBlank()) Intent.makeMainActivity(ComponentName(app.packageName, app.activityName))
    else context.packageManager.getLaunchIntentForPackage(app.packageName)
    intent?.let { runCatching { context.startActivity(it) } }
}'''
new_launch = '''private fun launchApp(context: Context, app: AppEntry) {
    if (app.packageName == context.packageName) return
    val pm = context.packageManager

    // Launch exactly like a launcher: start the target app as its own task.
    // Prefer a fresh PackageManager intent so cached activity names cannot go stale.
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
if old_launch not in text:
    raise SystemExit("launchApp block not found; source changed")
text = text.replace(old_launch, new_launch, 1)

text = text.replace(
    '"برای جابه‌جایی، آیکون برنامه را نگه دارید و بکشید."',
    '"لمس کوتاه: اجرا • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"'
)
text = text.replace(
    '"برای جابه‌جایی، نگه دارید و بلافاصله بکشید؛ برای گزینه‌ها نگه دارید و رها کنید."',
    '"لمس کوتاه: اجرا • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"'
)
text = text.replace(
    '"لمس کوتاه: اجرای برنامه • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"',
    '"لمس کوتاه: اجرا • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"'
)

path.write_text(text, encoding="utf-8")
print("Applied cache-first startup + task-independent app launch + gesture UX")

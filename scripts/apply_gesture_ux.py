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
            .scale(if (dragging) 1.06f else 1f)
            .pointerInput(app.packageName, columns) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedAt = android.os.SystemClock.uptimeMillis()
                    var previousPosition = down.position
                    var totalX = 0f
                    var totalY = 0f
                    var dragX = 0f
                    var dragY = 0f
                    var longPress = false
                    var movedAfterLongPress = false
                    val longPressMs = 420L
                    val tapSlop = 12.dp.toPx()
                    val reorderThreshold = 16.dp.toPx()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val amount = change.position - previousPosition
                        previousPosition = change.position
                        val elapsed = android.os.SystemClock.uptimeMillis() - startedAt

                        if (!longPress) {
                            totalX += amount.x
                            totalY += amount.y
                            if (elapsed >= longPressMs && kotlin.math.abs(totalX) < tapSlop && kotlin.math.abs(totalY) < tapSlop) {
                                longPress = true
                                dragging = true
                            }
                        }

                        if (longPress && change.pressed) {
                            dragX += amount.x
                            dragY += amount.y
                            if (kotlin.math.abs(amount.x) > 0.5f || kotlin.math.abs(amount.y) > 0.5f) {
                                change.consume()
                            }

                            if (kotlin.math.abs(dragY) >= reorderThreshold) {
                                movedAfterLongPress = true
                                onDragMove(if (dragY > 0f) columns else -columns)
                                dragY = 0f
                                dragX = 0f
                            } else if (kotlin.math.abs(dragX) >= reorderThreshold) {
                                movedAfterLongPress = true
                                onDragMove(if (dragX > 0f) -1 else 1)
                                dragX = 0f
                            }
                        }

                        if (!change.pressed) {
                            dragging = false
                            if (longPress) {
                                if (movedAfterLongPress) onDragFinished() else showMenu = true
                            } else if (kotlin.math.abs(totalX) < tapSlop && kotlin.math.abs(totalY) < tapSlop) {
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

text = text.replace(
    '"برای جابه‌جایی، آیکون برنامه را نگه دارید و بکشید."',
    '"لمس کوتاه: اجرای برنامه • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"'
)
text = text.replace(
    '"برای جابه‌جایی، نگه دارید و بلافاصله بکشید؛ برای گزینه‌ها نگه دارید و رها کنید."',
    '"لمس کوتاه: اجرای برنامه • نگه‌داشتن: گزینه‌ها • نگه‌داشتن و کشیدن: جابه‌جایی"'
)

path.write_text(text, encoding="utf-8")
print("Applied reliable tap, long-press menu and easier drag gesture UX")

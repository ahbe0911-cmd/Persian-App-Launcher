package com.ahbe.mylauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "launcher_state"
private const val KEY_PAGES = "pages"
private const val KEY_COLUMNS = "columns"
private const val KEY_ICON_SIZE = "icon_size"
private const val KEY_LAST_SAVED = "last_saved_epoch"
private const val KEY_APP_CACHE = "app_cache_v1"

@Immutable
private data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String
)

@Stable
private class LauncherPage(
    title: String,
    val packages: SnapshotStateList<String>,
    val customNames: SnapshotStateMap<String, String>
) {
    var title by mutableStateOf(title)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherRoot() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherRoot() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val storedPages = remember {
        val raw = prefs.getString(KEY_PAGES, null)
        val restored = loadPages(raw)
        Triple(raw, restored, restored.isEmpty() && !raw.isNullOrBlank())
    }
    val pages = remember {
        storedPages.second.ifEmpty {
            listOf(LauncherPage("برنامه‌های من", mutableStateListOf(), mutableStateMapOf()))
        }.toMutableStateList()
    }

    var installedApps by remember { mutableStateOf(loadAppCache(prefs.getString(KEY_APP_CACHE, null))) }
    var columns by remember { mutableIntStateOf(prefs.getInt(KEY_COLUMNS, 4).coerceIn(3, 6)) }
    var iconSize by remember { mutableIntStateOf(prefs.getInt(KEY_ICON_SIZE, 64).coerceIn(48, 84)) }
    var lastSavedEpoch by remember { mutableLongStateOf(prefs.getLong(KEY_LAST_SAVED, 0L)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showNewPage by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val iconCache = remember { createIconCache() }
    val appsByPackage = remember(installedApps) { installedApps.associateBy(AppEntry::packageName) }

    fun persist() {
        val now = System.currentTimeMillis()
        lastSavedEpoch = now
        prefs.edit()
            .putString(KEY_PAGES, encodePages(pages))
            .putInt(KEY_COLUMNS, columns)
            .putInt(KEY_ICON_SIZE, iconSize)
            .putLong(KEY_LAST_SAVED, now)
            .apply()
    }

    LaunchedEffect(refreshKey) {
        if (storedPages.first.isNullOrBlank() || storedPages.third) persist()
        withContext(Dispatchers.IO) { runCatching { queryLauncherApps(appContext) } }
            .onSuccess { loaded ->
                installedApps = loaded
                prefs.edit().putString(KEY_APP_CACHE, encodeAppCache(loaded)).apply()
            }
    }

    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9F86FF),
            background = Color(0xFF101218),
            surface = Color(0xFF1A1D25),
            surfaceVariant = Color(0xFF252936)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF7047EB),
            background = Color(0xFFF8F8FC),
            surface = Color(0xFFF1ECF4),
            surfaceVariant = Color(0xFFEAE5ED)
        )
    }

    val vazir = FontFamily(Font(R.font.vazirmatn_regular, FontWeight.Normal))
    val typography = Typography(
        titleLarge = TextStyle(fontFamily = vazir, fontWeight = FontWeight.Bold, fontSize = 25.sp),
        titleMedium = TextStyle(fontFamily = vazir, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = TextStyle(fontFamily = vazir, fontSize = 15.sp),
        bodyMedium = TextStyle(fontFamily = vazir, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = vazir, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = vazir, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = vazir, fontSize = 12.sp)
    )

    MaterialTheme(colorScheme = colors, typography = typography) {
        val gradient = if (dark) {
            Brush.verticalGradient(listOf(Color(0xFF101218), Color(0xFF171A22)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFCFBFF), Color(0xFFF6F3F9)))
        }

        Scaffold(containerColor = Color.Transparent) { inner ->
            Column(Modifier.fillMaxSize().background(gradient).padding(inner)) {
                val currentPage = pages.getOrNull(pagerState.currentPage)
                DashboardHeader(
                    title = currentPage?.title ?: "لانچر من",
                    appCount = currentPage?.packages?.size ?: 0,
                    onAdd = { showAppPicker = true },
                    onSettings = { showSettings = true }
                )

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                    val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                    val packageSnapshot = page.packages.toList()
                    val entries = remember(packageSnapshot, appsByPackage) {
                        packageSnapshot.map { packageName ->
                            appsByPackage[packageName] ?: AppEntry(
                                label = packageName.substringAfterLast('.').ifBlank { "برنامه" },
                                packageName = packageName,
                                activityName = ""
                            )
                        }
                    }

                    if (entries.isEmpty()) {
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
                }

                PageIndicator(pageCount = pages.size, currentPage = pagerState.currentPage)
            }
        }

        if (showAppPicker) {
            val page = pages.getOrNull(pagerState.currentPage)
            AppPickerDialog(
                apps = installedApps,
                currentOrder = page?.packages?.toList().orEmpty(),
                iconCache = iconCache,
                onRefresh = { refreshKey++ },
                onDismiss = { showAppPicker = false },
                onApply = { selected ->
                    page?.packages?.apply { clear(); addAll(selected) }
                    page?.customNames?.keys?.toList()?.forEach { packageName ->
                        if (packageName !in selected) page.customNames.remove(packageName)
                    }
                    persist()
                    showAppPicker = false
                }
            )
        }

        if (showNewPage) {
            NewPageDialog(onDismiss = { showNewPage = false }) { title ->
                pages += LauncherPage(title, mutableStateListOf(), mutableStateMapOf())
                persist()
                showNewPage = false
                scope.launch { pagerState.animateScrollToPage(pages.lastIndex) }
            }
        }

        if (showSettings) {
            SettingsDialog(
                columns = columns,
                iconSize = iconSize,
                pageTitle = pages.getOrNull(pagerState.currentPage)?.title.orEmpty(),
                canDeletePage = pages.size > 1,
                onDismiss = { showSettings = false },
                onColumns = { value ->
                    val safe = value.coerceIn(3, 6)
                    if (safe != columns) { columns = safe; persist() }
                },
                onIconSize = { value ->
                    val safe = value.coerceIn(48, 84)
                    if (safe != iconSize) { iconSize = safe; persist() }
                },
                onRename = { title ->
                    pages.getOrNull(pagerState.currentPage)?.let { page ->
                        val clean = title.trim()
                        if (clean.isNotEmpty()) { page.title = clean; persist() }
                    }
                },
                onAddPage = { showSettings = false; showNewPage = true },
                onDeletePage = {
                    if (pages.size > 1) {
                        val index = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                        pages.removeAt(index)
                        persist()
                        scope.launch { pagerState.scrollToPage(index.coerceAtMost(pages.lastIndex)) }
                    }
                    showSettings = false
                }
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    title: String,
    appCount: Int,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val time = remember(now) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)).toPersianDigits()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        PersianDate.todayLong(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                ) {
                    Text(
                        time,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "${appCount.toString().toPersianDigits()} برنامه",
                            maxLines = 1
                        )
                    }
                )
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.Add, "افزودن برنامه", modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.Settings, "تنظیمات", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyPage(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .82f), tonalElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 30.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("این بخش خالی است", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("برنامه‌های دلخواه را به این بخش اضافه کنید", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("افزودن برنامه")
                }
            }
        }
    }
}

@Composable
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
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
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
            .scale(if (dragging) 1.08f else 1f)
            .pointerInput(app.packageName, columns) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragX = 0f
                        dragY = 0f
                        onDragFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        val threshold = 54.dp.toPx()
                        if (kotlin.math.abs(dragY) >= threshold) {
                            onDragMove(if (dragY > 0) columns else -columns)
                            dragY = 0f
                            dragX = 0f
                        } else if (kotlin.math.abs(dragX) >= threshold) {
                            onDragMove(if (dragX > 0) -1 else 1)
                            dragX = 0f
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                onClick = onLaunch,
                modifier = Modifier
                    .size(iconSize.dp)
                    .shadow(if (dragging) 10.dp else 3.dp, RoundedCornerShape((iconSize * 0.28f).dp)),
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

        Surface(
            onClick = { showMenu = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp)
                .size(24.dp)
                .shadow(1.dp, CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
            tonalElevation = 1.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MoreVert,
                    "گزینه‌های برنامه",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = .78f)
                )
            }
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

@Composable
private fun AppActionDialog(
    title: String,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "برای جابه‌جایی، آیکون برنامه را نگه دارید و بکشید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f)
                )
                ActionRow(Icons.Rounded.Edit, "تغییر نام", onRename)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ActionRow(Icons.Rounded.Delete, "حذف از این بخش", onRemove, destructive = true)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(text, modifier = Modifier.weight(1f), color = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)
        }
    }
}

@Composable
private fun RenameAppDialog(
    currentName: String,
    originalName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("تغییر نام برنامه") },
        text = {
            Column {
                OutlinedTextField(value, { value = it }, label = { Text("نام نمایشی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { value = originalName }) { Text("بازگردانی نام اصلی") }
            }
        },
        confirmButton = { Button(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                Modifier.padding(horizontal = 4.dp)
                    .size(width = if (selected) 20.dp else 7.dp, height = 7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 1f else .25f))
            )
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    currentOrder: List<String>,
    iconCache: LruCache<String, ImageBitmap>,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    val selected = remember(currentOrder) { mutableStateMapOf<String, Boolean>().apply { currentOrder.forEach { put(it, true) } } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        val normalized = query.trim()
        if (normalized.isEmpty()) apps else apps.filter { it.label.contains(normalized, true) || it.packageName.contains(normalized, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        title = { Text("فهرست برنامه‌های گوشی") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("جستجوی برنامه") }, shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("تازه‌سازی")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = selected[app.packageName] == true
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniAppIcon(app, iconCache)
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked, { value -> if (value) selected[app.packageName] = true else selected.remove(app.packageName) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val selectedSet = selected.keys
                val retained = currentOrder.filter(selectedSet::contains)
                val retainedSet = retained.toHashSet()
                val newlyAdded = apps.asSequence().map(AppEntry::packageName).filter { it in selectedSet && it !in retainedSet }.toList()
                onApply(retained + newlyAdded)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun MiniAppIcon(app: AppEntry, iconCache: LruCache<String, ImageBitmap>) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }
    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) { runCatching { loadAppIcon(context.applicationContext, app, 80).asImageBitmap() }.getOrNull() }
            loaded?.let { iconCache.put(app.packageName, it); icon = it }
        }
    }
    Surface(Modifier.size(40.dp), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        if (icon != null) Image(icon!!, app.label, Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(9.dp)))
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(app.label.take(1), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NewPageDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("بخش جدید") },
        text = { OutlinedTextField(title, { title = it }, label = { Text("نام بخش") }, singleLine = true) },
        confirmButton = { Button(enabled = title.isNotBlank(), onClick = { onCreate(title.trim()) }) { Text("ساخت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun SettingsDialog(
    columns: Int,
    iconSize: Int,
    pageTitle: String,
    canDeletePage: Boolean,
    onDismiss: () -> Unit,
    onColumns: (Int) -> Unit,
    onIconSize: (Int) -> Unit,
    onRename: (String) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit
) {
    var title by remember(pageTitle) { mutableStateOf(pageTitle) }
    var sizeValue by remember(iconSize) { mutableFloatStateOf(iconSize.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = { Text("تنظیمات بخش") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("نام این بخش") }, singleLine = true)

                SettingsCard(title = "چیدمان و ظاهر") {
                    Text("تعداد ستون‌ها", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (3..6).forEach { count ->
                            FilterChip(
                                selected = columns == count,
                                onClick = { onColumns(count) },
                                label = { Text(count.toString().toPersianDigits()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اندازه آیکون", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${sizeValue.toInt().toString().toPersianDigits()}dp", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = sizeValue,
                        onValueChange = { sizeValue = it },
                        onValueChangeFinished = { onIconSize(sizeValue.toInt()) },
                        valueRange = 48f..84f,
                        steps = 5
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("کوچک", style = MaterialTheme.typography.labelMedium)
                        Text("بزرگ", style = MaterialTheme.typography.labelMedium)
                    }
                }

                FilledTonalButton(onClick = onAddPage, modifier = Modifier.fillMaxWidth()) { Text("افزودن بخش جدید") }
                TextButton(enabled = canDeletePage, onClick = onDeletePage, modifier = Modifier.fillMaxWidth()) {
                    Text("حذف این بخش", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) onRename(title.trim())
                onIconSize(sizeValue.toInt())
                onDismiss()
            }) { Text("ذخیره") }
        }
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .75f)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun queryLauncherApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0).asSequence().mapNotNull { ri ->
        val info = ri.activityInfo ?: return@mapNotNull null
        val packageName = info.packageName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val activityName = info.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        if (packageName == context.packageName) return@mapNotNull null
        AppEntry(runCatching { ri.loadLabel(pm).toString() }.getOrDefault(packageName), packageName, activityName)
    }.distinctBy(AppEntry::packageName).sortedBy { it.label.lowercase() }.toList()
}

private fun launchApp(context: Context, app: AppEntry) {
    val intent = if (app.activityName.isNotBlank()) Intent.makeMainActivity(ComponentName(app.packageName, app.activityName))
    else context.packageManager.getLaunchIntentForPackage(app.packageName)
    intent?.let { runCatching { context.startActivity(it) } }
}

private fun loadAppIcon(context: Context, app: AppEntry, maxSizePx: Int): Bitmap {
    val pm = context.packageManager
    val drawable = if (app.activityName.isNotBlank()) {
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }
            .recoverCatching { pm.getApplicationIcon(app.packageName) }.getOrThrow()
    } else pm.getApplicationIcon(app.packageName)
    return drawableToBitmap(drawable, maxSizePx)
}

private fun createIconCache(): LruCache<String, ImageBitmap> = object : LruCache<String, ImageBitmap>(8 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        ((value.width.toLong() * value.height.toLong() * 4L) / 1024L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun loadAppCache(raw: String?): List<AppEntry> = try {
    if (raw.isNullOrBlank()) emptyList() else {
        val array = JSONArray(raw)
        buildList(array.length()) {
            repeat(array.length()) { index ->
                val obj = array.optJSONObject(index) ?: return@repeat
                val pkg = obj.optString("packageName").trim()
                if (pkg.isBlank()) return@repeat
                add(AppEntry(obj.optString("label").ifBlank { pkg.substringAfterLast('.') }, pkg, obj.optString("activityName")))
            }
        }.distinctBy(AppEntry::packageName)
    }
} catch (_: Exception) { emptyList() }

private fun encodeAppCache(apps: List<AppEntry>): String {
    val array = JSONArray()
    apps.forEach { app -> array.put(JSONObject().put("label", app.label).put("packageName", app.packageName).put("activityName", app.activityName)) }
    return array.toString()
}

private fun movePackageBy(list: SnapshotStateList<String>, packageName: String, delta: Int) {
    val from = list.indexOf(packageName)
    if (from < 0 || list.isEmpty()) return
    val to = (from + delta).coerceIn(0, list.lastIndex)
    if (from == to) return
    val item = list.removeAt(from)
    list.add(to, item)
}

private fun drawableToBitmap(drawable: Drawable, maxSizePx: Int): Bitmap {
    val iw = drawable.intrinsicWidth.coerceAtLeast(1)
    val ih = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(1f, maxSizePx.toFloat() / maxOf(iw, ih))
    val width = (iw * scale).toInt().coerceAtLeast(1)
    val height = (ih * scale).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
}

private fun loadPages(raw: String?): List<LauncherPage> = try {
    if (raw.isNullOrBlank()) emptyList() else {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val source = obj.optJSONArray("packages") ?: JSONArray()
            val unique = LinkedHashSet<String>()
            repeat(source.length()) { i -> source.optString(i).takeIf(String::isNotBlank)?.let(unique::add) }
            val aliases = mutableStateMapOf<String, String>()
            obj.optJSONObject("customNames")?.let { names ->
                names.keys().forEach { pkg ->
                    names.optString(pkg).trim().takeIf { it.isNotBlank() && pkg in unique }?.let { aliases[pkg] = it }
                }
            }
            LauncherPage(obj.optString("title", "بخش").ifBlank { "بخش" }, unique.toList().toMutableStateList(), aliases)
        }
    }
} catch (_: Exception) { emptyList() }

private fun encodePages(pages: List<LauncherPage>): String {
    val result = JSONArray()
    pages.forEach { page ->
        val packages = JSONArray()
        page.packages.distinct().forEach(packages::put)
        val aliases = JSONObject()
        page.customNames.forEach { (pkg, name) -> if (pkg in page.packages && name.isNotBlank()) aliases.put(pkg, name) }
        result.put(JSONObject().put("title", page.title.ifBlank { "بخش" }).put("packages", packages).put("customNames", aliases))
    }
    return result.toString()
}
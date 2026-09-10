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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "launcher_state"
private const val KEY_PAGES = "pages"
private const val KEY_COLUMNS = "columns"
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

    var installedApps by remember {
        mutableStateOf(loadAppCache(prefs.getString(KEY_APP_CACHE, null)))
    }
    var columns by remember { mutableIntStateOf(prefs.getInt(KEY_COLUMNS, 4).coerceIn(3, 6)) }
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
            .putLong(KEY_LAST_SAVED, now)
            .apply()
    }

    LaunchedEffect(refreshKey) {
        if (storedPages.first.isNullOrBlank() || storedPages.third) persist()
        withContext(Dispatchers.IO) {
            runCatching { queryLauncherApps(appContext) }
        }.onSuccess { loaded ->
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
            surface = Color(0xFFF0EBF3),
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
            Brush.verticalGradient(listOf(Color(0xFFFBFAFE), Color(0xFFF5F3F8)))
        }

        Scaffold(containerColor = Color.Transparent) { inner ->
            Column(Modifier.fillMaxSize().background(gradient).padding(inner)) {
                val currentPage = pages.getOrNull(pagerState.currentPage)
                Header(
                    title = currentPage?.title ?: "لانچر من",
                    appCount = currentPage?.packages?.size ?: 0,
                    lastSavedEpoch = lastSavedEpoch,
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
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = entries,
                                key = { _, item -> item.packageName },
                                contentType = { _, _ -> "app" }
                            ) { index, item ->
                                val displayName = page.customNames[item.packageName]
                                    ?.takeIf { it.isNotBlank() } ?: item.label
                                AppTile(
                                    app = item,
                                    displayName = displayName,
                                    iconCache = iconCache,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < entries.lastIndex,
                                    onLaunch = { launchApp(context, item) },
                                    onRename = { newName ->
                                        val clean = newName.trim()
                                        if (clean.isBlank() || clean == item.label) {
                                            page.customNames.remove(item.packageName)
                                        } else {
                                            page.customNames[item.packageName] = clean
                                        }
                                        persist()
                                    },
                                    onMoveUp = {
                                        movePackage(page.packages, item.packageName, -1)
                                        persist()
                                    },
                                    onMoveDown = {
                                        movePackage(page.packages, item.packageName, 1)
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
                    page?.packages?.apply {
                        clear()
                        addAll(selected)
                    }
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
                pageTitle = pages.getOrNull(pagerState.currentPage)?.title.orEmpty(),
                canDeletePage = pages.size > 1,
                onDismiss = { showSettings = false },
                onColumns = { value ->
                    val safe = value.coerceIn(3, 6)
                    if (safe != columns) {
                        columns = safe
                        persist()
                    }
                },
                onRename = { title ->
                    pages.getOrNull(pagerState.currentPage)?.let { page ->
                        val clean = title.trim()
                        if (clean.isNotEmpty()) {
                            page.title = clean
                            persist()
                        }
                    }
                },
                onAddPage = {
                    showSettings = false
                    showNewPage = true
                },
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
private fun Header(
    title: String,
    appCount: Int,
    lastSavedEpoch: Long,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(PersianDate.todayLong(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f), style = MaterialTheme.typography.bodySmall)
                if (lastSavedEpoch > 0L) {
                    Text("آخرین ذخیره: ${PersianDate.formatCompact(lastSavedEpoch)}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), style = MaterialTheme.typography.labelMedium)
                }
            }
            AssistChip(onClick = {}, label = { Text("${appCount.toString().toPersianDigits()} برنامه") })
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "افزودن برنامه") }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "تنظیمات") }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    displayName: String,
    iconCache: LruCache<String, ImageBitmap>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLaunch: () -> Unit,
    onRename: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, 128).asImageBitmap() }.getOrNull()
            }
            loaded?.let {
                iconCache.put(app.packageName, it)
                icon = it
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onLaunch, onLongClick = { showMenu = true }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(66.dp).shadow(3.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (icon != null) {
                Image(icon!!, displayName, Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(15.dp)))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }

    if (showMenu) {
        AppActionDialog(
            title = displayName,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onDismiss = { showMenu = false },
            onRename = { showMenu = false; showRename = true },
            onMoveUp = { showMenu = false; onMoveUp() },
            onMoveDown = { showMenu = false; onMoveDown() },
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionRow(Icons.Rounded.Edit, "تغییر نام", onRename)
                ActionRow(Icons.Rounded.ArrowUpward, "جابجایی به قبل", onMoveUp, canMoveUp)
                ActionRow(Icons.Rounded.ArrowDownward, "جابجایی به بعد", onMoveDown, canMoveDown)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ActionRow(Icons.Rounded.Delete, "حذف از این بخش", onRemove, true, destructive = true)
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
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(text, modifier = Modifier.weight(1f), color = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)
        }
    }
}

@Composable
private fun RenameAppDialog(currentName: String, originalName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
    pageTitle: String,
    canDeletePage: Boolean,
    onDismiss: () -> Unit,
    onColumns: (Int) -> Unit,
    onRename: (String) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit
) {
    var title by remember(pageTitle) { mutableStateOf(pageTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        title = { Text("تنظیمات بخش") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("نام این بخش") }, singleLine = true)
                Text("تعداد ستون‌ها", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (3..6).forEach { count ->
                        FilterChip(selected = columns == count, onClick = { onColumns(count) }, label = { Text(count.toString().toPersianDigits()) })
                    }
                }
                HorizontalDivider()
                FilledTonalButton(onClick = onAddPage, modifier = Modifier.fillMaxWidth()) { Text("افزودن بخش جدید") }
                TextButton(enabled = canDeletePage, onClick = onDeletePage, modifier = Modifier.fillMaxWidth()) { Text("حذف این بخش", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank()) onRename(title.trim()); onDismiss() }) { Text("ذخیره") } }
    )
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

private fun movePackage(list: SnapshotStateList<String>, packageName: String, delta: Int) {
    val from = list.indexOf(packageName)
    if (from < 0) return
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

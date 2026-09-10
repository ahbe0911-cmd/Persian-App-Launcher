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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@Immutable
private data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String
)

@Stable
private class LauncherPage(title: String, val packages: SnapshotStateList<String>) {
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
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val storedState = remember {
        val raw = prefs.getString(KEY_PAGES, null)
        val restored = loadPages(raw)
        Triple(raw, restored, restored.isEmpty() && !raw.isNullOrBlank())
    }
    val pages = remember {
        storedState.second
            .ifEmpty { listOf(LauncherPage("برنامه‌های من", mutableStateListOf())) }
            .toMutableStateList()
    }

    var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var appLoadFailed by remember { mutableStateOf(false) }
    var reloadAppsKey by remember { mutableIntStateOf(0) }
    var columns by remember { mutableIntStateOf(prefs.getInt(KEY_COLUMNS, 4).coerceIn(3, 6)) }
    var lastSavedEpoch by remember { mutableLongStateOf(prefs.getLong(KEY_LAST_SAVED, 0L)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showNewPage by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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

    LaunchedEffect(reloadAppsKey) {
        isLoadingApps = true
        appLoadFailed = false
        if (storedState.first.isNullOrBlank() || storedState.third) persist()

        val result = withContext(Dispatchers.IO) {
            runCatching { queryLauncherApps(context.applicationContext) }
        }
        result.onSuccess { loaded ->
            installedApps = loaded
            appLoadFailed = false
        }.onFailure {
            // A transient PackageManager failure must never erase the user's saved layout.
            appLoadFailed = true
        }
        isLoadingApps = false
    }

    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9EB4FF),
            background = Color(0xFF101218),
            surface = Color(0xFF191C24),
            surfaceVariant = Color(0xFF252A35)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3156D3),
            background = Color(0xFFF5F7FC),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9EDF7)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
            titleMedium = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
    ) {
        val gradient = if (dark) {
            Brush.verticalGradient(listOf(Color(0xFF101218), Color(0xFF171B24)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFF9FAFF), Color(0xFFF0F4FB)))
        }

        Scaffold(containerColor = Color.Transparent) { inner ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(gradient)
                    .padding(inner)
            ) {
                Header(
                    title = pages.getOrNull(pagerState.currentPage)?.title ?: "لانچر من",
                    lastSavedEpoch = lastSavedEpoch,
                    isLoading = isLoadingApps,
                    appLoadFailed = appLoadFailed,
                    onAdd = { if (!isLoadingApps && !appLoadFailed) showAppPicker = true },
                    onSettings = { showSettings = true }
                )

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                    val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                    val entries by remember(page, appsByPackage) {
                        derivedStateOf { page.packages.mapNotNull(appsByPackage::get) }
                    }

                    when {
                        isLoadingApps && entries.isEmpty() -> LoadingPage()
                        appLoadFailed && installedApps.isEmpty() -> AppLoadErrorPage { reloadAppsKey++ }
                        entries.isEmpty() -> EmptyPage { if (!appLoadFailed) showAppPicker = true }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(entries, key = { _, item -> item.packageName }) { index, item ->
                                AppTile(
                                    app = item,
                                    iconCache = iconCache,
                                    canMoveBack = index > 0,
                                    canMoveForward = index < entries.lastIndex,
                                    onLaunch = { launchApp(context, item) },
                                    onMoveBack = {
                                        movePackage(page.packages, item.packageName, -1)
                                        persist()
                                    },
                                    onMoveForward = {
                                        movePackage(page.packages, item.packageName, 1)
                                        persist()
                                    },
                                    onRemove = {
                                        page.packages.remove(item.packageName)
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
                onDismiss = { showAppPicker = false },
                onApply = { selected ->
                    page?.packages?.apply {
                        clear()
                        addAll(selected)
                    }
                    persist()
                    showAppPicker = false
                }
            )
        }

        if (showNewPage) {
            NewPageDialog(onDismiss = { showNewPage = false }) { title ->
                pages += LauncherPage(title, mutableStateListOf())
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
                onColumns = { newValue ->
                    val safe = newValue.coerceIn(3, 6)
                    if (safe != columns) {
                        columns = safe
                        persist()
                    }
                },
                onRename = { title ->
                    pages.getOrNull(pagerState.currentPage)?.let { page ->
                        val clean = title.trim()
                        if (clean.isNotEmpty() && page.title != clean) {
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
    lastSavedEpoch: Long,
    isLoading: Boolean,
    appLoadFailed: Boolean,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(color = Color.Transparent) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        PersianDate.todayLong(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                        fontSize = 12.sp
                    )
                    if (lastSavedEpoch > 0L) {
                        Text(
                            "آخرین ذخیره: ${PersianDate.formatCompact(lastSavedEpoch)}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f),
                            fontSize = 10.sp
                        )
                    }
                }
                FilledTonalIconButton(onClick = onAdd, enabled = !isLoading && !appLoadFailed) {
                    Icon(Icons.Rounded.Add, contentDescription = "افزودن برنامه")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات")
                }
            }
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            } else if (appLoadFailed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "خواندن فهرست برنامه‌ها ناموفق بود؛ چیدمان ذخیره‌شده دست‌نخورده باقی ماند.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingPage() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "در حال آماده‌سازی برنامه‌ها…",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
        )
    }
}

@Composable
private fun AppLoadErrorPage(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f)
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("فهرست برنامه‌ها خوانده نشد", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "چیدمان شما محفوظ است. دوباره تلاش کنید.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .75f)
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onRetry) { Text("تلاش دوباره") }
            }
        }
    }
}

@Composable
private fun EmptyPage(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
            tonalElevation = 2.dp
        ) {
            Column(
                Modifier.padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("این صفحه خالی است", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("برنامه‌های دلخواهت را به این صفحه اضافه کن", fontSize = 12.sp)
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
    iconCache: LruCache<String, ImageBitmap>,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onLaunch: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, 144).asImageBitmap() }.getOrNull()
            }
            loaded?.let {
                iconCache.put(app.packageName, it)
                icon = it
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onLaunch, onLongClick = { menu = true }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(68.dp).shadow(4.dp, RoundedCornerShape(19.dp)),
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon!!,
                        contentDescription = app.label,
                        modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(app.label.take(1), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("جابه‌جایی به قبل") },
                    enabled = canMoveBack,
                    leadingIcon = { Icon(Icons.Rounded.ArrowForward, null) },
                    onClick = { menu = false; onMoveBack() }
                )
                DropdownMenuItem(
                    text = { Text("جابه‌جایی به بعد") },
                    enabled = canMoveForward,
                    leadingIcon = { Icon(Icons.Rounded.ArrowBack, null) },
                    onClick = { menu = false; onMoveForward() }
                )
                DropdownMenuItem(
                    text = { Text("حذف از صفحه") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menu = false; onRemove() }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
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
                Modifier
                    .padding(horizontal = 4.dp)
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
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    val selected = remember(currentOrder) {
        mutableStateMapOf<String, Boolean>().apply { currentOrder.forEach { put(it, true) } }
    }
    var query by remember { mutableStateOf("") }
    val normalized = remember(query) { query.trim() }
    val filtered = remember(normalized, apps) {
        if (normalized.isEmpty()) apps
        else apps.filter {
            it.label.contains(normalized, true) || it.packageName.contains(normalized, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب برنامه‌ها") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("جستجوی نام یا شناسه برنامه") },
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = selected[app.packageName] == true
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniAppIcon(app, iconCache)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    if (value) selected[app.packageName] = true
                                    else selected.remove(app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedSet = selected.keys
                val retained = currentOrder.filter(selectedSet::contains)
                val retainedSet = retained.toHashSet()
                val newlyAdded = apps.asSequence()
                    .map(AppEntry::packageName)
                    .filter { it in selectedSet && it !in retainedSet }
                    .toList()
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
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, 144).asImageBitmap() }.getOrNull()
            }
            loaded?.let {
                iconCache.put(app.packageName, it)
                icon = it
            }
        }
    }

    Surface(
        Modifier.size(40.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (icon != null) {
            Image(
                icon!!,
                app.label,
                Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(9.dp))
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(app.label.take(1), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NewPageDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صفحه جدید") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("نام صفحه") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onCreate(title.trim()) }
            ) { Text("ساخت") }
        },
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
        title = { Text("تنظیمات صفحه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("نام این صفحه") },
                    singleLine = true
                )
                Text("تعداد ستون‌ها", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (3..6).forEach { count ->
                        FilterChip(
                            selected = columns == count,
                            onClick = { onColumns(count) },
                            label = { Text(count.toString().toPersianDigits()) }
                        )
                    }
                }
                HorizontalDivider()
                FilledTonalButton(onClick = onAddPage, modifier = Modifier.fillMaxWidth()) {
                    Text("افزودن صفحه جدید")
                }
                TextButton(
                    enabled = canDeletePage,
                    onClick = onDeletePage,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حذف این صفحه") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onRename(title.trim())
                onDismiss()
            }) { Text("ذخیره") }
        }
    )
}

private fun queryLauncherApps(context: Context): List<AppEntry> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .asSequence()
        .mapNotNull { resolveInfo ->
            val info = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = info.packageName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val activityName = info.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            AppEntry(
                label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
                    .getOrDefault(packageName),
                packageName = packageName,
                activityName = activityName
            )
        }
        .distinctBy(AppEntry::packageName)
        .sortedBy { it.label.lowercase() }
        .toList()
}

private fun launchApp(context: Context, app: AppEntry) {
    val component = ComponentName(app.packageName, app.activityName)
    val intent = Intent.makeMainActivity(component)
    runCatching { context.startActivity(intent) }
}

private fun loadAppIcon(context: Context, app: AppEntry, maxSizePx: Int): Bitmap {
    val packageManager = context.packageManager
    val component = ComponentName(app.packageName, app.activityName)
    val drawable = runCatching { packageManager.getActivityIcon(component) }
        .recoverCatching { packageManager.getApplicationIcon(app.packageName) }
        .getOrThrow()
    return drawableToBitmap(drawable, maxSizePx)
}

private fun createIconCache(): LruCache<String, ImageBitmap> =
    object : LruCache<String, ImageBitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
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
            repeat(source.length()) { i ->
                source.optString(i).takeIf(String::isNotBlank)?.let(unique::add)
            }
            LauncherPage(
                obj.optString("title", "صفحه").ifBlank { "صفحه" },
                unique.toList().toMutableStateList()
            )
        }
    }
} catch (_: Exception) {
    emptyList()
}

private fun encodePages(pages: List<LauncherPage>): String {
    val result = JSONArray()
    pages.forEach { page ->
        val packages = JSONArray()
        page.packages.distinct().forEach(packages::put)
        result.put(
            JSONObject()
                .put("title", page.title.ifBlank { "صفحه" })
                .put("packages", packages)
        )
    }
    return result.toString()
}

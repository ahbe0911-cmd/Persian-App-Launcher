package com.ahbe.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private data class AppEntry(
    val label: String,
    val packageName: String,
    val resolveInfo: ResolveInfo
)

private class LauncherPage(title: String, val packages: SnapshotStateList<String>) {
    var title by mutableStateOf(title)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyLauncherApp() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyLauncherApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("launcher_state", Context.MODE_PRIVATE) }

    val pages = remember {
        val savedPages = loadPages(prefs.getString("pages", null))
        (if (savedPages.isEmpty()) listOf(LauncherPage("برنامه‌های من", mutableStateListOf())) else savedPages)
            .toMutableStateList()
    }

    var installedApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 4).coerceIn(3, 6)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showNewPage by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val iconCache = remember { LruCache<String, ImageBitmap>(96) }

    val appsByPackage = remember(installedApps) { installedApps.associateBy { it.packageName } }

    fun persist() {
        prefs.edit()
            .putString("pages", encodePages(pages))
            .putInt("columns", columns)
            .apply()
    }

    LaunchedEffect(Unit) {
        if (prefs.getString("pages", null).isNullOrBlank()) persist()
        installedApps = withContext(Dispatchers.Default) { queryLauncherApps(context) }
        isLoadingApps = false
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF3457D5),
            background = androidx.compose.ui.graphics.Color(0xFFF6F7FB),
            surface = androidx.compose.ui.graphics.Color.White,
            onSurface = androidx.compose.ui.graphics.Color(0xFF20232A)
        ),
        typography = Typography(
            titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 23.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(shadowElevation = 0.dp, color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                pages.getOrNull(pagerState.currentPage)?.title ?: "لانچر من",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "لانچر من",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                                fontSize = 12.sp
                            )
                        }
                        Row {
                            IconButton(
                                enabled = !isLoadingApps,
                                onClick = { showAppPicker = true }
                            ) { Icon(Icons.Rounded.Add, "افزودن برنامه") }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Rounded.Settings, "تنظیمات")
                            }
                        }
                    }
                }
            }
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                if (isLoadingApps) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                        val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                        val entries = remember(page.packages.toList(), appsByPackage) {
                            page.packages.mapNotNull(appsByPackage::get)
                        }

                        if (entries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("این صفحه هنوز خالی است", fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(8.dp))
                                    FilledTonalButton(onClick = { showAppPicker = true }) {
                                        Text("افزودن برنامه")
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(entries, key = { it.packageName }) { item ->
                                    AppTile(
                                        app = item,
                                        iconCache = iconCache,
                                        onLaunch = { launchApp(context, item.packageName) },
                                        onRemove = {
                                            page.packages.remove(item.packageName)
                                            persist()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, _ ->
                        val selected = index == pagerState.currentPage
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (selected) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 1f else .25f))
                        )
                    }
                }
            }
        }

        if (showAppPicker) {
            val page = pages.getOrNull(pagerState.currentPage)
            AppPickerDialog(
                apps = installedApps,
                selected = page?.packages?.toSet().orEmpty(),
                onDismiss = { showAppPicker = false },
                onApply = { selected ->
                    page?.packages?.apply {
                        clear()
                        addAll(selected.distinct())
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
                onColumns = { newColumns ->
                    val safeColumns = newColumns.coerceIn(3, 6)
                    if (safeColumns != columns) {
                        columns = safeColumns
                        persist()
                    }
                },
                onRename = { title ->
                    pages.getOrNull(pagerState.currentPage)?.let { page ->
                        if (page.title != title) {
                            page.title = title
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
                        scope.launch {
                            pagerState.scrollToPage(index.coerceAtMost(pages.lastIndex))
                        }
                    }
                    showSettings = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    iconCache: LruCache<String, ImageBitmap>,
    onLaunch: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.Default) {
                val drawable = app.resolveInfo.loadIcon(context.packageManager)
                drawableToBitmap(drawable, 128).asImageBitmap()
            }
            iconCache.put(app.packageName, loaded)
            icon = loaded
        }
    }

    Column(
        modifier = Modifier.combinedClickable(onClick = onLaunch, onLongClick = { menu = true }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon!!,
                    contentDescription = app.label,
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(16.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("حذف از صفحه") },
                    onClick = {
                        menu = false
                        onRemove()
                    }
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    val picked = remember(selected) { mutableStateMapOf<String, Boolean>().apply { selected.forEach { put(it, true) } } }
    var query by remember { mutableStateOf("") }
    val normalizedQuery = remember(query) { query.trim() }
    val filtered = remember(normalizedQuery, apps) {
        if (normalizedQuery.isEmpty()) apps
        else apps.filter {
            it.label.contains(normalizedQuery, ignoreCase = true) ||
                it.packageName.contains(normalizedQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب برنامه‌ها") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("جستجوی برنامه") }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = picked[app.packageName] == true
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    if (value) picked[app.packageName] = true else picked.remove(app.packageName)
                                }
                            )
                            Text(app.label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedInDisplayOrder = apps.asSequence()
                    .map { it.packageName }
                    .filter { picked[it] == true }
                    .toList()
                onApply(selectedInDisplayOrder)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
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
        title = { Text("تنظیمات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("نام این صفحه") },
                    singleLine = true
                )
                Text("تعداد ستون‌ها")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (3..6).forEach { count ->
                        FilterChip(
                            selected = columns == count,
                            onClick = { onColumns(count) },
                            label = { Text(count.toString()) }
                        )
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onAddPage, modifier = Modifier.fillMaxWidth()) {
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
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map {
            AppEntry(
                label = it.loadLabel(packageManager).toString(),
                packageName = it.activityInfo.packageName,
                resolveInfo = it
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

private fun launchApp(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { context.startActivity(launchIntent) }
}

private fun drawableToBitmap(drawable: Drawable, maxSizePx: Int): Bitmap {
    val intrinsicWidth = drawable.intrinsicWidth.coerceAtLeast(1)
    val intrinsicHeight = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(1f, maxSizePx.toFloat() / maxOf(intrinsicWidth, intrinsicHeight))
    val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
}

private fun loadPages(raw: String?): List<LauncherPage> = try {
    if (raw.isNullOrBlank()) {
        emptyList()
    } else {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val packages = obj.optJSONArray("packages") ?: JSONArray()
            val uniquePackages = LinkedHashSet<String>()
            repeat(packages.length()) { packageIndex ->
                val packageName = packages.optString(packageIndex)
                if (packageName.isNotBlank()) uniquePackages += packageName
            }
            LauncherPage(
                obj.optString("title", "صفحه").ifBlank { "صفحه" },
                uniquePackages.toList().toMutableStateList()
            )
        }
    }
} catch (_: Exception) {
    emptyList()
}

private fun encodePages(pages: List<LauncherPage>): String {
    val array = JSONArray()
    pages.forEach { page ->
        val packages = JSONArray()
        page.packages.distinct().forEach { packages.put(it) }
        array.put(
            JSONObject()
                .put("title", page.title.ifBlank { "صفحه" })
                .put("packages", packages)
        )
    }
    return array.toString()
}

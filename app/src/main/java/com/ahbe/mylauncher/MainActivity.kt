package com.ahbe.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AppEntry(val label: String, val packageName: String, val resolveInfo: ResolveInfo)
private class LauncherPage(var title: String, val packages: SnapshotStateList<String>)

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
    val installedApps = remember { queryLauncherApps(context) }
    val pages = remember { loadPages(prefs.getString("pages", null)).toMutableStateList() }
    var columns by remember { mutableIntStateOf(prefs.getInt("columns", 4)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showNewPage by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    fun persist() {
        prefs.edit().putString("pages", encodePages(pages)).putInt("columns", columns).apply()
    }

    LaunchedEffect(Unit) {
        if (pages.isEmpty()) {
            pages += LauncherPage("برنامه‌های من", mutableStateListOf())
            persist()
        }
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
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(pages.getOrNull(pagerState.currentPage)?.title ?: "لانچر من", style = MaterialTheme.typography.titleLarge)
                            Text("لانچر من", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), fontSize = 12.sp)
                        }
                        Row {
                            IconButton(onClick = { showAppPicker = true }) { Icon(Icons.Rounded.Add, "افزودن برنامه") }
                            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "تنظیمات") }
                        }
                    }
                }
            }
        ) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIndex ->
                    val page = pages[pageIndex]
                    val entries = page.packages.mapNotNull { pkg -> installedApps.firstOrNull { it.packageName == pkg } }
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("این صفحه هنوز خالی است", fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = { showAppPicker = true }) { Text("افزودن برنامه") }
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
                            itemsIndexed(entries, key = { _, item -> item.packageName }) { _, item ->
                                AppTile(
                                    app = item,
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
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, _ ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(if (index == pagerState.currentPage) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (index == pagerState.currentPage) 1f else .25f))
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
                    page?.packages?.apply { clear(); addAll(selected) }
                    persist(); showAppPicker = false
                }
            )
        }

        if (showNewPage) {
            NewPageDialog(onDismiss = { showNewPage = false }) { title ->
                pages += LauncherPage(title, mutableStateListOf())
                persist(); showNewPage = false
                scope.launch { pagerState.animateScrollToPage(pages.lastIndex) }
            }
        }

        if (showSettings) {
            SettingsDialog(
                columns = columns,
                pageTitle = pages.getOrNull(pagerState.currentPage)?.title.orEmpty(),
                canDeletePage = pages.size > 1,
                onDismiss = { showSettings = false },
                onColumns = { columns = it; persist() },
                onRename = { title -> pages.getOrNull(pagerState.currentPage)?.title = title; persist() },
                onAddPage = { showSettings = false; showNewPage = true },
                onDeletePage = {
                    if (pages.size > 1) {
                        pages.removeAt(pagerState.currentPage.coerceIn(0, pages.lastIndex)); persist()
                    }
                    showSettings = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: AppEntry, onLaunch: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = Modifier.combinedClickable(onClick = onLaunch, onLongClick = { menu = true }),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            val drawable = remember(app.packageName) { app.resolveInfo.loadIcon(context.packageManager) }
            Image(
                bitmap = remember(drawable) { drawableToBitmap(drawable).asImageBitmap() },
                contentDescription = app.label,
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(16.dp))
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("حذف از صفحه") }, onClick = { menu = false; onRemove() })
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
    }
}

@Composable
private fun AppPickerDialog(apps: List<AppEntry>, selected: Set<String>, onDismiss: () -> Unit, onApply: (List<String>) -> Unit) {
    val picked = remember { selected.toMutableStateList() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب برنامه‌ها") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("جستجوی برنامه") })
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = app.packageName in picked, onCheckedChange = { checked -> if (checked) { if (app.packageName !in picked) picked += app.packageName } else picked.remove(app.packageName) })
                            Text(app.label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(picked.toList()) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun NewPageDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("صفحه جدید") }, text = {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("نام صفحه") }, singleLine = true)
    }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title.trim()) }) { Text("ساخت") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun SettingsDialog(columns: Int, pageTitle: String, canDeletePage: Boolean, onDismiss: () -> Unit, onColumns: (Int) -> Unit, onRename: (String) -> Unit, onAddPage: () -> Unit, onDeletePage: () -> Unit) {
    var title by remember(pageTitle) { mutableStateOf(pageTitle) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تنظیمات") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("نام این صفحه") }, singleLine = true)
            Text("تعداد ستون‌ها")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (3..6).forEach { count -> FilterChip(selected = columns == count, onClick = { onColumns(count) }, label = { Text(count.toString()) }) }
            }
            HorizontalDivider()
            TextButton(onClick = onAddPage, modifier = Modifier.fillMaxWidth()) { Text("افزودن صفحه جدید") }
            TextButton(enabled = canDeletePage, onClick = onDeletePage, modifier = Modifier.fillMaxWidth()) { Text("حذف این صفحه") }
        }
    }, confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onRename(title.trim()); onDismiss() }) { Text("ذخیره") } })
}

private fun queryLauncherApps(context: Context): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { AppEntry(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName, it) }
        .sortedBy { it.label.lowercase() }
}

private fun launchApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
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
            val packages = obj.optJSONArray("packages") ?: JSONArray()
            LauncherPage(obj.optString("title", "صفحه"), MutableList(packages.length()) { packages.getString(it) }.toMutableStateList())
        }
    }
} catch (_: Exception) { emptyList() }

private fun encodePages(pages: List<LauncherPage>): String {
    val array = JSONArray()
    pages.forEach { page ->
        val packages = JSONArray(); page.packages.forEach { packages.put(it) }
        array.put(JSONObject().put("title", page.title).put("packages", packages))
    }
    return array.toString()
}

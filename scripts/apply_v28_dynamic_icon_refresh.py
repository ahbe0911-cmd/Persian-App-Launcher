from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

old_activity = '''class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherRoot() }
    }
}'''
new_activity = '''class MainActivity : ComponentActivity() {
    private var iconRefreshGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherRoot(iconRefreshGeneration) }
    }

    override fun onResume() {
        super.onResume()
        // Apps such as Telegram can switch launcher aliases/icons at runtime.
        // Bumping this state forces every visible tile to resolve the active
        // launcher activity and its current icon again when we return here.
        iconRefreshGeneration++
    }
}'''
if old_activity not in s:
    raise SystemExit("MainActivity anchor not found")
s = s.replace(old_activity, new_activity, 1)

s = s.replace(
    "private fun LauncherRoot() {",
    "private fun LauncherRoot(iconRefreshGeneration: Int) {",
    1,
)

# Pass the generation through both the dashboard tile and app picker icon paths.
s = s.replace(
    "                                    iconCache = iconCache,\n                                    iconSize = iconSize,",
    "                                    iconCache = iconCache,\n                                    iconRefreshGeneration = iconRefreshGeneration,\n                                    iconSize = iconSize,",
    1,
)
s = s.replace(
    "                iconCache = iconCache,\n                onRefresh = { refreshKey++ },",
    "                iconCache = iconCache,\n                iconRefreshGeneration = iconRefreshGeneration,\n                onRefresh = { refreshKey++ },",
    1,
)

s = s.replace(
    "    iconCache: LruCache<String, ImageBitmap>,\n    iconSize: Int,",
    "    iconCache: LruCache<String, ImageBitmap>,\n    iconRefreshGeneration: Int,\n    iconSize: Int,",
    1,
)

old_tile_state = '''    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, 144).asImageBitmap() }.getOrNull()
            }
            loaded?.let { iconCache.put(app.packageName, it); icon = it }
        }
    }'''
new_tile_state = '''    val context = LocalContext.current
    val iconCacheKey = remember(app.packageName, app.activityName, iconRefreshGeneration) {
        "${app.packageName}|${app.activityName}|$iconRefreshGeneration|144"
    }
    var icon by remember(iconCacheKey) { mutableStateOf(iconCache.get(iconCacheKey)) }

    LaunchedEffect(iconCacheKey) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadAppIcon(context.applicationContext, app, 144).asImageBitmap() }.getOrNull()
            }
            loaded?.let { iconCache.put(iconCacheKey, it); icon = it }
        }
    }'''
if old_tile_state not in s:
    raise SystemExit("AppTile icon anchor not found")
s = s.replace(old_tile_state, new_tile_state, 1)

s = s.replace(
    "    iconCache: LruCache<String, ImageBitmap>,\n    onRefresh: () -> Unit,",
    "    iconCache: LruCache<String, ImageBitmap>,\n    iconRefreshGeneration: Int,\n    onRefresh: () -> Unit,",
    1,
)
s = s.replace(
    "                            MiniAppIcon(app, iconCache)",
    "                            MiniAppIcon(app, iconCache, iconRefreshGeneration)",
    1,
)
s = s.replace(
    "private fun MiniAppIcon(app: AppEntry, iconCache: LruCache<String, ImageBitmap>) {",
    "private fun MiniAppIcon(app: AppEntry, iconCache: LruCache<String, ImageBitmap>, iconRefreshGeneration: Int) {",
    1,
)

old_mini_state = '''    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf(iconCache.get(app.packageName)) }
    LaunchedEffect(app.packageName) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) { runCatching { loadAppIcon(context.applicationContext, app, 80).asImageBitmap() }.getOrNull() }
            loaded?.let { iconCache.put(app.packageName, it); icon = it }
        }
    }'''
new_mini_state = '''    val context = LocalContext.current
    val iconCacheKey = remember(app.packageName, app.activityName, iconRefreshGeneration) {
        "${app.packageName}|${app.activityName}|$iconRefreshGeneration|80"
    }
    var icon by remember(iconCacheKey) { mutableStateOf(iconCache.get(iconCacheKey)) }
    LaunchedEffect(iconCacheKey) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) { runCatching { loadAppIcon(context.applicationContext, app, 80).asImageBitmap() }.getOrNull() }
            loaded?.let { iconCache.put(iconCacheKey, it); icon = it }
        }
    }'''
if old_mini_state not in s:
    raise SystemExit("MiniAppIcon anchor not found")
s = s.replace(old_mini_state, new_mini_state, 1)

p.write_text(s, encoding="utf-8")

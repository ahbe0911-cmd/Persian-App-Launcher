from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# Network imports for the tiny daily quote feed.
if "import java.net.HttpURLConnection\n" not in s:
    s = s.replace(
        "import java.text.SimpleDateFormat\n",
        "import java.net.HttpURLConnection\nimport java.net.URL\nimport java.text.SimpleDateFormat\n",
        1,
    )

# Persistent daily quote cache. The launcher always renders the cached value first,
# so network access never blocks startup.
anchor = 'private const val KEY_APP_CACHE = "app_cache_v1"\n'
extra = '''private const val KEY_DAILY_QUOTE = "daily_quote_v1"\nprivate const val KEY_DAILY_QUOTE_DATE = "daily_quote_date_v1"\nprivate const val DAILY_QUOTES_URL = "https://raw.githubusercontent.com/ahbe0911-cmd/Persian-App-Launcher/v26-from-v23/daily-quotes.json"\n'''
if extra not in s:
    if anchor not in s:
        raise SystemExit("preferences constants anchor not found")
    s = s.replace(anchor, anchor + extra, 1)

# Quote state lives beside the existing launcher UI state.
anchor = '    var refreshKey by remember { mutableIntStateOf(0) }\n'
extra = '''    var dailyQuote by remember {\n        mutableStateOf(prefs.getString(KEY_DAILY_QUOTE, null) ?: "هر روز یک فرصت تازه است.")\n    }\n'''
if extra not in s:
    if anchor not in s:
        raise SystemExit("refreshKey state anchor not found")
    s = s.replace(anchor, anchor + extra, 1)

# Fetch at most once per successful day. Failure keeps the previous cached quote
# and does not affect launcher startup or app launching.
refresh_end = '''    LaunchedEffect(refreshKey) {\n        if (storedPages.first.isNullOrBlank() || storedPages.third) persist()\n        if (installedApps.isEmpty() || refreshKey > 0) {\n            withContext(Dispatchers.IO) { runCatching { queryLauncherApps(appContext) } }\n                .onSuccess { loaded ->\n                    if (loaded.isNotEmpty()) {\n                        installedApps = loaded\n                        prefs.edit().putString(KEY_APP_CACHE, encodeAppCache(loaded)).apply()\n                    }\n                }\n        }\n    }\n'''
quote_effect = '''\n    LaunchedEffect(Unit) {\n        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())\n        val cachedDate = prefs.getString(KEY_DAILY_QUOTE_DATE, null)\n        if (cachedDate != todayKey) {\n            val fresh = withContext(Dispatchers.IO) { fetchDailyQuote(todayKey) }\n            if (!fresh.isNullOrBlank()) {\n                dailyQuote = fresh\n                prefs.edit()\n                    .putString(KEY_DAILY_QUOTE, fresh)\n                    .putString(KEY_DAILY_QUOTE_DATE, todayKey)\n                    .apply()\n            }\n        }\n    }\n'''
if quote_effect not in s:
    if refresh_end not in s:
        raise SystemExit("launcher refresh effect anchor not found")
    s = s.replace(refresh_end, refresh_end + quote_effect, 1)

# Feed the compact header.
old_call = '''                DashboardHeader(\n                    title = currentPage?.title ?: "لانچر من",\n                    appCount = currentPage?.packages?.size ?: 0,\n                    onAdd = { showAppPicker = true },\n                    onSettings = { showSettings = true }\n                )\n'''
new_call = '''                DashboardHeader(\n                    title = currentPage?.title ?: "لانچر من",\n                    dailyQuote = dailyQuote,\n                    onAdd = { showAppPicker = true },\n                    onSettings = { showSettings = true }\n                )\n'''
if old_call not in s:
    raise SystemExit("DashboardHeader call anchor not found")
s = s.replace(old_call, new_call, 1)

# Replace only the old oversized page-title header. The new design keeps the title
# compact and adds one slim rounded quote card beneath it.
start_marker = "@Composable\nprivate fun DashboardHeader("
end_marker = "\n@Composable\nprivate fun EmptyPage("
start = s.find(start_marker)
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("DashboardHeader block not found")

new_header = r'''@Composable
private fun DashboardHeader(
    title: String,
    dailyQuote: String,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "تنظیمات",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "افزودن برنامه",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f),
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dailyQuote,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
'''
s = s[:start] + new_header + s[end:]

# Small HTTPS helper. Deterministic day selection means all launches on the same
# day show the same quote even if the cache is cleared and rebuilt.
helper_anchor = "\nprivate fun queryLauncherApps(context: Context): List<AppEntry> {"
helper = r'''
private fun fetchDailyQuote(dayKey: String): String? = runCatching {
    val connection = (URL(DAILY_QUOTES_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 3500
        readTimeout = 3500
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Persian-App-Launcher/29")
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val quotes = JSONArray(body)
        if (quotes.length() == 0) return@runCatching null
        val index = (dayKey.hashCode() and Int.MAX_VALUE) % quotes.length()
        quotes.optString(index).trim().takeIf { it.isNotBlank() }
    } finally {
        connection.disconnect()
    }
}.getOrNull()
'''
if helper not in s:
    if helper_anchor not in s:
        raise SystemExit("queryLauncherApps helper anchor not found")
    s = s.replace(helper_anchor, "\n" + helper + helper_anchor, 1)

p.write_text(s, encoding="utf-8")
print("Applied v29 compact daily quote header")

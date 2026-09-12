from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# v30 keeps the cached UI visible immediately. Network and icon refreshes happen
# after the first frame and never clear the currently visible data.

# Replace the once-per-day quote cache key with a cached online quote collection.
s = s.replace(
    'private const val KEY_DAILY_QUOTE_DATE = "daily_quote_date_v1"\n',
    'private const val KEY_QUOTES_CACHE = "quotes_cache_v1"\n',
    1,
)

# Change the quote effect from once per Activity creation/day to every resume/open.
old_effect = '''    LaunchedEffect(Unit) {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val cachedDate = prefs.getString(KEY_DAILY_QUOTE_DATE, null)
        if (cachedDate != todayKey) {
            val fresh = withContext(Dispatchers.IO) { fetchDailyQuote(todayKey) }
            if (!fresh.isNullOrBlank()) {
                dailyQuote = fresh
                prefs.edit()
                    .putString(KEY_DAILY_QUOTE, fresh)
                    .putString(KEY_DAILY_QUOTE_DATE, todayKey)
                    .apply()
            }
        }
    }
'''
new_effect = '''    LaunchedEffect(iconRefreshGeneration) {
        // First choose immediately from the last online list cached on-device.
        // This is synchronous and tiny, so the first frame is not delayed.
        val cachedQuotes = decodeQuoteCache(prefs.getString(KEY_QUOTES_CACHE, null))
        if (cachedQuotes.isNotEmpty()) {
            val next = pickDifferentQuote(cachedQuotes, dailyQuote, iconRefreshGeneration)
            if (next.isNotBlank()) dailyQuote = next
        }

        // Refresh the source in the background on every launcher resume/open.
        // The currently rendered quote remains visible while the request runs.
        val onlineQuotes = withContext(Dispatchers.IO) { fetchQuoteList() }
        if (onlineQuotes.isNotEmpty()) {
            val next = pickDifferentQuote(onlineQuotes, dailyQuote, iconRefreshGeneration)
            if (next.isNotBlank()) dailyQuote = next
            prefs.edit()
                .putString(KEY_QUOTES_CACHE, JSONArray(onlineQuotes).toString())
                .putString(KEY_DAILY_QUOTE, dailyQuote)
                .apply()
        } else {
            prefs.edit().putString(KEY_DAILY_QUOTE, dailyQuote).apply()
        }
    }
'''
if old_effect not in s:
    raise SystemExit("v29 quote effect anchor not found")
s = s.replace(old_effect, new_effect, 1)

# Keep tile icon cache stable across resumes. Previously the generation was part
# of the key, which intentionally blanked/reloaded every icon and caused a small
# visible startup delay. Now the old bitmap stays visible until a fresh one arrives.
old_tile_key = '''    val cacheKey = remember(app.packageName, app.activityName, iconPx, iconRefreshGeneration) {
        "${app.packageName}|${app.activityName}@$iconPx#$iconRefreshGeneration"
    }
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
    }'''
new_tile_key = '''    val cacheKey = remember(app.packageName, iconPx) { "${app.packageName}@$iconPx" }
    var icon by remember(cacheKey) { mutableStateOf(iconCache.get(cacheKey)) }

    LaunchedEffect(app.packageName, iconPx, iconRefreshGeneration) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadAppIcon(context.applicationContext, app, iconPx).asImageBitmap() }.getOrNull()
        }
        loaded?.let {
            iconCache.put(cacheKey, it)
            icon = it
        }
    }'''
if old_tile_key not in s:
    raise SystemExit("v28 AppTile cache anchor not found")
s = s.replace(old_tile_key, new_tile_key, 1)

old_mini = '''    val cacheKey = remember(app.packageName, app.activityName, iconRefreshGeneration) {
        "${app.packageName}|${app.activityName}@80#$iconRefreshGeneration"
    }
    var icon by remember(cacheKey) { mutableStateOf(iconCache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (icon == null) {
            val loaded = withContext(Dispatchers.IO) { runCatching { loadAppIcon(context.applicationContext, app, 80).asImageBitmap() }.getOrNull() }
            loaded?.let { iconCache.put(cacheKey, it); icon = it }
        }
    }'''
new_mini = '''    val cacheKey = remember(app.packageName) { "${app.packageName}@80" }
    var icon by remember(cacheKey) { mutableStateOf(iconCache.get(cacheKey)) }
    LaunchedEffect(app.packageName, iconRefreshGeneration) {
        val loaded = withContext(Dispatchers.IO) { runCatching { loadAppIcon(context.applicationContext, app, 80).asImageBitmap() }.getOrNull() }
        loaded?.let { iconCache.put(cacheKey, it); icon = it }
    }'''
if old_mini not in s:
    raise SystemExit("v28 MiniAppIcon cache anchor not found")
s = s.replace(old_mini, new_mini, 1)

# Resolve the currently enabled launcher alias each time an icon is refreshed.
# This catches Telegram-style runtime icon changes while preserving the old icon
# on screen until the new bitmap is ready.
old_icon_loader = '''private fun loadAppIcon(context: Context, app: AppEntry, maxSizePx: Int): Bitmap {
    val pm = context.packageManager
    val drawable = if (app.activityName.isNotBlank()) {
        runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }
            .recoverCatching { pm.getApplicationIcon(app.packageName) }.getOrThrow()
    } else pm.getApplicationIcon(app.packageName)
    return drawableToBitmap(drawable, maxSizePx)
}'''
new_icon_loader = '''private fun loadAppIcon(context: Context, app: AppEntry, maxSizePx: Int): Bitmap {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(app.packageName)
    }
    val activeLauncher = runCatching { pm.queryIntentActivities(launcherIntent, 0).firstOrNull() }.getOrNull()
    val drawable = activeLauncher?.let { runCatching { it.loadIcon(pm) }.getOrNull() }
        ?: if (app.activityName.isNotBlank()) {
            runCatching { pm.getActivityIcon(ComponentName(app.packageName, app.activityName)) }
                .recoverCatching { pm.getApplicationIcon(app.packageName) }.getOrThrow()
        } else pm.getApplicationIcon(app.packageName)
    return drawableToBitmap(drawable, maxSizePx)
}'''
if old_icon_loader not in s:
    raise SystemExit("loadAppIcon anchor not found")
s = s.replace(old_icon_loader, new_icon_loader, 1)

# Replace v29 day-based network helper with an online list fetch + no-repeat picker.
start = s.find("private fun fetchDailyQuote(")
end = s.find("\nprivate fun queryLauncherApps", start)
if start < 0 or end < 0:
    raise SystemExit("v29 quote helper block not found")
new_helpers = r'''private fun fetchQuoteList(): List<String> = runCatching {
    val connection = (URL(DAILY_QUOTES_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 2200
        readTimeout = 2200
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("User-Agent", "Persian-App-Launcher/30")
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching emptyList()
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(body)
        buildList(array.length()) {
            repeat(array.length()) { i ->
                array.optString(i).trim().takeIf { it.length in 8..120 }?.let(::add)
            }
        }.distinct()
    } finally {
        connection.disconnect()
    }
}.getOrDefault(emptyList())

private fun decodeQuoteCache(raw: String?): List<String> = runCatching {
    if (raw.isNullOrBlank()) return@runCatching emptyList()
    val array = JSONArray(raw)
    buildList(array.length()) {
        repeat(array.length()) { i -> array.optString(i).trim().takeIf(String::isNotBlank)?.let(::add) }
    }.distinct()
}.getOrDefault(emptyList())

private fun pickDifferentQuote(quotes: List<String>, current: String, generation: Int): String {
    if (quotes.isEmpty()) return current
    if (quotes.size == 1) return quotes.first()
    val candidates = quotes.filterNot { it == current }
    if (candidates.isEmpty()) return quotes.first()
    val seed = System.nanoTime() xor System.currentTimeMillis() xor generation.toLong()
    val index = ((seed xor (seed ushr 32)).toInt() and Int.MAX_VALUE) % candidates.size
    return candidates[index]
}
'''
s = s[:start] + new_helpers + s[end:]

p.write_text(s, encoding="utf-8")
print("Applied v30 instant startup + quote on every open")

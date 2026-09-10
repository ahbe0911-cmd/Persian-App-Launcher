from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V23: replace the large dashboard header with a minimal settings-only strip.
# The previous patch replaced everything up to AppTile and accidentally removed
# EmptyPage. Recreate both composables explicitly so empty launcher pages compile.
start_marker = "@Composable\nprivate fun DashboardHeader("
end_marker = "\n@Composable\nprivate fun AppTile("
start = s.find(start_marker)
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("DashboardHeader/AppTile boundary not found; source changed")

replacement = r'''@Composable
private fun DashboardHeader(
    title: String,
    appCount: Int,
    onAdd: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(44.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
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
    }
}

@Composable
private fun EmptyPage(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ افزودن برنامه",
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
'''

s = s[:start] + replacement + s[end:]

# Grid begins immediately below the compact settings strip.
s = s.replace(
    "contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),",
    "contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),"
)

p.write_text(s, encoding="utf-8")

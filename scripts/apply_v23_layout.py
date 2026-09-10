from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V23: replace the large dashboard header with a minimal settings-only strip.
start_marker = "@Composable\nprivate fun DashboardHeader("
end_marker = "\n@Composable\nprivate fun AppTile("
start = s.find(start_marker)
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("DashboardHeader block not found; source changed")

new_header = r'''@Composable
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
'''

s = s[:start] + new_header + s[end:]

# Grid begins immediately below the compact settings strip.
s = s.replace(
    "contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),",
    "contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),"
)

p.write_text(s, encoding="utf-8")

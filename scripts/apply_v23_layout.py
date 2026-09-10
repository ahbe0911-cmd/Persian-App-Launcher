from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V27: keep the fast/minimal V26 layout, but always show the current page title.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 14.dp),
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
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

s = s.replace(
    "contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),",
    "contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),"
)

p.write_text(s, encoding="utf-8")

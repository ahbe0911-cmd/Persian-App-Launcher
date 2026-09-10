from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V26 is deliberately based on V23. Keep its minimal header, but expose the
# existing onAdd action permanently so apps can be added after a page is populated.
old = '''        IconButton(
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
'''
new = '''        Row(
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
'''
if old not in s:
    raise SystemExit("V23 settings button block not found")
s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")

from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V22 grid system: header and grid share the same 14dp outer edge,
# while both grid axes use one 12dp spacing token.
s = s.replace(
    "contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),\n                            verticalArrangement = Arrangement.spacedBy(16.dp),\n                            horizontalArrangement = Arrangement.spacedBy(12.dp)",
    "contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),\n                            verticalArrangement = Arrangement.spacedBy(12.dp),\n                            horizontalArrangement = Arrangement.spacedBy(12.dp)"
)

# Fixed icon-to-label rhythm across every tile.
s = s.replace("Spacer(Modifier.height(7.dp))", "Spacer(Modifier.height(8.dp))")

# Unify icon artwork inside one Material 3 container. Keep a little breathing
# room so icons with full-bleed backgrounds and white backgrounds read equally.
s = s.replace(
    "Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape((iconSize * 0.22f).dp))",
    "Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape((iconSize * 0.22f).dp))"
)
s = s.replace(
    "tonalElevation = if (isDragging) 6.dp else 1.dp,\n                shadowElevation = if (isDragging) 8.dp else 2.dp",
    "tonalElevation = if (isDragging) 5.dp else 1.dp,\n                shadowElevation = if (isDragging) 6.dp else 1.dp"
)

# The header should not visually overpower the launcher grid.
s = s.replace("tonalElevation = 2.dp,\n        shadowElevation = 2.dp", "tonalElevation = 1.dp,\n        shadowElevation = 1.dp")

# Slightly more compact labels reduce vertical noise while keeping two lines.
s = s.replace("lineHeight = 15.sp,", "lineHeight = 14.sp,")

p.write_text(s, encoding="utf-8")

from pathlib import Path

p = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
s = p.read_text(encoding="utf-8")

# V25: Never tint the complete launcher bitmap. Tinting a full adaptive/legacy
# bitmap turns its opaque background into a white rounded block. Preserve the
# actual icon pixels instead; this is the safe fallback for every icon format.
s = s.replace(
    "colorFilter = if (monochrome) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null",
    "colorFilter = null"
)

# The old heuristic is intentionally disabled: transparency/white corners do
# not prove that the bitmap itself is a valid monochrome glyph mask.
s = s.replace(
    "val monochrome = remember(icon) { icon?.let(::prefersMonochromeTreatment) ?: false }\n",
    ""
)

# Give the original artwork enough room while keeping the uniform colored tile.
s = s.replace(
    "modifier = Modifier.size((visualSize * 0.58f).dp),",
    "modifier = Modifier.size((visualSize * 0.68f).dp),"
)

p.write_text(s, encoding="utf-8")

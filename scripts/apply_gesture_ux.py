from pathlib import Path

path = Path("app/src/main/java/com/ahbe/mylauncher/MainActivity.kt")
text = path.read_text(encoding="utf-8")

text = text.replace("import androidx.compose.material.icons.rounded.MoreVert\n", "")
text = text.replace(
    "    var dragging by remember { mutableStateOf(false) }\n    var dragX by remember { mutableFloatStateOf(0f) }\n    var dragY by remember { mutableFloatStateOf(0f) }",
    "    var dragging by remember { mutableStateOf(false) }\n    var dragMoved by remember { mutableStateOf(false) }\n    var dragX by remember { mutableFloatStateOf(0f) }\n    var dragY by remember { mutableFloatStateOf(0f) }"
)
text = text.replace(
    "                        dragging = true\n                        dragX = 0f\n                        dragY = 0f",
    "                        dragging = true\n                        dragMoved = false\n                        dragX = 0f\n                        dragY = 0f"
)
text = text.replace(
    "                        dragging = false\n                        dragX = 0f\n                        dragY = 0f\n                        onDragFinished()",
    "                        dragging = false\n                        dragX = 0f\n                        dragY = 0f\n                        if (dragMoved) onDragFinished() else showMenu = true"
)
text = text.replace(
    "                        val threshold = 54.dp.toPx()",
    "                        val threshold = 22.dp.toPx()"
)
text = text.replace(
    "                        if (kotlin.math.abs(dragY) >= threshold) {\n                            onDragMove(if (dragY > 0) columns else -columns)",
    "                        if (kotlin.math.abs(dragY) >= threshold) {\n                            dragMoved = true\n                            onDragMove(if (dragY > 0) columns else -columns)"
)
text = text.replace(
    "                        } else if (kotlin.math.abs(dragX) >= threshold) {\n                            onDragMove(if (dragX > 0) -1 else 1)",
    "                        } else if (kotlin.math.abs(dragX) >= threshold) {\n                            dragMoved = true\n                            onDragMove(if (dragX > 0) -1 else 1)"
)

old_overlay = '''\n        Surface(\n            onClick = { showMenu = true },\n            modifier = Modifier\n                .align(Alignment.TopEnd)\n                .padding(top = 2.dp, end = 2.dp)\n                .size(24.dp)\n                .shadow(1.dp, CircleShape),\n            shape = CircleShape,\n            color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),\n            tonalElevation = 1.dp\n        ) {\n            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n                Icon(\n                    Icons.Rounded.MoreVert,\n                    "گزینه‌های برنامه",\n                    modifier = Modifier.size(14.dp),\n                    tint = MaterialTheme.colorScheme.primary.copy(alpha = .78f)\n                )\n            }\n        }\n'''
if old_overlay not in text:
    raise SystemExit("overflow overlay block not found; source changed")
text = text.replace(old_overlay, "\n")

text = text.replace(
    '"برای جابه‌جایی، آیکون برنامه را نگه دارید و بکشید."',
    '"برای جابه‌جایی، نگه دارید و بلافاصله بکشید؛ برای گزینه‌ها نگه دارید و رها کنید."'
)

path.write_text(text, encoding="utf-8")
print("Applied long-press menu + lower-threshold drag UX patch")

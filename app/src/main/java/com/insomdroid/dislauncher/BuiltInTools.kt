                    Text(
                        if (previewMode) "Preview" else if (title.isBlank()) "New note" else title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { previewMode = !previewMode }) {
                        Icon(
                            if (previewMode) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                            contentDescription = if (previewMode) "Edit note" else "Preview note"
                        )
                    }
                    IconButton(onClick = { exportNote(title, body) }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Export Markdown file")
                    }
                    Box {
                        IconButton(onClick = { editorMenuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More note actions")
                        }
                        DropdownMenu(expanded = editorMenuExpanded, onDismissRequest = { editorMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Heading") }, onClick = {
                                editorMenuExpanded = false
                                body += if (body.isBlank()) "# " else "\n# "
                                saveCurrent()
                            })
                            DropdownMenuItem(text = { Text("Bold") }, onClick = {
                                editorMenuExpanded = false
                                body += if (body.isBlank()) "**bold**" else "\n**bold**"
                                saveCurrent()
                            })
                            DropdownMenuItem(text = { Text("Bullet list") }, onClick = {
                                editorMenuExpanded = false
                                body += if (body.isBlank()) "- " else "\n- "
                                saveCurrent()
                            })
                            DropdownMenuItem(text = { Text("Checklist") }, onClick = {
                                editorMenuExpanded = false
                                body += if (body.isBlank()) "- [ ] " else "\n- [ ] "
                                saveCurrent()
                            })
                            DropdownMenuItem(text = { Text("Link") }, onClick = {
                                editorMenuExpanded = false
                                body += if (body.isBlank()) "[link](https://)" else "\n[link](https://)"
                                saveCurrent()
                            })
                            DropdownMenuItem(
                                text = { Text("Insert image") },
                                onClick = {
                                    editorMenuExpanded = false
                                    imageLauncher.launch(arrayOf("image/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete note") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = { editorMenuExpanded = false; deleteTarget = selectedId }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it; saveCurrent() }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Title (optional)") }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge, readOnly = previewMode
                )
                if (previewMode) {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(body.lines().ifEmpty { listOf("") }) { line -> MarkdownPreviewLine(line) }
                    }
                } else {
                    OutlinedTextField(
                        value = body, onValueChange = { body = it; saveCurrent() },
                        modifier = Modifier.weight(1f).fillMaxWidth(), placeholder = { Text("Write Markdown here…") },
                        minLines = 8, maxLines = Int.MAX_VALUE
                    )
                }
            }
        }
        if (selectedId == null) {
            FloatingActionButton(onClick = {
                selectedId = UUID.randomUUID().toString()
                title = ""
                body = ""
                previewMode = false
                onEditingChanged(true)
            }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Outlined.NoteAdd, contentDescription = "New note")
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete note?") },
            text = { Text("This note will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    notes = notes.filterNot { it.id == deleteTarget }
                    saveNotes(context, notes)
                    deleteTarget = null
                    selectedId = null
                    title = ""
                    body = ""
                    previewMode = false
                    onEditingChanged(false)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MarkdownPreviewLine(line: String) {
    val trimmed = line.trimStart()
    val imageMatch = Regex("^!\\[[^]]*]\\((?:<)?([^)>]+)(?:>)?\\)$").matchEntire(trimmed)
    if (imageMatch != null) {
        val imageUri = runCatching { Uri.parse(imageMatch.groupValues[1]) }.getOrNull()
        if (imageUri != null) {
            AndroidView(
                factory = { context -> ImageView(context).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER } },
                update = { view -> runCatching { view.setImageURI(imageUri) } },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }
        return
    }
    val headingLevel = trimmed.takeWhile { it == '#' }.length.coerceIn(0, 6)
        .takeIf { trimmed.startsWith("#") && trimmed.getOrNull(it) == ' ' } ?: 0
    val displayText = when {
        headingLevel > 0 -> trimmed.drop(headingLevel).trimStart()
        trimmed.startsWith("- [ ] ") -> "☐ " + trimmed.removePrefix("- [ ] ")
        trimmed.startsWith("- [x] ", true) -> "☑ " + trimmed.substring(6)
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "• " + trimmed.drop(2)
        trimmed.startsWith("> ") -> "│ " + trimmed.drop(2)
        trimmed.startsWith("---") -> "────────"
        else -> trimmed
    }
    val visibleText = displayText
        .replace(Regex("\\*\\*([^*]+)\\*\\*")) { it.groupValues[1] }
        .replace(Regex("\\*([^*]+)\\*")) { it.groupValues[1] }
    val styled = buildAnnotatedString {
        append(visibleText)
        if (headingLevel > 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), 0, length)
        if (trimmed.startsWith(">")) addStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic), 0, length)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val linkRegex = Regex("\\[([^]]+)]\\((https?://[^)]+)\\)")
    val linkedText = buildAnnotatedString {
        var cursor = 0
        linkRegex.findAll(visibleText).forEach { match ->
            append(visibleText.substring(cursor, match.range.first))
            pushStringAnnotation(tag = "URL", annotation = match.groupValues[2])
            pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
            append(match.groupValues[1])
            pop()
            pop()
            cursor = match.range.last + 1
        }
        append(visibleText.substring(cursor))
    }
    ClickableText(
        text = linkedText,
        style = when {
            headingLevel == 1 -> MaterialTheme.typography.headlineSmall
            headingLevel == 2 -> MaterialTheme.typography.titleLarge
            headingLevel in 3..6 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.bodyLarge
        }.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            linkedText.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                    else Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show() }
            }
        }
    )
}

@Composable
private fun CalculatorTool(
    modifier: Modifier = Modifier,
    showHistory: Boolean,
    onShowHistoryChange: (Boolean) -> Unit,
    clearHistorySignal: Int,
    onHistoryAvailabilityChanged: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var history by remember(context) { mutableStateOf(loadCalculatorHistory(context)) }

    LaunchedEffect(history) {
        onHistoryAvailabilityChanged(history.isNotEmpty())
    }
    LaunchedEffect(clearHistorySignal) {
        if (clearHistorySignal > 0) showClearHistoryDialog = true
    }
    val keyRows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("(", "0", ")", "."),
        listOf("±", "00", "=", " ")
    )

    BoxWithConstraints(modifier = modifier) {
        val keySize = minOf(72.dp, (maxWidth - 24.dp) / 4, (maxHeight - 190.dp) / 6)
            .coerceAtLeast(36.dp)

        if (showHistory) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No calculations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history, key = { it.id }) { entry ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                    expression = entry.expression
                                    result = entry.result
                                    onShowHistoryChange(false)
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(entry.expression, style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    Text(entry.result, style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        java.time.Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    if (expression.isNotBlank()) {
                        Text(expression, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        result.ifBlank { expression.ifBlank { "0" } },
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 2
                    )
                }
                keyRows.forEach { rowKeys ->
                    Row(
                        Modifier.fillMaxWidth().height(keySize),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowKeys.forEach { key ->
                            if (key.isBlank()) {
                                Spacer(Modifier.size(keySize))
                            } else {
                                val operator = key in listOf("÷", "×", "−", "+", "=")
                                val action: () -> Unit = {
                                    when (key) {
                                        "C" -> { expression = ""; result = "" }
                                        "⌫" -> { expression = expression.dropLast(1); result = "" }
                                        "=" -> {
                                            val calculated = runCatching {
                                                formatCalculation(ExpressionParser(expression).parse())
                                            }.getOrNull()
                                            if (calculated == null) {
                                                result = "Invalid expression"
                                            } else {
                                                result = calculated
                                                val entry = CalculatorHistoryEntry(
                                                    UUID.randomUUID().toString(), expression, calculated,
                                                    System.currentTimeMillis()
                                                )
                                                history = (listOf(entry) + history).take(100)
                                                saveCalculatorHistory(context, history)
                                            }
                                        }
                                        "%" -> {
                                            if (expression.lastOrNull()?.let { it.isDigit() || it == ')' } == true) {
                                                expression = applyCalculatorPercentage(expression)
                                            }
                                            result = ""
                                        }
                                        "±" -> {
                                            if (expression.isNotBlank()) expression = "-(" + expression + ")"
                                            result = ""
                                        }
                                        else -> {
                                            expression += when (key) {
                                                "×" -> "*"
                                                "÷" -> "/"
                                                "−" -> "-"
                                                else -> key
                                            }
                                            result = ""
                                        }
                                    }
                                }
                                val labelSize = (keySize.value / 2.35f).coerceIn(18f, 28f).sp
                                if (operator) {
                                    Button(onClick = action, modifier = Modifier.size(keySize), shape = CircleShape,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                } else {
                                    FilledTonalButton(onClick = action, modifier = Modifier.size(keySize), shape = CircleShape,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                        Text(key, fontSize = labelSize, lineHeight = labelSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear calculator history?") },
            text = { Text("Saved calculations will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    history = emptyList()
                    saveCalculatorHistory(context, history)
                    showClearHistoryDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun applyCalculatorPercentage(expression: String): String {
    var depth = 0
    var lastOperator = -1
    for (index in expression.indices) {
        when (expression[index]) {
            '(' -> depth++
            ')' -> depth--
            '+', '-', '*', '/' -> {
                val previous = expression.getOrNull(index - 1)
                if (
                    depth == 0 && index > 0 &&
                    previous != null && previous !in charArrayOf('+', '-', '*', '/', '(')
                ) {
                    lastOperator = index
                }
            }
        }
    }

    if (lastOperator <= 0) return "($expression)/100"
    val left = expression.substring(0, lastOperator)
    val operator = expression[lastOperator]
    val right = expression.substring(lastOperator + 1)
    return when (operator) {
        '+', '-' -> "$left$operator(($left)*($right)/100)"
        '*' -> "$left*(($right)/100)"
        '/' -> "$left/(($right)/100)"
        else -> "($expression)/100"
    }
}

private fun formatCalculation(value: Double): String =
    if (!value.isFinite()) "Cannot calculate"
    else if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')

private class ExpressionParser(raw: String) {
    private val input = raw.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
    private var index = 0
    fun parse(): Double {
        require(input.isNotBlank())
        val value = expression()
        require(index == input.length)
        require(value.isFinite())
        return value
    }
    private fun expression(): Double {
        var value = term()
        while (index < input.length && (input[index] == '+' || input[index] == '-')) {
            val op = input[index++]
            val rhs = term()
            value = if (op == '+') value + rhs else value - rhs
        }
        return value
    }
    private fun term(): Double {
        var value = primary()
        while (index < input.length && (input[index] == '*' || input[index] == '/')) {
            val op = input[index++]
            val rhs = primary()
            value = if (op == '*') value * rhs else {
                require(rhs != 0.0)
                value / rhs
            }
        }
        return value
    }
    private fun primary(): Double {
        if (index < input.length && (input[index] == '+' || input[index] == '-')) {
            val op = input[index++]
            val value = primary()
            return if (op == '-') -value else value
        }
        if (index < input.length && input[index] == '(') {
            index++
            val value = expression()
            require(index < input.length && input[index] == ')')
            index++
            return value
        }
        val start = index
        var dots = 0
        while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
            if (input[index] == '.') dots++
            require(dots <= 1)
            index++
        }
        require(index > start)
        return input.substring(start, index).toDouble()
    }
}

private data class DeviceCalendarEvent(
    val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean
)

private fun readDeviceCalendarEvents(context: Context, start: LocalDate, endExclusive: LocalDate): List<DeviceCalendarEvent> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val zone = ZoneId.systemDefault()
    val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, startMillis)
        ContentUris.appendId(it, endMillis)
    }.build()
    val result = mutableListOf<DeviceCalendarEvent>()
    try {
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            while (cursor.moveToNext()) result += DeviceCalendarEvent(
                cursor.getLong(idIndex),
                cursor.getString(titleIndex)?.trim().takeUnless { it.isNullOrEmpty() } ?: "Untitled event",
                cursor.getLong(beginIndex), cursor.getLong(endIndex), cursor.getInt(allDayIndex) != 0
            )
        }
    } catch (_: SecurityException) { return emptyList() }
      catch (_: IllegalArgumentException) { return emptyList() }
    return result.distinctBy { "${it.id}:${it.begin}" }
}

private fun openDeviceCalendarEvent(context: Context, event: DeviceCalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id).buildUpon()
        .appendQueryParameter("beginTime", event.begin.toString()).appendQueryParameter("endTime", event.end.toString()).build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else Toast.makeText(context, "No calendar app available", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "Could not open calendar event", Toast.LENGTH_SHORT).show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTool(context: Context, modifier: Modifier = Modifier, onBack: () -> Unit) {
    var tasks by remember(context) { mutableStateOf(loadTasks(context)) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var calendarPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var deviceEvents by remember { mutableStateOf<List<DeviceCalendarEvent>>(emptyList()) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarPermission = granted || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showEditor by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("todo") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var priority by remember { mutableStateOf(2) }
    var pendingReminder by remember { mutableStateOf<LocalTask?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalTask?>(null) }

    fun addTask(task: LocalTask, schedule: Boolean) {
        tasks = (tasks.filterNot { it.id == task.id } + task)
        saveTasks(context, tasks)
        if (schedule && task.type == "reminder" && task.reminderAt > System.currentTimeMillis()) {
            scheduleLocalReminder(context, task.id, task.title, task.reminderAt)
        }
        selectedDate = LocalDate.parse(task.date)
        month = YearMonth.from(selectedDate)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val task = pendingReminder
        pendingReminder = null
        if (task != null) {
            addTask(task, granted)
            if (!granted) Toast.makeText(context, "Reminder saved without a notification. Allow notifications in Android settings to receive reminders.", Toast.LENGTH_LONG).show()
        }
    }

    fun beginNewTask() {
        title = ""
        type = "todo"
        date = selectedDate
        time = LocalTime.of(9, 0)
        priority = 2
        showEditor = true
    }

    LaunchedEffect(month, calendarPermission) {
        deviceEvents = if (calendarPermission) withContext(Dispatchers.IO) {
            readDeviceCalendarEvents(context, month.atDay(1), month.plusMonths(1).atDay(1))
        } else emptyList()
    }

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekDays = (0..6).map { firstDayOfWeek.plus(it.toLong()) }
    val taskDate = selectedDate.toString()
    val dayEvents = deviceEvents.filter { Instant.ofEpochMilli(it.begin).atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate }.sortedBy { it.begin }
    val dayTasks = tasks.filter { it.date == taskDate }
        .sortedWith(compareBy<LocalTask> { it.completed }.thenByDescending { it.priority }
            .thenBy { if (it.time.isBlank()) "99:99" else it.time })

    Box(modifier = modifier) {
    Column(modifier = Modifier.fillMaxSize().padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back") }
                Text(month.format(monthFormatter), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { month = month.minusMonths(1); selectedDate = month.atDay(1) }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Previous month")
                }
                IconButton(onClick = { month = month.plusMonths(1); selectedDate = month.atDay(1) }) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "Next month")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { month = YearMonth.now(); selectedDate = LocalDate.now() }) {
                    Text(if (Locale.getDefault().language == "id") "Hari ini" else "Today")
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    weekDays.forEach { weekDay ->
                        Text(
                            weekDay.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                            Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                val firstOffset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
                val cells = List(firstOffset) { 0 } + (1..month.lengthOfMonth()).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(38.dp).padding(1.dp), contentAlignment = Alignment.Center) {
                                if (day > 0) {
                                    val dateValue = month.atDay(day)
                                    val selected = dateValue == selectedDate
                                    val today = dateValue == LocalDate.now()
                                    val hasTasks = tasks.any { it.date == dateValue.toString() } ||
                                        deviceEvents.any { Instant.ofEpochMilli(it.begin).atZone(ZoneId.systemDefault()).toLocalDate() == dateValue }
                                    Column(
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                            .background(when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                today -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> Color.Transparent
                                            })
                                            .clickable { selectedDate = dateValue },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(day.toString(), color = when {
                                            selected -> MaterialTheme.colorScheme.onPrimary
                                            today -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal)
                                        Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape)
                                            .background(if (hasTasks) {
                                                if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                            } else Color.Transparent))
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (selectedDate == LocalDate.now()) (if (Locale.getDefault().language == "id") "Hari ini" else "Today") else selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(buildString {
                    if (dayEvents.isNotEmpty()) append(dayEvents.size).append(if (dayEvents.size == 1) " event" else " events")
                    if (dayEvents.isNotEmpty() && dayTasks.isNotEmpty()) append(" · ")
                    if (dayTasks.isNotEmpty() || dayEvents.isEmpty()) append(dayTasks.size).append(if (dayTasks.size == 1) " task" else " tasks")
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!calendarPermission) {
            TextButton(onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                Text("Allow calendar access to show device events")
            }
        }
        if (dayTasks.isEmpty() && dayEvents.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nothing planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(dayEvents, key = { "device-${it.id}-${it.begin}" }) { event ->
                    Row(Modifier.fillMaxWidth().clickable { openDeviceCalendarEvent(context, event) }
                        .padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (event.allDay) "All day" else SimpleDateFormat(
                            if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
                            Locale.getDefault()).format(Date(event.begin)),
                            modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.tertiary))
                        Text(event.title, modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
                items(dayTasks, key = { "task-${it.id}" }) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.type == "todo") {
                            Checkbox(checked = task.completed, onCheckedChange = { checked ->
                                tasks = tasks.map { if (it.id == task.id) it.copy(completed = checked) else it }
                                saveTasks(context, tasks)
                            })
                        } else {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Reminder",
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = priorityColor(task.priority), shape = RoundedCornerShape(50)) {
                                    Text(priorityLabel(task.priority), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (task.time.isNotBlank()) Text(task.time, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (task.type == "reminder") Text("Reminder", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { deleteTarget = task }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete task",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
        FloatingActionButton(
            onClick = ::beginNewTask,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(Icons.Outlined.Add, contentDescription = "Add task") }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("New task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (type == "todo") {
                            Button(
                                onClick = { type = "todo" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("To-do", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        } else {
                            FilledTonalButton(
                                onClick = { type = "todo" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("To-do", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        }
                        if (type == "reminder") {
                            Button(
                                onClick = { type = "reminder" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("Reminder", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        } else {
                            FilledTonalButton(
                                onClick = { type = "reminder" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) { Text("Reminder", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    TextButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))
                    }
                    if (type == "reminder") {
                        TextButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                        }
                    }
                    Text(if (Locale.getDefault().language == "id") "Prioritas" else "Priority", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..3).forEach { level ->
                            val label = priorityLabel(level)
                            val isSelected = priority == level
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { priority = level },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Enter a task title", Toast.LENGTH_SHORT).show()
                    } else {
                        val at = if (type == "reminder") date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else 0L
                        if (type == "reminder" && at <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Choose a future time for the reminder", Toast.LENGTH_SHORT).show()
                        } else {
                            val task = LocalTask(UUID.randomUUID().toString(), title.trim(), type,
                                date.toString(), if (type == "reminder") time.format(DateTimeFormatter.ofPattern("HH:mm")) else "",
                                at, priority)
                            showEditor = false
                            val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (type == "reminder" && permissionMissing) {
                                pendingReminder = task
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                addTask(task, type == "reminder")
                            }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        Dialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column {
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = {
                            Text(
                                if (Locale.getDefault().language == "id") "Pilih tanggal" else "Select date",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(if (Locale.getDefault().language == "id") "Batal" else "Cancel")
                        }
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            showDatePicker = false
                        }) {
                            Text(if (Locale.getDefault().language == "id") "Pilih" else "Select")
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(if (Locale.getDefault().language == "id") "Pilih waktu" else "Select time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text(if (Locale.getDefault().language == "id") "Pilih" else "Select") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(if (Locale.getDefault().language == "id") "Batal" else "Cancel")
                }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete task?") },
            text = { Text("This task will be removed from your local calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    val task = deleteTarget
                    if (task != null) {
                        cancelLocalReminder(context, task.id)
                        tasks = tasks.filterNot { it.id == task.id }
                        saveTasks(context, tasks)
                    }
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

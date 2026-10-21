package com.linuxkernel44.openwebdav.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.linuxkernel44.openwebdav.R
import com.linuxkernel44.openwebdav.data.DavEntry
import com.linuxkernel44.openwebdav.data.DownloadWorker
import com.linuxkernel44.openwebdav.data.SortField
import com.linuxkernel44.openwebdav.util.ExternalOpen
import com.linuxkernel44.openwebdav.util.FileKind
import com.linuxkernel44.openwebdav.util.FileTypes
import com.linuxkernel44.openwebdav.util.cleanSegments
import com.linuxkernel44.openwebdav.util.sortEntries
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val SUBTITLE_TIMEOUT_MS = 4000L

private data class Crumb(val label: String, val url: HttpUrl)

private fun buildCrumbs(root: HttpUrl, current: HttpUrl): List<Crumb> {
    val rootSegments = root.cleanSegments()
    val segments = current.cleanSegments()
    val crumbs = mutableListOf(Crumb(root.host, root))
    if (segments.size > rootSegments.size && segments.take(rootSegments.size) == rootSegments) {
        val extra = mutableListOf<String>()
        for (segment in segments.drop(rootSegments.size)) {
            extra += segment
            val url = root.newBuilder().apply { extra.forEach { addPathSegment(it) } }.addPathSegment("").build()
            crumbs += Crumb(segment, url)
        }
    }
    return crumbs
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(viewModel: MainViewModel, session: Session.Active, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val state by viewModel.browser.collectAsState()
    val query by viewModel.query.collectAsState()
    val sortField by viewModel.sortField.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val openProgress by viewModel.openProgress.collectAsState()

    var menuEntry by remember { mutableStateOf<DavEntry?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showVlcDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<DavEntry?>(null) }

    val currentUrl = state.url ?: session.root
    val canGoUp = currentUrl != session.root

    fun message(resId: Int) {
        scope.launch { snackbar.showSnackbar(context.getString(resId)) }
    }

    fun enqueueDownload(entry: DavEntry) {
        DownloadWorker.enqueue(context, entry)
        message(R.string.download_started)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val entry = pendingDownload
        pendingDownload = null
        val storageDenied = Build.VERSION.SDK_INT < 29 && result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (entry != null) {
            if (storageDenied) message(R.string.storage_permission_denied) else enqueueDownload(entry)
        }
    }

    fun startDownload(entry: DavEntry) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) {
            enqueueDownload(entry)
        } else {
            pendingDownload = entry
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun onEntryClick(entry: DavEntry) {
        when (FileTypes.kindOf(entry.name, entry.isDirectory, entry.contentType)) {
            FileKind.FOLDER -> viewModel.open(entry.url)
            FileKind.VIDEO -> {
                if (!ExternalOpen.isVlcInstalled(context)) {
                    showVlcDialog = true
                } else {
                    scope.launch {
                        val subtitle = withTimeoutOrNull(SUBTITLE_TIMEOUT_MS) { viewModel.prepareSubtitle(entry) }
                        if (!ExternalOpen.playInVlc(context, viewModel.streamUrl(entry), entry.name, subtitle)) {
                            showVlcDialog = true
                        }
                    }
                }
            }
            FileKind.AUDIO -> {
                val mime = FileTypes.mimeOf(entry.name, entry.contentType)
                if (!ExternalOpen.openDefault(context, Uri.parse(viewModel.streamUrl(entry)), mime)) {
                    viewModel.openViaCache(entry)
                }
            }
            else -> viewModel.openViaCache(entry)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Failure -> snackbar.showSnackbar(errorMessage(context, event.error))
                is UiEvent.OpenCached -> {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", event.file)
                    if (!ExternalOpen.openDefault(context, uri, event.mime, grantRead = true)) {
                        val result = snackbar.showSnackbar(
                            message = context.getString(R.string.no_app_found),
                            actionLabel = context.getString(R.string.action_download),
                        )
                        if (result == SnackbarResult.ActionPerformed) startDownload(event.entry)
                    }
                }
            }
        }
    }

    val refreshError = state.error?.takeIf { state.entries.isNotEmpty() }
    val refreshErrorText = refreshError?.let { errorText(it) }
    LaunchedEffect(refreshError) {
        if (refreshErrorText != null) {
            val result = snackbar.showSnackbar(refreshErrorText, actionLabel = context.getString(R.string.retry))
            if (result == SnackbarResult.ActionPerformed) viewModel.retry()
        }
    }

    BackHandler(enabled = query != null || canGoUp) {
        if (query != null) viewModel.setQuery(null) else viewModel.up()
    }

    val visible = remember(state.entries, sortField, sortAscending, query) {
        val needle = query?.trim().orEmpty()
        val filtered = if (needle.isEmpty()) state.entries else state.entries.filter { it.name.contains(needle, ignoreCase = true) }
        sortEntries(filtered, sortField, sortAscending)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (query != null) {
                        IconButton(onClick = { viewModel.setQuery(null) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.close_search))
                        }
                    } else if (canGoUp) {
                        IconButton(onClick = { viewModel.up() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    }
                },
                title = {
                    val q = query
                    if (q != null) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            value = q,
                            onValueChange = { viewModel.setQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Text(
                            text = if (canGoUp) currentUrl.cleanSegments().lastOrNull() ?: session.root.host
                            else stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (query == null) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Search, stringResource(R.string.search))
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            Text(
                                stringResource(R.string.sort_by),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            SortField.entries.forEach { field ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                when (field) {
                                                    SortField.NAME -> R.string.sort_name
                                                    SortField.DATE -> R.string.sort_date
                                                    SortField.SIZE -> R.string.sort_size
                                                },
                                            ),
                                        )
                                    },
                                    leadingIcon = { RadioButton(selected = sortField == field, onClick = null) },
                                    onClick = {
                                        viewModel.setSort(field, sortAscending)
                                        showSortMenu = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_ascending)) },
                                leadingIcon = { RadioButton(selected = sortAscending, onClick = null) },
                                onClick = {
                                    viewModel.setSort(sortField, true)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_descending)) },
                                leadingIcon = { RadioButton(selected = !sortAscending, onClick = null) },
                                onClick = {
                                    viewModel.setSort(sortField, false)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Breadcrumbs(session.root, currentUrl, onNavigate = { viewModel.open(it) })

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                val locale = LocalConfiguration.current.locales[0]
                val dateFormat = remember(locale) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
                }

                LazyColumn(
                    state = viewModel.listState(currentUrl),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(visible, key = { it.url.toString() }) { entry ->
                        val kind = FileTypes.kindOf(entry.name, entry.isDirectory, entry.contentType)
                        EntryRow(
                            entry = entry,
                            kind = kind,
                            details = entryDetails(context, entry, dateFormat),
                            onClick = { onEntryClick(entry) },
                            onLongClick = { menuEntry = entry },
                            onMore = { menuEntry = entry },
                            menu = {
                                DropdownMenu(expanded = menuEntry == entry, onDismissRequest = { menuEntry = null }) {
                                    if (!entry.isDirectory) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_open_with)) },
                                            onClick = {
                                                menuEntry = null
                                                val mime = FileTypes.mimeOf(entry.name, entry.contentType)
                                                if (!ExternalOpen.openWithChooser(context, viewModel.streamUrl(entry), mime, entry.name)) {
                                                    message(R.string.no_app_found)
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_download)) },
                                            onClick = {
                                                menuEntry = null
                                                startDownload(entry)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_copy_link)) },
                                        onClick = {
                                            menuEntry = null
                                            ExternalOpen.copyToClipboard(context, entry.name, viewModel.plainUrl(entry))
                                            message(R.string.link_copied)
                                        },
                                    )
                                    if (!entry.isDirectory) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_share_link)) },
                                            onClick = {
                                                menuEntry = null
                                                ExternalOpen.shareText(context, viewModel.plainUrl(entry), context.getString(R.string.share_title))
                                            },
                                        )
                                    }
                            }
                            },
                        )
                    }
                }

                when {
                    state.loading && state.entries.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    state.error != null && state.entries.isEmpty() ->
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(errorText(state.error!!), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.retry)) }
                        }

                    visible.isEmpty() && !state.loading ->
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(if (state.entries.isEmpty()) R.string.empty_folder else R.string.no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }
    }

    if (showVlcDialog) {
        AlertDialog(
            onDismissRequest = { showVlcDialog = false },
            title = { Text(stringResource(R.string.vlc_missing_title)) },
            text = { Text(stringResource(R.string.vlc_missing_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = { showVlcDialog = false; ExternalOpen.installVlc(context, fDroid = true) }) {
                        Text(stringResource(R.string.install_f_droid))
                    }
                    TextButton(onClick = { showVlcDialog = false; ExternalOpen.installVlc(context, fDroid = false) }) {
                        Text(stringResource(R.string.install_play_store))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showVlcDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    openProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelOpen() },
            title = { Text(stringResource(R.string.opening_file, progress.name), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                if (progress.fraction != null) {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelOpen() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Breadcrumbs(root: HttpUrl, current: HttpUrl, onNavigate: (HttpUrl) -> Unit) {
    val crumbs = remember(root, current) { buildCrumbs(root, current) }
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            val last = index == crumbs.lastIndex
            if (index > 0) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                crumb.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .then(if (last) Modifier else Modifier.clickable { onNavigate(crumb.url) })
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: DavEntry,
    kind: FileKind,
    details: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            iconFor(kind),
            contentDescription = null,
            tint = if (kind == FileKind.FOLDER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            menu()
        }
    }
}

private fun iconFor(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Filled.Folder
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.AUDIO -> Icons.Filled.MusicNote
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.SUBTITLE -> Icons.Filled.Subtitles
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun entryDetails(context: android.content.Context, entry: DavEntry, dateFormat: DateTimeFormatter): String {
    val parts = buildList {
        entry.size?.let { add(Formatter.formatShortFileSize(context, it)) }
        entry.modified?.let { add(dateFormat.format(Instant.ofEpochMilli(it))) }
    }
    return parts.joinToString(" · ")
}

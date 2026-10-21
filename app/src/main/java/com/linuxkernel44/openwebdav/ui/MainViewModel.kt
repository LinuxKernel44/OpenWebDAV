package com.linuxkernel44.openwebdav.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.linuxkernel44.openwebdav.BuildConfig
import com.linuxkernel44.openwebdav.OpenWebDavApp
import com.linuxkernel44.openwebdav.data.Credentials
import com.linuxkernel44.openwebdav.data.DavEntry
import com.linuxkernel44.openwebdav.data.DavError
import com.linuxkernel44.openwebdav.data.LanguagePref
import com.linuxkernel44.openwebdav.data.SortField
import com.linuxkernel44.openwebdav.data.SubtitleStore
import com.linuxkernel44.openwebdav.data.ThemeMode
import com.linuxkernel44.openwebdav.data.WebDavClient
import com.linuxkernel44.openwebdav.util.AppLocale
import com.linuxkernel44.openwebdav.util.FileTypes
import com.linuxkernel44.openwebdav.util.SubtitleMatcher
import com.linuxkernel44.openwebdav.util.normalizeServerUrl
import com.linuxkernel44.openwebdav.util.parentUrl
import com.linuxkernel44.openwebdav.util.withCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.File

sealed interface Session {
    data object Loading : Session
    data object LoggedOut : Session
    data class Active(val credentials: Credentials, val root: HttpUrl) : Session
}

data class BrowserState(
    val url: HttpUrl? = null,
    val entries: List<DavEntry> = emptyList(),
    /** True while there is nothing to show yet. */
    val loading: Boolean = false,
    /** True while fresh data is being fetched behind cached content or a pull-to-refresh. */
    val refreshing: Boolean = false,
    val error: DavError? = null,
)

data class LoginState(val busy: Boolean = false, val error: LoginError? = null)

sealed interface LoginError {
    data object InvalidUrl : LoginError
    data class Server(val error: DavError) : LoginError
}

data class OpenProgress(val name: String, val fraction: Float?)

sealed interface UiEvent {
    data class Failure(val error: DavError) : UiEvent
    data class OpenCached(val entry: DavEntry, val file: File, val mime: String) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as OpenWebDavApp
    private val settings = graph.settings

    val theme: StateFlow<ThemeMode> = settings.theme
    val language: StateFlow<LanguagePref> = settings.language
    val sortField: StateFlow<SortField> = settings.sortField
    val sortAscending: StateFlow<Boolean> = settings.sortAscending

    private val _session = MutableStateFlow<Session>(Session.Loading)
    val session = _session.asStateFlow()

    private val _login = MutableStateFlow(LoginState())
    val login = _login.asStateFlow()

    private val _browser = MutableStateFlow(BrowserState())
    val browser = _browser.asStateFlow()

    /** Null while the search field is closed. */
    private val _query = MutableStateFlow<String?>(null)
    val query = _query.asStateFlow()

    private val _openProgress = MutableStateFlow<OpenProgress?>(null)
    val openProgress = _openProgress.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var client: WebDavClient? = null
    private var loadJob: Job? = null
    private var openJob: Job? = null

    private val cache = object : LinkedHashMap<String, List<DavEntry>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<DavEntry>>?) = size > CACHE_SIZE
    }
    private val listStates = HashMap<String, LazyListState>()

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { graph.credentialStore.load() }
            val root = saved?.let { normalizeServerUrl(it.baseUrl, allowCleartext = BuildConfig.DEBUG) }
            if (saved != null && root != null) {
                start(saved, root)
            } else {
                _session.value = Session.LoggedOut
            }
        }
    }

    // ---- Login / logout -------------------------------------------------------------------

    fun login(urlInput: String, username: String, password: String) {
        if (_login.value.busy) return
        val root = normalizeServerUrl(urlInput, allowCleartext = BuildConfig.DEBUG)
        if (root == null) {
            _login.value = LoginState(error = LoginError.InvalidUrl)
            return
        }
        val credentials = Credentials(root.toString(), username.trim(), password)
        _login.value = LoginState(busy = true)
        viewModelScope.launch {
            try {
                val entries = WebDavClient(credentials).list(root)
                withContext(Dispatchers.IO) { graph.credentialStore.save(credentials) }
                _login.value = LoginState()
                start(credentials, root, firstListing = entries)
            } catch (e: DavError) {
                _login.value = LoginState(error = LoginError.Server(e))
            }
        }
    }

    fun clearLoginError() {
        if (_login.value.error != null) _login.update { it.copy(error = null) }
    }

    fun logout() {
        loadJob?.cancel()
        openJob?.cancel()
        WorkManager.getInstance(getApplication()).cancelAllWork()
        client = null
        cache.clear()
        listStates.clear()
        _browser.value = BrowserState()
        _query.value = null
        _openProgress.value = null
        File(getApplication<Application>().cacheDir, OPEN_DIR).deleteRecursively()
        viewModelScope.launch(Dispatchers.IO) { graph.credentialStore.clear() }
        _session.value = Session.LoggedOut
    }

    private fun start(credentials: Credentials, root: HttpUrl, firstListing: List<DavEntry>? = null) {
        client = WebDavClient(credentials)
        _session.value = Session.Active(credentials, root)
        if (firstListing != null) {
            cache[root.toString()] = firstListing
            _browser.value = BrowserState(url = root, entries = firstListing)
        } else {
            open(root)
        }
    }

    // ---- Navigation -----------------------------------------------------------------------

    fun open(url: HttpUrl) {
        val key = url.toString()
        val cached = cache[key]
        _query.value = null
        _browser.value = BrowserState(
            url = url,
            entries = cached.orEmpty(),
            loading = cached == null,
            refreshing = cached != null,
        )
        fetch(url)
    }

    /** Goes to the parent folder; returns false when already at the root. */
    fun up(): Boolean {
        val root = (session.value as? Session.Active)?.root ?: return false
        val current = _browser.value.url ?: return false
        if (current == root) return false
        open(current.parentUrl())
        return true
    }

    fun refresh() {
        val url = _browser.value.url ?: return
        _browser.update { it.copy(refreshing = true, error = null) }
        fetch(url)
    }

    fun retry() {
        val url = _browser.value.url ?: return
        _browser.update { it.copy(loading = it.entries.isEmpty(), refreshing = it.entries.isNotEmpty(), error = null) }
        fetch(url)
    }

    private fun fetch(url: HttpUrl) {
        val client = client ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val entries = client.list(url)
                cache[url.toString()] = entries
                _browser.update { it.copy(entries = entries, loading = false, refreshing = false, error = null) }
            } catch (e: DavError) {
                _browser.update { it.copy(loading = false, refreshing = false, error = e) }
            }
        }
    }

    fun listState(url: HttpUrl): LazyListState = listStates.getOrPut(url.toString()) { LazyListState() }

    // ---- Search & settings ----------------------------------------------------------------

    fun setQuery(value: String?) {
        _query.value = value
    }

    fun setTheme(value: ThemeMode) = settings.setTheme(value)

    fun setLanguage(value: LanguagePref) {
        settings.setLanguage(value)
        AppLocale.apply(value)
    }

    fun setSort(field: SortField, ascending: Boolean) = settings.setSort(field, ascending)

    // ---- Links & opening ------------------------------------------------------------------

    /** Link that players can open directly: the credentials are embedded in the URL. */
    fun streamUrl(entry: DavEntry): String {
        val credentials = (session.value as? Session.Active)?.credentials ?: return entry.url.toString()
        return entry.url.withCredentials(credentials.username, credentials.password).toString()
    }

    /** Link that is safe to copy or share: it never contains the password. */
    fun plainUrl(entry: DavEntry): String = entry.url.toString()

    /**
     * Saves the subtitle that belongs to [video], if the folder has one, and returns its local path.
     * Never throws: a missing subtitle must not stop the video from playing.
     */
    suspend fun prepareSubtitle(video: DavEntry): String? {
        val client = client ?: return null
        val match = SubtitleMatcher.find(video, _browser.value.entries, AppLocale.resolve(settings.language.value))
            ?: return null
        return try {
            withContext(Dispatchers.IO) { SubtitleStore.save(getApplication(), client, match) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads [entry] to the app cache, then asks the UI to open it with the default app. */
    fun openViaCache(entry: DavEntry) {
        val client = client ?: return
        openJob?.cancel()
        openJob = viewModelScope.launch {
            val dir = File(getApplication<Application>().cacheDir, OPEN_DIR)
            val file = File(dir, entry.name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
            _openProgress.value = OpenProgress(entry.name, null)
            try {
                withContext(Dispatchers.IO) {
                    dir.deleteRecursively()
                    dir.mkdirs()
                }
                withContext(Dispatchers.IO) {
                    file.outputStream().use { out ->
                        client.download(entry.url, out) { done, total ->
                            _openProgress.value = OpenProgress(
                                entry.name,
                                if (total > 0) done.toFloat() / total else null,
                            )
                        }
                    }
                }
                _events.send(UiEvent.OpenCached(entry, file, FileTypes.mimeOf(entry.name, entry.contentType)))
            } catch (e: CancellationException) {
                file.delete()
                throw e
            } catch (e: DavError) {
                file.delete()
                _events.send(UiEvent.Failure(e))
            } finally {
                _openProgress.value = null
            }
        }
    }

    fun cancelOpen() {
        openJob?.cancel()
    }

    private companion object {
        const val CACHE_SIZE = 60
        const val OPEN_DIR = "open"
    }
}

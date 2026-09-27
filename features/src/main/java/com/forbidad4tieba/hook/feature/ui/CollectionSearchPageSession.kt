package com.forbidad4tieba.hook.feature.ui

/** Page intent and request ownership contain no Fragment, Activity or host model. */
internal class CollectionSearchPageSession(val sourceAccount: String?) {
    var query = ""
    var active = false
    var indexMap = IntArray(0)
    var applying = false
    var fullDataReady = false
    var fullLoadRequested = false
    var syncFooterVisible = false

    @Volatile var fetchingAll = false
        private set
    @Volatile var syncingFirstPage = false
        private set
    var diskRestoreTried = false
        private set
    var diskRestoreInFlight = false
        private set
    private var fetchAfterDiskRestore = false
    private var notifyCacheMissAfterDiskRestore = false
    private var fullLoadToken = 0
    private var diskRestoreToken = 0
    private var firstPageToken = 0
    private val firstPageRequests = LinkedHashSet<Int>()
    private var closed = false

    fun shouldRestoreFullDataOnResume(): Boolean =
        active || query.isNotBlank() || fullLoadRequested || syncFooterVisible

    @Synchronized
    fun beginFullLoad(userId: String?): CollectionSearchRequest? {
        if (closed || fetchingAll || accountKey(userId) != sourceAccount) return null
        invalidateDiskRestore()
        fetchingAll = true
        return request(CollectionSearchRequest.Kind.FULL_LOAD, ++fullLoadToken, userId)
    }

    @Synchronized
    fun finishFullLoad(request: CollectionSearchRequest, currentAccount: String?): Boolean {
        if (request.kind != CollectionSearchRequest.Kind.FULL_LOAD || !owns(request) || !fetchingAll) return false
        fetchingAll = false
        if (sourceAccount != currentAccount) {
            fullLoadRequested = false
            return false
        }
        return true
    }

    @Synchronized
    fun beginFirstPage(userId: String?, force: Boolean): CollectionSearchRequest? {
        if (closed || accountKey(userId) != sourceAccount) return null
        if (!force && (syncingFirstPage || fetchingAll)) return null
        syncingFirstPage = true
        val token = ++firstPageToken
        // Forced requests share the existing FIFO executor; a newer request does
        // not supersede an earlier first-page result in the same session.
        firstPageRequests.add(token)
        return request(CollectionSearchRequest.Kind.FIRST_PAGE, token, userId)
    }

    @Synchronized
    fun finishFirstPage(request: CollectionSearchRequest, currentAccount: String?): Boolean {
        if (request.kind != CollectionSearchRequest.Kind.FIRST_PAGE || !owns(request)) return false
        firstPageRequests.remove(request.token)
        syncingFirstPage = false
        return sourceAccount == currentAccount
    }

    @Synchronized
    fun joinDiskRestore(fetchOnMiss: Boolean, userVisible: Boolean): Boolean {
        if (closed || !diskRestoreInFlight) return false
        if (fetchOnMiss) {
            fetchAfterDiskRestore = true
            notifyCacheMissAfterDiskRestore = notifyCacheMissAfterDiskRestore || userVisible
        }
        return true
    }

    @Synchronized
    fun beginDiskRestore(fetchOnMiss: Boolean, userVisible: Boolean): CollectionSearchRequest? {
        if (closed || diskRestoreTried || diskRestoreInFlight || sourceAccount == null) return null
        diskRestoreTried = true
        diskRestoreInFlight = true
        fetchAfterDiskRestore = fetchOnMiss
        notifyCacheMissAfterDiskRestore = userVisible
        return request(CollectionSearchRequest.Kind.DISK_RESTORE, ++diskRestoreToken, null)
    }

    @Synchronized
    fun finishDiskRestore(request: CollectionSearchRequest, currentAccount: String?): DiskRestoreIntent? {
        if (request.kind != CollectionSearchRequest.Kind.DISK_RESTORE || !owns(request) || !diskRestoreInFlight) return null
        diskRestoreInFlight = false
        val intent = DiskRestoreIntent(fetchAfterDiskRestore, notifyCacheMissAfterDiskRestore)
        fetchAfterDiskRestore = false
        notifyCacheMissAfterDiskRestore = false
        if (sourceAccount != currentAccount) {
            diskRestoreTried = false
            return null
        }
        return intent
    }

    @Synchronized
    fun invalidateDiskRestore() {
        diskRestoreToken++
        diskRestoreInFlight = false
        fetchAfterDiskRestore = false
        notifyCacheMissAfterDiskRestore = false
    }

    @Synchronized
    fun owns(request: CollectionSearchRequest): Boolean {
        if (closed || request.session !== this) return false
        return when (request.kind) {
            CollectionSearchRequest.Kind.FULL_LOAD -> fetchingAll && request.token == fullLoadToken
            CollectionSearchRequest.Kind.FIRST_PAGE -> request.token in firstPageRequests
            CollectionSearchRequest.Kind.DISK_RESTORE -> diskRestoreInFlight && request.token == diskRestoreToken
        }
    }

    @Synchronized
    fun close() {
        closed = true
        fullLoadToken++
        fetchingAll = false
        syncingFirstPage = false
        firstPageRequests.clear()
        invalidateDiskRestore()
    }

    private fun request(kind: CollectionSearchRequest.Kind, token: Int, userId: String?) =
        CollectionSearchRequest(this, kind, token, sourceAccount, userId)

    data class DiskRestoreIntent(val fetchOnMiss: Boolean, val userVisible: Boolean)

    companion object {
        fun accountKey(userId: String?): String? = userId?.trim()?.ifBlank { "__default__" }
    }
}

/** Immutable origin shared by network, disk and UI delivery. */
internal class CollectionSearchRequest internal constructor(
    internal val session: CollectionSearchPageSession,
    val kind: Kind,
    val token: Int,
    val sourceAccount: String?,
    val userId: String?,
) {
    enum class Kind { FULL_LOAD, FIRST_PAGE, DISK_RESTORE }

    // A final page accepted by the loader is durable for its source account even
    // after the page has closed. UI delivery separately requires a live session.
    fun completeCacheAccount(complete: Boolean, hasRawPages: Boolean): String? =
        sourceAccount.takeIf { kind == Kind.FULL_LOAD && complete && hasRawPages }
}

package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.view.View
import com.forbidad4tieba.hook.core.XposedCompat
import java.io.File
import java.util.zip.ZipFile

internal object DexShareIconScanner {
    private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
    private const val TEXT_UTILS_DESCRIPTOR = "Landroid/text/TextUtils;"
    private const val NAVIGATION_BAR_DESCRIPTOR = "Lcom/baidu/tbadk/core/view/NavigationBar;"
    private const val PB_BAR_IMAGE_VIEW_DESCRIPTOR = "Lcom/baidu/tbadk/core/view/PbBarImageView;"
    private const val EM_TEXT_VIEW_DESCRIPTOR =
        "Lcom/baidu/tbadk/core/elementsMaven/view/EMTextView;"
    private const val EM_MANAGER_DESCRIPTOR = "Lcom/baidu/tbadk/core/elementsMaven/EMManager;"
    private const val WEBP_MANAGER_DESCRIPTOR = "Lcom/baidu/tbadk/core/util/WebPManager;"
    private const val WEBP_SET_PURE_DRAWABLE = "setPureDrawable"
    private const val R_DRAWABLE_DESCRIPTOR = "Lcom/baidu/tieba/R\$drawable;"
    private const val BIGDAY_SWIPE_REFRESH_DESCRIPTOR =
        "Lcom/baidu/tieba/homepage/personalize/bigday/BigdaySwipeRefreshLayout;"
    private const val SCROLL_FRAGMENT_TAB_CALLBACK_DESCRIPTOR =
        "Lcom/baidu/tieba/homepage/framework/indicator/ScrollFragmentTabHost\$z;"
    private const val AI_WRITE_HELPER_DESCRIPTOR = "Lcom/baidu/tieba/hs6;"
    private const val AI_SPRITE_MEME_PAN_DESCRIPTOR =
        "Lcom/baidu/tbadk/editortools/meme/pan/SpriteMemePan;"
    private const val IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR =
        "Lcom/baidu/tbadk/coreExtra/view/ImageJumpButtonLayout;"
    private const val ANDROID_VIEW_DESCRIPTOR = "Landroid/view/View;"
    private const val FRAME_LAYOUT_DESCRIPTOR = "Landroid/widget/FrameLayout;"
    private const val LINEAR_LAYOUT_DESCRIPTOR = "Landroid/widget/LinearLayout;"
    private const val VIEW_GROUP_DESCRIPTOR = "Landroid/view/ViewGroup;"
    private const val LINEAR_LAYOUT_LAYOUT_PARAMS_DESCRIPTOR =
        "Landroid/widget/LinearLayout\$LayoutParams;"
    private const val VIEW_MARGIN_LAYOUT_PARAMS_DESCRIPTOR =
        "Landroid/view/ViewGroup\$MarginLayoutParams;"
    private const val PB_AD_BID_ENDPOINT = "c/b/ad/adBid?cmd=309757&format=protobuf"
    private const val PB_COMMON_REQUEST_MODEL_DESCRIPTOR =
        "Lcom/baidu/tieba/pb/pb/main/newmodel/CommonRequestModel;"
    private const val PB_PAGE_BROWSER_REQUEST_MODEL_DESCRIPTOR =
        "Lcom/baidu/tieba/pb/pagebrowser/model/BaseRequestModel;"
    private const val KOTLIN_CONTINUATION_DESCRIPTOR = "Lkotlin/coroutines/Continuation;"
    private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
    private const val AGREE_DATA_DESCRIPTOR = "Lcom/baidu/tieba/tbadkcore/data/AgreeData;"
    private const val AGREE_DATA_HAS_AGREE_FIELD = "hasAgree"
    private const val AGREE_DATA_AGREE_TYPE_FIELD = "agreeType"
    private const val AI_PB_NEW_INPUT_INIT_SPRITE_MEME_METHOD = "e0"
    private const val AI_PB_NEW_INPUT_INIT_AI_WRITE_METHOD = "X"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_INIT_METHOD = "r"
    private const val HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_REFRESH_METHOD = "D"
    private const val PB_AD_BID_DEX_KIND_COMMON = "common"
    private const val PB_AD_BID_DEX_KIND_PAGE_BROWSER = "pageBrowser"

    fun scan(
        sourcePaths: List<String>,
        ownerClassNames: List<String>,
        cl: ClassLoader,
        logger: ScanLogger? = null,
    ): DexShareIconMatch? {
        val ownerDescriptors = ownerClassNames.associateBy { "L${it.replace('.', '/')};" }
        var best: DexShareIconMatch? = null
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                for ((descriptor, className) in ownerDescriptors) {
                    try {
                        val match = reader.findShareIconMatch(descriptor, className, cl)
                        if (match != null && (best == null || match.score > best!!.score)) best = match
                    } catch (t: Throwable) {
                        recordError(t)
                    }
                }
            }
        }
        recordDexScanIssue(logger, "ImageViewerNativeShareHook.IconDex", errorCount, firstError)
        return best
    }

    fun scanAutoRefresh(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAutoRefreshMatch> {
        val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
        val out = ArrayList<DexAutoRefreshMatch>(4)
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                try {
                    out.addAll(reader.findAutoRefreshMatches(ownerDescriptor))
                } catch (t: Throwable) {
                    recordError(t)
                }
            }
        }
        recordDexScanIssue(logger, "AutoRefreshHook.Dex", errorCount, firstError)
        return out
    }

    fun scanPbLikeAgreeClick(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexPbLikeAgreeClickMatch> {
        val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
        val out = ArrayList<DexPbLikeAgreeClickMatch>(4)
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                try {
                    out.addAll(reader.findPbLikeAgreeClickMatches(ownerDescriptor))
                } catch (t: Throwable) {
                    recordError(t)
                }
            }
        }
        recordDexScanIssue(logger, "PbLikeAutoReplyHook.AgreeClickDex", errorCount, firstError)
        return out
    }

    fun scanAiWriteInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAiComponentInitMatch> {
        val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
        val out = ArrayList<DexAiComponentInitMatch>(4)
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                try {
                    out.addAll(reader.findAiWriteInitMatches(ownerDescriptor))
                } catch (t: Throwable) {
                    recordError(t)
                }
            }
        }
        recordDexScanIssue(logger, "AiComponentDisableHook.AiWriteDex", errorCount, firstError)
        return out
    }

    fun scanSpriteMemeInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAiComponentInitMatch> {
        val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
        val out = ArrayList<DexAiComponentInitMatch>(4)
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                try {
                    out.addAll(reader.findSpriteMemeInitMatches(ownerDescriptor))
                } catch (t: Throwable) {
                    recordError(t)
                }
            }
        }
        recordDexScanIssue(logger, "AiComponentDisableHook.SpriteMemeDex", errorCount, firstError)
        return out
    }

    fun scanImageViewerJumpButtonInit(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexAiComponentInitMatch> {
        val ownerDescriptor = "L${ownerClassName.replace('.', '/')};"
        val out = ArrayList<DexAiComponentInitMatch>(4)
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                try {
                    out.addAll(reader.findImageViewerJumpButtonInitMatches(ownerDescriptor))
                } catch (t: Throwable) {
                    recordError(t)
                }
            }
        }
        recordDexScanIssue(logger, "AiComponentDisableHook.ImageViewerJumpButtonDex", errorCount, firstError)
        return out
    }

    private fun recordDexScanIssue(
        logger: ScanLogger?,
        tag: String,
        errorCount: Int,
        firstError: String?,
    ) {
        if (errorCount <= 0) return
        val detail = "source errors=$errorCount" +
            (firstError?.let { ", firstException=$it" } ?: "")
        val errors = HookSymbolScanSession.get()?.scanErrors
        if (errors != null) {
            recordScanIssue(logger, tag, errors, detail)
        } else {
            log(logger, "$tag: $detail")
        }
    }

    fun scanEnterForumCapsule(
        sourcePaths: List<String>,
        ownerClassName: String,
        logger: ScanLogger? = null,
    ): List<DexEnterForumCapsuleMethodMatch> {
        return scanEnterForumCapsules(sourcePaths, listOf(ownerClassName), logger)[ownerClassName].orEmpty()
    }

    fun scanPbAdBid(
        sourcePaths: List<String>,
        logger: ScanLogger? = null,
    ): DexPbAdBidRawScan {
        val modelMatches = ArrayList<DexPbAdBidModelMatch>(4)
        val requestDataMethodNames = LinkedHashSet<String>()
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                try {
                    modelMatches.addAll(reader.findPbAdBidModelMatches())
                } catch (t: Throwable) {
                    recordError(t)
                }
                try {
                    reader.findPbAdBidPageBrowserRequestDataMethod()?.let(requestDataMethodNames::add)
                } catch (t: Throwable) {
                    recordError(t)
                }
            }
        }
        if (errorCount > 0) {
            log(
                logger,
                "pbAdBidDex: source errors=$errorCount" +
                    (firstError?.let { ", firstException=$it" } ?: ""),
            )
        }
        val requestDataMethodName = when (requestDataMethodNames.size) {
            0 -> null
            1 -> requestDataMethodNames.first()
            else -> {
                log(
                    logger,
                    "pbAdBid.pageBrowserDex: requestData method ambiguous: " +
                        requestDataMethodNames.take(5).joinToString(","),
                )
                null
            }
        }
        return DexPbAdBidRawScan(
            modelMatches = modelMatches,
            pageBrowserRequestDataMethodName = requestDataMethodName,
        )
    }

    fun scanEnterForumCapsules(
        sourcePaths: List<String>,
        ownerClassNames: List<String>,
        logger: ScanLogger? = null,
    ): Map<String, List<DexEnterForumCapsuleMethodMatch>> {
        val owners = ownerClassNames
            .distinct()
            .associateBy { "L${it.replace('.', '/')};" }
        val out = LinkedHashMap<String, MutableList<DexEnterForumCapsuleMethodMatch>>()
        var errorCount = 0
        var firstError: String? = null
        fun recordError(t: Throwable) {
            errorCount++
            if (firstError == null) firstError = sanitizeScanStatusText(formatScanException(t))
        }
        for (sourcePath in sourcePaths) {
            val file = File(sourcePath)
            if (!file.isFile) continue
            scanDexFiles(file, ::recordError) { dexBytes ->
                val reader = try {
                    DexImage(dexBytes)
                } catch (t: Throwable) {
                    recordError(t)
                    return@scanDexFiles
                }
                for ((descriptor, className) in owners) {
                    val matches = reader.findEnterForumCapsuleMatches(descriptor)
                    if (matches.isNotEmpty()) {
                        out.getOrPut(className) { ArrayList(4) }.addAll(matches)
                    }
                }
            }
        }
        if (errorCount > 0) {
            log(
                logger,
                "homeNativeGlassEnterForumCapsuleDex: source errors=$errorCount" +
                    (firstError?.let { ", firstException=$it" } ?: ""),
            )
        }
        return out
    }

    private fun scanDexFiles(
        file: File,
        onError: (Throwable) -> Unit = {},
        block: (ByteArray) -> Unit,
    ) {
        if (file.extension.equals("dex", ignoreCase = true)) {
            runCatching { block(file.readBytes()) }.onFailure(onError)
            return
        }
        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!name.startsWith("classes") || !name.endsWith(".dex")) continue
                    zip.getInputStream(entry).use { input ->
                        block(input.readBytes())
                    }
                }
            }
        }.onFailure(onError)
    }

    private class DexImage(private val bytes: ByteArray) {
        private val stringIdsSize = intAt(56)
        private val stringIdsOff = intAt(60)
        private val typeIdsSize = intAt(64)
        private val typeIdsOff = intAt(68)
        private val protoIdsSize = intAt(72)
        private val protoIdsOff = intAt(76)
        private val fieldIdsSize = intAt(80)
        private val fieldIdsOff = intAt(84)
        private val methodIdsSize = intAt(88)
        private val methodIdsOff = intAt(92)
        private val classDefsSize = intAt(96)
        private val classDefsOff = intAt(100)
        private val stringCache = HashMap<Int, String?>()
        private val typeCache = HashMap<Int, String?>()
        private val classSuperCache = HashMap<String, String?>()
        private val aiWriteListenerEvidenceCache = HashMap<String, Boolean>()

        fun findAutoRefreshMatches(ownerDescriptor: String): List<DexAutoRefreshMatch> {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return emptyList()
                return scanAutoRefreshClassData(classDataOff)
            }
            return emptyList()
        }

        fun findPbLikeAgreeClickMatches(ownerDescriptor: String): List<DexPbLikeAgreeClickMatch> {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return emptyList()
                return scanPbLikeAgreeClickClassData(classDataOff)
            }
            return emptyList()
        }

        fun findAiWriteInitMatches(ownerDescriptor: String): List<DexAiComponentInitMatch> {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return emptyList()
                return scanAiWriteInitClassData(classDataOff)
            }
            return emptyList()
        }

        fun findSpriteMemeInitMatches(ownerDescriptor: String): List<DexAiComponentInitMatch> {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return emptyList()
                return scanSpriteMemeInitClassData(classDataOff)
            }
            return emptyList()
        }

        fun findImageViewerJumpButtonInitMatches(ownerDescriptor: String): List<DexAiComponentInitMatch> {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return emptyList()
                return scanImageViewerJumpButtonInitClassData(classDataOff)
            }
            return emptyList()
        }

        fun findEnterForumCapsuleMatches(ownerDescriptor: String): List<DexEnterForumCapsuleMethodMatch> {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return emptyList()
                return scanEnterForumCapsuleClassData(classDataOff, ownerDescriptor)
            }
            return emptyList()
        }

        fun findPbAdBidModelMatches(): List<DexPbAdBidModelMatch> {
            val out = ArrayList<DexPbAdBidModelMatch>(4)
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val descriptor = typeDescriptor(intAt(classDefOff)) ?: continue
                if (!descriptor.startsWith("Lcom/baidu/tieba/")) continue
                val kind = when {
                    extendsDescriptor(descriptor, PB_COMMON_REQUEST_MODEL_DESCRIPTOR) ->
                        PB_AD_BID_DEX_KIND_COMMON
                    extendsDescriptor(descriptor, PB_PAGE_BROWSER_REQUEST_MODEL_DESCRIPTOR) ->
                        PB_AD_BID_DEX_KIND_PAGE_BROWSER
                    else -> null
                } ?: continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) continue
                out.addAll(scanPbAdBidModelClassData(classDataOff, descriptor, kind))
            }
            return out
        }

        fun findPbAdBidPageBrowserRequestDataMethod(): String? {
            val classDataOff = classDataOffset(PB_PAGE_BROWSER_REQUEST_MODEL_DESCRIPTOR) ?: return null
            val names = scanMethodNamesByProto(
                classDataOff = classDataOff,
                returnDescriptor = OBJECT_DESCRIPTOR,
                parameterDescriptors = listOf(KOTLIN_CONTINUATION_DESCRIPTOR),
            )
            return names.singleOrNull()
        }

        private fun classDataOffset(descriptor: String): Int? {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != descriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                return classDataOff.takeIf { it != 0 }
            }
            return null
        }

        private fun scanPbAdBidModelClassData(
            classDataOff: Int,
            ownerDescriptor: String,
            kind: String,
        ): List<DexPbAdBidModelMatch> {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            val out = ArrayList<DexPbAdBidModelMatch>(2)
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanPbAdBidModelCodeItem(method, ownerDescriptor, kind)?.let(out::add)
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanPbAdBidModelCodeItem(method, ownerDescriptor, kind)?.let(out::add)
            }
            return out
        }

        private fun scanMethodNamesByProto(
            classDataOff: Int,
            returnDescriptor: String,
            parameterDescriptors: List<String>,
        ): List<String> {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            val out = ArrayList<String>(2)
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                if (!isStaticAccess(method.accessFlags) &&
                    methodReturnDescriptor(methodIdx) == returnDescriptor &&
                    methodParameterDescriptors(methodIdx) == parameterDescriptors
                ) {
                    methodName(methodIdx)?.takeIf { it.isNotBlank() }?.let(out::add)
                }
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                if (!isStaticAccess(method.accessFlags) &&
                    methodReturnDescriptor(methodIdx) == returnDescriptor &&
                    methodParameterDescriptors(methodIdx) == parameterDescriptors
                ) {
                    methodName(methodIdx)?.takeIf { it.isNotBlank() }?.let(out::add)
                }
            }
            return out.distinct()
        }

        private fun hasAiWriteClickListenerEvidence(listenerDescriptor: String): Boolean {
            aiWriteListenerEvidenceCache[listenerDescriptor]?.let { return it }
            val classDataOff = classDataOffset(listenerDescriptor) ?: run {
                aiWriteListenerEvidenceCache[listenerDescriptor] = false
                return false
            }
            val matched = scanAiWriteListenerClassData(classDataOff)
            aiWriteListenerEvidenceCache[listenerDescriptor] = matched
            return matched
        }

        fun findShareIconMatch(
            ownerDescriptor: String,
            ownerClassName: String,
            cl: ClassLoader,
        ): DexShareIconMatch? {
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                val classIdx = intAt(classDefOff)
                if (typeDescriptor(classIdx) != ownerDescriptor) continue
                val classDataOff = intAt(classDefOff + 24)
                if (classDataOff == 0) return null
                return scanClassData(classDataOff, ownerClassName, cl)
            }
            return null
        }

        private fun scanAutoRefreshClassData(classDataOff: Int): List<DexAutoRefreshMatch> {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            val out = ArrayList<DexAutoRefreshMatch>(4)
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanAutoRefreshCodeItem(method.codeOff, methodName(methodIdx))?.let(out::add)
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanAutoRefreshCodeItem(method.codeOff, methodName(methodIdx))?.let(out::add)
            }
            return out
        }

        private fun scanPbLikeAgreeClickClassData(classDataOff: Int): List<DexPbLikeAgreeClickMatch> {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            val out = ArrayList<DexPbLikeAgreeClickMatch>(2)
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanPbLikeAgreeClickCodeItem(method)?.let(out::add)
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanPbLikeAgreeClickCodeItem(method)?.let(out::add)
            }
            return out
        }

        private fun scanAiWriteInitClassData(classDataOff: Int): List<DexAiComponentInitMatch> {
            return scanAiComponentInitClassData(classDataOff, ::scanAiWriteInitCodeItem)
        }

        private fun scanSpriteMemeInitClassData(classDataOff: Int): List<DexAiComponentInitMatch> {
            return scanAiComponentInitClassData(classDataOff, ::scanSpriteMemeInitCodeItem)
        }

        private fun scanImageViewerJumpButtonInitClassData(classDataOff: Int): List<DexAiComponentInitMatch> {
            return scanAiComponentInitClassData(classDataOff, ::scanImageViewerJumpButtonInitCodeItem)
        }

        private fun scanEnterForumCapsuleClassData(
            classDataOff: Int,
            ownerDescriptor: String,
        ): List<DexEnterForumCapsuleMethodMatch> {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            val out = ArrayList<DexEnterForumCapsuleMethodMatch>(4)
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                out.addAll(
                    scanEnterForumCapsuleCodeItem(
                        method.codeOff,
                        methodName(methodIdx),
                        ownerDescriptor,
                    ),
                )
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                out.addAll(
                    scanEnterForumCapsuleCodeItem(
                        method.codeOff,
                        methodName(methodIdx),
                        ownerDescriptor,
                    ),
                )
            }
            return out
        }

        private fun scanAiWriteListenerClassData(classDataOff: Int): Boolean {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                if (scanAiWriteListenerCodeItem(method.codeOff, methodName(methodIdx))) return true
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                if (scanAiWriteListenerCodeItem(method.codeOff, methodName(methodIdx))) return true
            }
            return false
        }

        private fun scanAiComponentInitClassData(
            classDataOff: Int,
            scanner: (Int, String?) -> DexAiComponentInitMatch?,
        ): List<DexAiComponentInitMatch> {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            val out = ArrayList<DexAiComponentInitMatch>(4)
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanner(method.codeOff, methodName(methodIdx))?.let(out::add)
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                scanner(method.codeOff, methodName(methodIdx))?.let(out::add)
            }
            return out
        }

        private fun scanClassData(
            classDataOff: Int,
            ownerClassName: String,
            cl: ClassLoader,
        ): DexShareIconMatch? {
            var cursor = classDataOff
            val staticFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val instanceFieldsSize = readUleb128(cursor).also { cursor = it.next }.value
            val directMethodsSize = readUleb128(cursor).also { cursor = it.next }.value
            val virtualMethodsSize = readUleb128(cursor).also { cursor = it.next }.value

            repeat(staticFieldsSize) { cursor = skipEncodedField(cursor) }
            repeat(instanceFieldsSize) { cursor = skipEncodedField(cursor) }

            var best: DexShareIconMatch? = null
            var methodIdx = 0
            repeat(directMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                val match = scanCodeItem(method.codeOff, ownerClassName, methodName(methodIdx), cl)
                if (match != null && (best == null || match.score > best.score)) best = match
            }

            methodIdx = 0
            repeat(virtualMethodsSize) {
                val method = readEncodedMethod(cursor, methodIdx)
                cursor = method.next
                methodIdx = method.methodIdx
                val match = scanCodeItem(method.codeOff, ownerClassName, methodName(methodIdx), cl)
                if (match != null && (best == null || match.score > best.score)) best = match
            }
            return best
        }

        private fun scanPbAdBidModelCodeItem(
            method: EncodedMethod,
            ownerDescriptor: String,
            kind: String,
        ): DexPbAdBidModelMatch? {
            if (isStaticAccess(method.accessFlags)) return null
            if (!codeContainsString(method.codeOff, PB_AD_BID_ENDPOINT)) return null
            val ownerClassName = descriptorToClassName(ownerDescriptor) ?: return null
            val methodName = methodName(method.methodIdx)?.takeIf { it.isNotBlank() } ?: return null
            var score = 260
            val evidence = ArrayList<String>(4)
            evidence.add("endpoint")
            when (kind) {
                PB_AD_BID_DEX_KIND_COMMON -> {
                    score += 40
                    evidence.add("commonBase")
                }
                PB_AD_BID_DEX_KIND_PAGE_BROWSER -> {
                    score += 40
                    evidence.add("pageBrowserBase")
                }
            }
            if (methodReturnDescriptor(method.methodIdx)?.endsWith("/u3b;") == true) {
                score += 25
                evidence.add("requestReturn")
            }
            if (methodParameterDescriptors(method.methodIdx).isEmpty()) {
                score += 10
                evidence.add("noArgs")
            }
            return DexPbAdBidModelMatch(
                className = ownerClassName,
                requestImplMethodName = methodName,
                kind = kind,
                score = score,
                evidence = evidence.joinToString(","),
            )
        }

        private fun codeContainsString(codeOff: Int, expected: String): Boolean {
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return false
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return false
            for (i in 0 until insnsSize) {
                val offset = insnsOff + i * 2
                val unit = ushortAt(offset)
                when (unit and 0xFF) {
                    0x1A -> {
                        val value = stringAt(ushortAt(offset + 2)).orEmpty()
                        if (value == expected) return true
                    }
                    0x1B -> {
                        val value = stringAt(intAt(offset + 2)).orEmpty()
                        if (value == expected) return true
                    }
                }
            }
            return false
        }

        private fun skipEncodedField(offset: Int): Int {
            var cursor = offset
            cursor = readUleb128(cursor).next
            cursor = readUleb128(cursor).next
            return cursor
        }

        private fun readEncodedMethod(offset: Int, previousMethodIdx: Int): EncodedMethod {
            var cursor = offset
            val methodIdxDiff = readUleb128(cursor).also { cursor = it.next }.value
            val accessFlags = readUleb128(cursor).also { cursor = it.next }.value
            val codeOff = readUleb128(cursor).also { cursor = it.next }.value
            return EncodedMethod(previousMethodIdx + methodIdxDiff, accessFlags, codeOff, cursor)
        }

        private fun scanPbLikeAgreeClickCodeItem(method: EncodedMethod): DexPbLikeAgreeClickMatch? {
            if (isStaticAccess(method.accessFlags)) return null
            val methodName = methodName(method.methodIdx)?.takeIf { it.isNotBlank() } ?: return null
            if (methodReturnDescriptor(method.methodIdx) != "V") return null
            if (methodParameterDescriptors(method.methodIdx) != listOf(ANDROID_VIEW_DESCRIPTOR)) return null
            val codeOff = method.codeOff
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

            var readsHasAgree = 0
            var readsAgreeType = 0
            var writesHasAgree = 0
            var writesAgreeType = 0
            var hasViewGetId = false
            var hasAgreeDataField = false
            var hasBackendCall = false

            for (i in 0 until insnsSize) {
                val unit = ushortAt(insnsOff + i * 2)
                val op = unit and 0xFF
                when {
                    op in 0x52..0x5F -> {
                        val fieldIdx = ushortAt(insnsOff + (i + 1) * 2)
                        if (fieldTypeDescriptor(fieldIdx) == AGREE_DATA_DESCRIPTOR) {
                            hasAgreeDataField = true
                        }
                        if (fieldClassDescriptor(fieldIdx) != AGREE_DATA_DESCRIPTOR) continue
                        when (fieldName(fieldIdx)) {
                            AGREE_DATA_HAS_AGREE_FIELD -> {
                                if (isDexInstancePut(op)) writesHasAgree++ else readsHasAgree++
                            }
                            AGREE_DATA_AGREE_TYPE_FIELD -> {
                                if (isDexInstancePut(op)) writesAgreeType++ else readsAgreeType++
                            }
                        }
                    }
                    op in 0x6E..0x72 || op in 0x74..0x78 -> {
                        val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                        if (isViewGetId(methodIdx)) {
                            hasViewGetId = true
                        }
                        if (isAgreeBackendInvoke(methodIdx)) {
                            hasBackendCall = true
                        }
                    }
                }
            }

            val hasStateWrite = writesHasAgree > 0 && writesAgreeType > 0
            val hasStateRead = readsHasAgree > 0 || readsAgreeType > 0
            if (!hasStateWrite || !hasStateRead || !hasViewGetId) return null

            var score = 0
            val evidence = ArrayList<String>(8)
            score += writesHasAgree.coerceAtMost(3) * 80
            evidence.add("writeHasAgree=$writesHasAgree")
            score += writesAgreeType.coerceAtMost(3) * 70
            evidence.add("writeAgreeType=$writesAgreeType")
            if (readsHasAgree > 0) {
                score += 45
                evidence.add("readHasAgree")
            }
            if (readsAgreeType > 0) {
                score += 40
                evidence.add("readAgreeType")
            }
            if (hasViewGetId) {
                score += 70
                evidence.add("viewGetId")
            }
            if (hasAgreeDataField) {
                score += 25
                evidence.add("agreeDataField")
            }
            if (hasBackendCall) {
                score += 35
                evidence.add("backend")
            }
            if (methodName.length <= 3) score += 8
            if (score < 360) return null
            return DexPbLikeAgreeClickMatch(methodName, score, evidence.joinToString(","))
        }

        private fun scanAutoRefreshCodeItem(
            codeOff: Int,
            ownerMethodName: String?,
        ): DexAutoRefreshMatch? {
            val methodName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

            var hasSelectionTop = false
            var hasSelectionCall = false
            var hasSetRefreshing = false
            var hasSetRefreshingTrue = false
            var hasSwipeRefreshInvoke = false
            var hasScrollTabNotify = false
            for (i in 0 until insnsSize) {
                val unit = ushortAt(insnsOff + i * 2)
                val op = unit and 0xFF
                val is35cInvoke = op in 0x6E..0x72
                val isRangeInvoke = op in 0x74..0x78
                if (!is35cInvoke && !isRangeInvoke) continue

                val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)

                if (isListSetSelection(methodIdx)) {
                    hasSelectionCall = true
                    val selectionArgReg = invokeRegs.getOrNull(1)
                    if (selectionArgReg != null && findIntConstBefore(insnsOff, i, selectionArgReg) == 0) {
                        hasSelectionTop = true
                    }
                }

                if (isSwipeRefreshInvoke(methodIdx)) {
                    hasSwipeRefreshInvoke = true
                }

                if (isSwipeRefreshSetRefreshing(methodIdx)) {
                    hasSetRefreshing = true
                    val refreshingArgReg = invokeRegs.getOrNull(1)
                    if (refreshingArgReg != null && findBooleanConstBefore(insnsOff, i, refreshingArgReg) == true) {
                        hasSetRefreshingTrue = true
                    }
                }

                if (isScrollTabNotify(methodIdx)) {
                    hasScrollTabNotify = true
                }
            }

            val hasStrongRefreshEvidence = hasSetRefreshingTrue ||
                (hasSelectionTop && (hasSetRefreshing || hasSwipeRefreshInvoke || hasScrollTabNotify))
            if (!hasStrongRefreshEvidence) return null

            var score = 0
            val evidence = ArrayList<String>(6)
            if (hasSelectionTop) {
                score += 130
                evidence.add("selection0")
            } else if (hasSelectionCall) {
                score += 35
                evidence.add("selection")
            }
            if (hasSetRefreshingTrue) {
                score += 170
                evidence.add("setRefreshingTrue")
            } else if (hasSetRefreshing) {
                score += 70
                evidence.add("setRefreshing")
            }
            if (hasSwipeRefreshInvoke) {
                score += 35
                evidence.add("swipeRefresh")
            }
            if (hasScrollTabNotify) {
                score += 50
                evidence.add("scrollTabNotify")
            }
            if (methodName == "w1") score += 30
            if (methodName.length <= 3) score += 8
            if (methodName.endsWith("1")) score += 6
            if (score < 150) return null
            return DexAutoRefreshMatch(methodName, score, evidence.joinToString(","))
        }

        private fun scanEnterForumCapsuleCodeItem(
            codeOff: Int,
            ownerMethodName: String?,
            ownerDescriptor: String,
        ): List<DexEnterForumCapsuleMethodMatch> {
            val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return emptyList()
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return emptyList()
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return emptyList()

            var hasAddCustomView = false
            var hasFindViewById = false
            var hasSetOnClickListener = false
            var hasTextUtilsIsEmpty = false
            var hasEmManagerFrom = false
            var hasEmBackground = false
            var hasSetBackgroundResource = false
            var hasSetVisibility = false
            var hasSetVisibilityVisible = false
            val putViewFields = LinkedHashMap<String, Int>()
            val getViewFields = LinkedHashMap<String, Int>()
            val putPbBarFields = LinkedHashMap<String, Int>()
            val putEmTextFields = LinkedHashMap<String, Int>()
            val getStringFields = LinkedHashMap<String, Int>()

            fun count(map: MutableMap<String, Int>, key: String) {
                map[key] = (map[key] ?: 0) + 1
            }

            for (i in 0 until insnsSize) {
                val offset = insnsOff + i * 2
                val unit = ushortAt(offset)
                val op = unit and 0xFF
                if (op in 0x52..0x5F) {
                    val fieldIdx = ushortAt(offset + 2)
                    if (fieldClassDescriptor(fieldIdx) == ownerDescriptor) {
                        val fieldName = fieldName(fieldIdx).orEmpty()
                        val type = fieldTypeDescriptor(fieldIdx).orEmpty()
                        if (fieldName.isNotBlank()) {
                            if (op == 0x54) {
                                when {
                                    isViewLikeDescriptor(type) -> count(getViewFields, fieldName)
                                    type == STRING_DESCRIPTOR -> count(getStringFields, fieldName)
                                }
                            } else if (op == 0x5B) {
                                when {
                                    type == PB_BAR_IMAGE_VIEW_DESCRIPTOR -> count(putPbBarFields, fieldName)
                                    type == EM_TEXT_VIEW_DESCRIPTOR -> count(putEmTextFields, fieldName)
                                    isViewLikeDescriptor(type) -> count(putViewFields, fieldName)
                                }
                            }
                        }
                    }
                    continue
                }

                val is35cInvoke = op in 0x6E..0x72
                val isRangeInvoke = op in 0x74..0x78
                if (!is35cInvoke && !isRangeInvoke) continue
                val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                when {
                    isNavigationBarAddCustomView(methodIdx) -> hasAddCustomView = true
                    isViewFindViewById(methodIdx) -> hasFindViewById = true
                    isViewSetOnClickListener(methodIdx) -> hasSetOnClickListener = true
                    isTextUtilsIsEmpty(methodIdx) -> hasTextUtilsIsEmpty = true
                    isEmManagerFrom(methodIdx) -> hasEmManagerFrom = true
                    isEmManagerBackgroundMethod(methodIdx) -> hasEmBackground = true
                    isViewSetBackgroundResource(methodIdx) -> hasSetBackgroundResource = true
                    isViewSetVisibility(methodIdx) -> {
                        hasSetVisibility = true
                        val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)
                        val visibilityReg = invokeRegs.getOrNull(1)
                        if (visibilityReg != null && findIntConstBefore(insnsOff, i, visibilityReg) == View.VISIBLE) {
                            hasSetVisibilityVisible = true
                        }
                    }
                }
            }

            val out = ArrayList<DexEnterForumCapsuleMethodMatch>(2)
            val initViewField = bestDexFieldName(putViewFields)
            val hasInitEvidence = initViewField != null &&
                hasAddCustomView &&
                hasFindViewById &&
                (hasSetOnClickListener || putPbBarFields.isNotEmpty() || putEmTextFields.isNotEmpty())
            if (hasInitEvidence) {
                var score = 0
                val evidence = ArrayList<String>(8)
                score += 120
                evidence.add("addCustomView")
                score += 55
                evidence.add("findViewById")
                score += 70
                evidence.add("viewField=$initViewField")
                if (hasSetOnClickListener) {
                    score += 40
                    evidence.add("click")
                }
                if (putPbBarFields.isNotEmpty()) {
                    score += 35
                    evidence.add("pbBar")
                }
                if (putEmTextFields.isNotEmpty()) {
                    score += 35
                    evidence.add("emText")
                }
                if (ownerName == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_INIT_METHOD) score += 16
                if (ownerName.length <= 2) score += 8
                if (score >= 260) {
                    out.add(
                        DexEnterForumCapsuleMethodMatch(
                            ownerMethodName = ownerName,
                            kind = DexEnterForumCapsuleMethodKind.INIT,
                            score = score,
                            evidence = evidence.joinToString(","),
                            viewFieldName = initViewField,
                        ),
                    )
                }
            }

            val refreshViewField = bestDexFieldName(getViewFields)
            val refreshTitleField = bestDexFieldName(getStringFields)
            val hasRefreshBackground = hasEmBackground || hasSetBackgroundResource
            val hasRefreshEvidence = refreshViewField != null &&
                refreshTitleField != null &&
                (hasTextUtilsIsEmpty || hasSetVisibility) &&
                (hasRefreshBackground || hasEmManagerFrom)
            if (hasRefreshEvidence) {
                var score = 0
                val evidence = ArrayList<String>(8)
                score += 70
                evidence.add("viewField=$refreshViewField")
                score += 65
                evidence.add("titleField=$refreshTitleField")
                if (hasTextUtilsIsEmpty) {
                    score += 70
                    evidence.add("isEmpty")
                }
                if (hasEmBackground) {
                    score += 85
                    evidence.add("emBackground")
                } else if (hasEmManagerFrom) {
                    score += 35
                    evidence.add("emManager")
                }
                if (hasSetBackgroundResource) {
                    score += 45
                    evidence.add("clearBackground")
                }
                if (hasSetVisibilityVisible) {
                    score += 20
                    evidence.add("visible")
                } else if (hasSetVisibility) {
                    score += 10
                    evidence.add("visibility")
                }
                if (ownerName == HOME_NATIVE_GLASS_ENTER_FORUM_CAPSULE_REFRESH_METHOD) score += 16
                if (ownerName.length <= 2) score += 8
                if (score >= 235) {
                    out.add(
                        DexEnterForumCapsuleMethodMatch(
                            ownerMethodName = ownerName,
                            kind = DexEnterForumCapsuleMethodKind.REFRESH,
                            score = score,
                            evidence = evidence.joinToString(","),
                            viewFieldName = refreshViewField,
                            titleFieldName = refreshTitleField,
                        ),
                    )
                }
            }
            return out
        }

        private fun scanAiWriteListenerCodeItem(
            codeOff: Int,
            ownerMethodName: String?,
        ): Boolean {
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return false
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return false

            var hasAiWriteReply = false
            var hasAiCapacityHelper = false
            for (i in 0 until insnsSize) {
                val offset = insnsOff + i * 2
                val unit = ushortAt(offset)
                val op = unit and 0xFF
                when {
                    op == 0x1A -> {
                        val value = stringAt(ushortAt(offset + 2)).orEmpty()
                        if (isAiWriteReplyString(value)) hasAiWriteReply = true
                    }
                    op == 0x1B -> {
                        val value = stringAt(intAt(offset + 2)).orEmpty()
                        if (isAiWriteReplyString(value)) hasAiWriteReply = true
                    }
                    op in 0x52..0x6D -> {
                        val fieldIdx = ushortAt(offset + 2)
                        val name = fieldName(fieldIdx).orEmpty()
                        if (isAiWriteReplyString(name)) hasAiWriteReply = true
                        if (
                            isAiCapacityDescriptor(fieldClassDescriptor(fieldIdx).orEmpty()) ||
                            isAiCapacityDescriptor(fieldTypeDescriptor(fieldIdx).orEmpty())
                        ) {
                            hasAiCapacityHelper = true
                        }
                    }
                    op in 0x6E..0x72 || op in 0x74..0x78 -> {
                        val methodIdx = ushortAt(offset + 2)
                        if (isAiCapacityDescriptor(methodClassDescriptor(methodIdx).orEmpty())) {
                            hasAiCapacityHelper = true
                        }
                    }
                }
                if (hasAiWriteReply || (hasAiCapacityHelper && ownerMethodName == "onClick")) return true
            }
            return false
        }

        private fun scanAiWriteInitCodeItem(
            codeOff: Int,
            ownerMethodName: String?,
        ): DexAiComponentInitMatch? {
            val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

            var hasHelperFactory = false
            var hasFrameLayoutCtor = false
            var hasFrameLayoutNewInstance = false
            var hasFrameLayoutFieldPut = false
            var hasViewLikeNewInstance = false
            var hasViewLikeFieldPut = false
            var hasSetOnClickListener = false
            var hasSetVisibility = false
            var hasSetVisibilityGone = false
            var hasLayoutParams = false
            var hasMargins = false
            val listenerDescriptors = linkedSetOf<String>()

            for (i in 0 until insnsSize) {
                val unit = ushortAt(insnsOff + i * 2)
                val op = unit and 0xFF
                if (op == 0x22) {
                    val descriptor = typeDescriptor(ushortAt(insnsOff + (i + 1) * 2)).orEmpty()
                    if (descriptor == FRAME_LAYOUT_DESCRIPTOR) {
                        hasFrameLayoutNewInstance = true
                    }
                    if (isViewLikeDescriptor(descriptor)) {
                        hasViewLikeNewInstance = true
                    } else if (isAppClassDescriptor(descriptor)) {
                        listenerDescriptors.add(descriptor)
                    }
                    continue
                }
                if (op == 0x5B) {
                    val fieldIdx = ushortAt(insnsOff + (i + 1) * 2)
                    if (isFrameLayoutField(fieldIdx)) {
                        hasFrameLayoutFieldPut = true
                    }
                    if (isViewLikeField(fieldIdx)) {
                        hasViewLikeFieldPut = true
                    }
                    continue
                }

                val is35cInvoke = op in 0x6E..0x72
                val isRangeInvoke = op in 0x74..0x78
                if (!is35cInvoke && !isRangeInvoke) continue

                val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                if (isAiWriteHelperFactory(methodIdx)) {
                    hasHelperFactory = true
                }
                if (isFrameLayoutConstructor(methodIdx)) {
                    hasFrameLayoutCtor = true
                }
                if (isViewSetOnClickListener(methodIdx)) {
                    hasSetOnClickListener = true
                }
                if (isViewGetLayoutParams(methodIdx)) {
                    hasLayoutParams = true
                }
                if (isViewMarginSetMargins(methodIdx)) {
                    hasMargins = true
                }
                if (isViewSetVisibility(methodIdx)) {
                    hasSetVisibility = true
                    val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)
                    val visibilityReg = invokeRegs.getOrNull(1)
                    if (visibilityReg != null && findIntConstBefore(insnsOff, i, visibilityReg) == View.GONE) {
                        hasSetVisibilityGone = true
                    }
                }
            }

            var score = 0
            val evidence = ArrayList<String>(12)
            val hasAiWriteClickListener = listenerDescriptors.any(::hasAiWriteClickListenerEvidence)
            if (hasAiWriteClickListener) {
                score += 170
                evidence.add("aiClickListener")
            }
            if (hasHelperFactory) {
                score += 150
                evidence.add("hs6Factory")
            }
            if (hasFrameLayoutFieldPut) {
                score += 85
                evidence.add("frameField")
            }
            if (hasFrameLayoutCtor) {
                score += 45
                evidence.add("frameCtor")
            }
            if (hasFrameLayoutNewInstance) {
                score += 45
                evidence.add("frameNew")
            }
            if (!hasFrameLayoutFieldPut && hasViewLikeFieldPut) {
                score += 35
                evidence.add("viewField")
            }
            if (!hasFrameLayoutCtor && !hasFrameLayoutNewInstance && hasViewLikeNewInstance) {
                score += 25
                evidence.add("viewNew")
            }
            if (hasSetOnClickListener) {
                score += 80
                evidence.add("click")
            }
            if (hasSetVisibilityGone) {
                score += 90
                evidence.add("visibilityGone")
            } else if (hasSetVisibility) {
                score += 40
                evidence.add("visibility")
            }
            if (hasLayoutParams) {
                score += 20
                evidence.add("layoutParams")
            }
            if (hasMargins) {
                score += 25
                evidence.add("margins")
            }
            if (ownerName == AI_PB_NEW_INPUT_INIT_AI_WRITE_METHOD) score += 20
            if (ownerName.length <= 3) score += 8
            val hasViewSlotEvidence = hasHelperFactory ||
                hasFrameLayoutFieldPut ||
                hasFrameLayoutCtor ||
                hasFrameLayoutNewInstance ||
                (hasViewLikeFieldPut && (hasLayoutParams || hasMargins || hasAiWriteClickListener)) ||
                (hasLayoutParams && hasMargins)
            val hasUiPlacementEvidence = hasSetVisibilityGone ||
                hasSetVisibility ||
                hasLayoutParams ||
                hasMargins ||
                hasAiWriteClickListener
            val hasStrongEvidence = hasSetOnClickListener &&
                hasUiPlacementEvidence &&
                hasViewSlotEvidence &&
                (hasAiWriteClickListener || hasHelperFactory || hasFrameLayoutFieldPut || hasFrameLayoutCtor || hasFrameLayoutNewInstance)
            if (!hasStrongEvidence && score < 90) return null
            if (hasStrongEvidence && score < 170) return null
            return DexAiComponentInitMatch(
                ownerMethodName = ownerName,
                score = score,
                evidence = evidence.joinToString(","),
                strong = hasStrongEvidence,
            )
        }

        private fun scanSpriteMemeInitCodeItem(
            codeOff: Int,
            ownerMethodName: String?,
        ): DexAiComponentInitMatch? {
            val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

            var hasSpriteMemeCtor = false
            var hasListPanGetter = false
            var hasHintPanGetter = false
            var hasSetLayoutParams = false
            var hasSetPbEventCallBack = false

            for (i in 0 until insnsSize) {
                val unit = ushortAt(insnsOff + i * 2)
                val op = unit and 0xFF
                if (op !in 0x6E..0x72 && op !in 0x74..0x78) continue
                val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                if (isSpriteMemePanConstructor(methodIdx)) {
                    hasSpriteMemeCtor = true
                }
                if (isSpriteMemePanMethod(methodIdx, "getSpriteMemeListPan")) {
                    hasListPanGetter = true
                }
                if (isSpriteMemePanMethod(methodIdx, "getSpriteMemeHintPan")) {
                    hasHintPanGetter = true
                }
                if (isViewSetLayoutParams(methodIdx)) {
                    hasSetLayoutParams = true
                }
                if (methodName(methodIdx) == "setPbEventCallBack") {
                    hasSetPbEventCallBack = true
                }
            }

            val hasStrongEvidence = hasSpriteMemeCtor && (hasListPanGetter || hasHintPanGetter)
            if (!hasStrongEvidence) return null

            var score = 0
            val evidence = ArrayList<String>(5)
            if (hasSpriteMemeCtor) {
                score += 150
                evidence.add("spriteCtor")
            }
            if (hasListPanGetter) {
                score += 60
                evidence.add("listPan")
            }
            if (hasHintPanGetter) {
                score += 60
                evidence.add("hintPan")
            }
            if (hasSetLayoutParams) {
                score += 25
                evidence.add("layoutParams")
            }
            if (hasSetPbEventCallBack) {
                score += 35
                evidence.add("callback")
            }
            if (ownerName == AI_PB_NEW_INPUT_INIT_SPRITE_MEME_METHOD) score += 20
            if (ownerName.length <= 3) score += 8
            if (score < 190) return null
            return DexAiComponentInitMatch(ownerName, score, evidence.joinToString(","))
        }

        private fun scanImageViewerJumpButtonInitCodeItem(
            codeOff: Int,
            ownerMethodName: String?,
        ): DexAiComponentInitMatch? {
            val ownerName = ownerMethodName?.takeIf { it.isNotBlank() } ?: return null
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

            var hasLayoutNewInstance = false
            var hasLayoutCtor = false
            var hasLayoutFieldPut = false
            var hasVisibilityGone = false
            var hasVisibilityCall = false
            var hasLinearLayoutParams = false
            var hasAddView = false

            for (i in 0 until insnsSize) {
                val unit = ushortAt(insnsOff + i * 2)
                val op = unit and 0xFF
                if (op == 0x22) {
                    val descriptor = typeDescriptor(ushortAt(insnsOff + (i + 1) * 2)).orEmpty()
                    if (descriptor == IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR) {
                        hasLayoutNewInstance = true
                    }
                    continue
                }
                if (op == 0x5B) {
                    val fieldIdx = ushortAt(insnsOff + (i + 1) * 2)
                    if (fieldTypeDescriptor(fieldIdx) == IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR) {
                        hasLayoutFieldPut = true
                    }
                    continue
                }

                val is35cInvoke = op in 0x6E..0x72
                val isRangeInvoke = op in 0x74..0x78
                if (!is35cInvoke && !isRangeInvoke) continue

                val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                if (isImageJumpButtonLayoutConstructor(methodIdx)) {
                    hasLayoutCtor = true
                }
                if (isLinearLayoutLayoutParamsConstructor(methodIdx)) {
                    hasLinearLayoutParams = true
                }
                if (isViewGroupAddView(methodIdx)) {
                    hasAddView = true
                }
                if (isViewSetVisibility(methodIdx)) {
                    hasVisibilityCall = true
                    val invokeRegs = invokeRegisters(insnsOff, i, unit, is35cInvoke)
                    val visibilityReg = invokeRegs.getOrNull(1)
                    if (visibilityReg != null && findIntConstBefore(insnsOff, i, visibilityReg) == View.GONE) {
                        hasVisibilityGone = true
                    }
                }
            }

            val hasStrongEvidence = (hasLayoutCtor || hasLayoutNewInstance) &&
                hasLayoutFieldPut &&
                (hasAddView || hasVisibilityGone)
            if (!hasStrongEvidence) return null

            var score = 0
            val evidence = ArrayList<String>(7)
            if (hasLayoutCtor) {
                score += 145
                evidence.add("jumpCtor")
            }
            if (hasLayoutNewInstance) {
                score += 90
                evidence.add("jumpNew")
            }
            if (hasLayoutFieldPut) {
                score += 95
                evidence.add("jumpField")
            }
            if (hasAddView) {
                score += 65
                evidence.add("addView")
            }
            if (hasVisibilityGone) {
                score += 45
                evidence.add("visibilityGone")
            } else if (hasVisibilityCall) {
                score += 20
                evidence.add("visibility")
            }
            if (hasLinearLayoutParams) {
                score += 25
                evidence.add("linearParams")
            }
            if (ownerName == "r") score += 18
            if (ownerName.length <= 2) score += 8
            if (score < 280) return null
            return DexAiComponentInitMatch(ownerName, score, evidence.joinToString(","))
        }

        private fun scanCodeItem(
            codeOff: Int,
            ownerClassName: String,
            ownerMethodName: String?,
            cl: ClassLoader,
        ): DexShareIconMatch? {
            if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
            val insnsSize = intAt(codeOff + 12)
            val insnsOff = codeOff + 16
            if (insnsOff <= 0 || insnsOff + insnsSize * 2 > bytes.size) return null

            var best: DexShareIconMatch? = null
            for (i in 0 until insnsSize) {
                val unit = ushortAt(insnsOff + i * 2)
                when (unit and 0xFF) {
                    0x71 -> {
                        val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                        if (!isWebPSetPureDrawable(methodIdx)) continue
                        val regs = invoke35cRegisters(insnsOff, i)
                        if (regs.size < 2) continue
                        val resId = findDrawableValueBefore(insnsOff, i, regs[1], cl) ?: continue
                        val match = DexShareIconMatch(
                            ownerClassName = ownerClassName,
                            ownerMethodName = ownerMethodName.orEmpty(),
                            resId = resId,
                            score = 240 + i.coerceAtMost(40),
                        )
                        if (best == null || match.score > best.score) best = match
                    }
                    0x77 -> {
                        val methodIdx = ushortAt(insnsOff + (i + 1) * 2)
                        if (!isWebPSetPureDrawable(methodIdx)) continue
                        val argCount = (unit ushr 8) and 0xFF
                        if (argCount < 2) continue
                        val firstReg = ushortAt(insnsOff + (i + 2) * 2)
                        val resId = findDrawableValueBefore(insnsOff, i, firstReg + 1, cl) ?: continue
                        val match = DexShareIconMatch(
                            ownerClassName = ownerClassName,
                            ownerMethodName = ownerMethodName.orEmpty(),
                            resId = resId,
                            score = 230 + i.coerceAtMost(40),
                        )
                        if (best == null || match.score > best.score) best = match
                    }
                }
            }
            return best
        }

        private fun findBooleanConstBefore(
            insnsOff: Int,
            invokeIndex: Int,
            targetReg: Int,
        ): Boolean? {
            return findIntConstBefore(insnsOff, invokeIndex, targetReg)?.let { it != 0 }
        }

        private fun findIntConstBefore(
            insnsOff: Int,
            invokeIndex: Int,
            targetReg: Int,
        ): Int? {
            val start = (invokeIndex - 32).coerceAtLeast(0)
            for (i in invokeIndex - 1 downTo start) {
                val offset = insnsOff + i * 2
                val unit = ushortAt(offset)
                val op = unit and 0xFF
                when (op) {
                    0x12 -> {
                        val dest = (unit ushr 8) and 0x0F
                        if (dest != targetReg) continue
                        val literal = signExtend4((unit ushr 12) and 0x0F)
                        return literal
                    }
                    0x13 -> {
                        val dest = (unit ushr 8) and 0xFF
                        if (dest != targetReg) continue
                        return signedShortAt(offset + 2)
                    }
                    0x14 -> {
                        val dest = (unit ushr 8) and 0xFF
                        if (dest != targetReg) continue
                        return intAt(offset + 2)
                    }
                    0x15 -> {
                        val dest = (unit ushr 8) and 0xFF
                        if (dest != targetReg) continue
                        return signedShortAt(offset + 2) shl 16
                    }
                }
            }
            return null
        }

        private fun signExtend4(value: Int): Int {
            return if ((value and 0x8) != 0) value or -0x10 else value
        }

        private fun invokeRegisters(
            insnsOff: Int,
            index: Int,
            firstUnit: Int,
            is35cInvoke: Boolean,
        ): List<Int> {
            if (is35cInvoke) return invoke35cRegisters(insnsOff, index)
            val argCount = (firstUnit ushr 8) and 0xFF
            if (argCount <= 0) return emptyList()
            val firstReg = ushortAt(insnsOff + (index + 2) * 2)
            return (0 until argCount.coerceAtMost(16)).map { firstReg + it }
        }

        private fun invoke35cRegisters(insnsOff: Int, index: Int): List<Int> {
            val first = ushortAt(insnsOff + index * 2)
            val argCount = (first ushr 12) and 0x0F
            val fifth = (first ushr 8) and 0x0F
            val third = ushortAt(insnsOff + (index + 2) * 2)
            val regs = intArrayOf(
                third and 0x0F,
                (third ushr 4) and 0x0F,
                (third ushr 8) and 0x0F,
                (third ushr 12) and 0x0F,
                fifth,
            )
            return regs.take(argCount.coerceIn(0, regs.size))
        }

        private fun findDrawableValueBefore(
            insnsOff: Int,
            invokeIndex: Int,
            targetReg: Int,
            cl: ClassLoader,
        ): Int? {
            val start = (invokeIndex - 96).coerceAtLeast(0)
            for (i in invokeIndex - 1 downTo start) {
                val offset = insnsOff + i * 2
                val unit = ushortAt(offset)
                val op = unit and 0xFF
                val dest = (unit ushr 8) and 0xFF
                if (dest != targetReg) continue
                when (op) {
                    0x14 -> {
                        val value = intAt(offset + 2)
                        if (isDrawableResourceId(value)) return value
                    }
                    0x15 -> {
                        val value = signedShortAt(offset + 2) shl 16
                        if (isDrawableResourceId(value)) return value
                    }
                    0x60 -> {
                        val fieldIdx = ushortAt(offset + 2)
                        val value = resolveDrawableFieldValue(fieldIdx, cl)
                        if (value != null && isDrawableResourceId(value)) return value
                    }
                }
            }
            return null
        }

        private fun isWebPSetPureDrawable(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == WEBP_SET_PURE_DRAWABLE &&
                methodClassDescriptor(methodIdx) == WEBP_MANAGER_DESCRIPTOR
        }

        private fun isAiWriteHelperFactory(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            val descriptor = methodClassDescriptor(methodIdx) ?: return false
            val name = methodName(methodIdx).orEmpty()
            return descriptor == AI_WRITE_HELPER_DESCRIPTOR &&
                (name == "a" || name.length <= 2)
        }

        private fun isFrameLayoutConstructor(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "<init>" &&
                methodClassDescriptor(methodIdx) == FRAME_LAYOUT_DESCRIPTOR
        }

        private fun isFrameLayoutField(fieldIdx: Int): Boolean {
            if (fieldIdx !in 0 until fieldIdsSize) return false
            return fieldTypeDescriptor(fieldIdx) == FRAME_LAYOUT_DESCRIPTOR
        }

        private fun isViewLikeField(fieldIdx: Int): Boolean {
            if (fieldIdx !in 0 until fieldIdsSize) return false
            return isViewLikeDescriptor(fieldTypeDescriptor(fieldIdx).orEmpty())
        }

        private fun isViewSetOnClickListener(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setOnClickListener") return false
            return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isViewGetId(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "getId" &&
                isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty()) &&
                methodReturnDescriptor(methodIdx) == "I" &&
                methodParameterDescriptors(methodIdx).isEmpty()
        }

        private fun isAgreeBackendInvoke(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            val descriptor = methodClassDescriptor(methodIdx).orEmpty()
            if (!descriptor.startsWith("Lcom/baidu/")) return false
            val parameters = methodParameterDescriptors(methodIdx)
            return parameters.any { it == AGREE_DATA_DESCRIPTOR }
        }

        private fun isDexInstancePut(op: Int): Boolean {
            return op in 0x59..0x5F
        }

        private fun isViewSetVisibility(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setVisibility") return false
            return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isViewGetLayoutParams(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "getLayoutParams") return false
            return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isViewSetLayoutParams(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setLayoutParams") return false
            return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isViewFindViewById(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "findViewById") return false
            return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isViewSetBackgroundResource(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setBackgroundResource") return false
            return isViewLikeDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isNavigationBarAddCustomView(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "addCustomView" &&
                methodClassDescriptor(methodIdx) == NAVIGATION_BAR_DESCRIPTOR
        }

        private fun isTextUtilsIsEmpty(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "isEmpty" &&
                methodClassDescriptor(methodIdx) == TEXT_UTILS_DESCRIPTOR
        }

        private fun isEmManagerFrom(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "from" &&
                methodClassDescriptor(methodIdx) == EM_MANAGER_DESCRIPTOR
        }

        private fun isEmManagerBackgroundMethod(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodClassDescriptor(methodIdx) != EM_MANAGER_DESCRIPTOR) return false
            val name = methodName(methodIdx).orEmpty()
            return name == "setBackGroundRealColor" ||
                name == "setBackGroundColor" ||
                name == "setCorner"
        }

        private fun bestDexFieldName(counts: LinkedHashMap<String, Int>): String? {
            var bestName: String? = null
            var bestCount = 0
            for ((name, count) in counts) {
                if (count > bestCount) {
                    bestName = name
                    bestCount = count
                }
            }
            return bestName
        }

        private fun isViewMarginSetMargins(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setMargins") return false
            val descriptor = methodClassDescriptor(methodIdx).orEmpty()
            return descriptor == VIEW_MARGIN_LAYOUT_PARAMS_DESCRIPTOR ||
                descriptor.endsWith("/FrameLayout\$LayoutParams;") ||
                descriptor.endsWith("/ViewGroup\$MarginLayoutParams;")
        }

        private fun isSpriteMemePanConstructor(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "<init>" &&
                methodClassDescriptor(methodIdx) == AI_SPRITE_MEME_PAN_DESCRIPTOR
        }

        private fun isSpriteMemePanMethod(methodIdx: Int, name: String): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == name &&
                methodClassDescriptor(methodIdx) == AI_SPRITE_MEME_PAN_DESCRIPTOR
        }

        private fun isImageJumpButtonLayoutConstructor(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "<init>" &&
                methodClassDescriptor(methodIdx) == IMAGE_JUMP_BUTTON_LAYOUT_DESCRIPTOR
        }

        private fun isLinearLayoutLayoutParamsConstructor(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "<init>" &&
                methodClassDescriptor(methodIdx) == LINEAR_LAYOUT_LAYOUT_PARAMS_DESCRIPTOR
        }

        private fun isViewGroupAddView(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "addView") return false
            val descriptor = methodClassDescriptor(methodIdx).orEmpty()
            return descriptor == VIEW_GROUP_DESCRIPTOR ||
                descriptor == LINEAR_LAYOUT_DESCRIPTOR ||
                descriptor.endsWith("ViewGroup;") ||
                descriptor.endsWith("Layout;")
        }

        private fun isViewLikeDescriptor(descriptor: String): Boolean {
            if (descriptor.isBlank()) return false
            return descriptor == ANDROID_VIEW_DESCRIPTOR ||
                descriptor == FRAME_LAYOUT_DESCRIPTOR ||
                descriptor.endsWith("View;") ||
                descriptor.endsWith("Layout;") ||
                descriptor.endsWith("Button;") ||
                descriptor.endsWith("ProgressBar;") ||
                descriptor.endsWith("Progressbar;")
        }

        private fun isAppClassDescriptor(descriptor: String): Boolean {
            return descriptor.startsWith("Lcom/baidu/") ||
                descriptor.startsWith("Landroidx/") ||
                descriptor.startsWith("Lkotlin/")
        }

        private fun isAiWriteReplyString(value: String): Boolean {
            return value == "AI_WRITE_REPLY" ||
                value.equals("ai_write_reply", ignoreCase = true) ||
                value.equals("aiWriteReply", ignoreCase = true)
        }

        private fun isAiCapacityDescriptor(descriptor: String): Boolean {
            return descriptor.contains("AICapacityApplyHelper") ||
                descriptor.contains("capacityApplyType") ||
                descriptor.contains("AIWrite", ignoreCase = true)
        }

        private fun isListSetSelection(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setSelection") return false
            val descriptor = methodClassDescriptor(methodIdx).orEmpty()
            return descriptor.contains("List") ||
                descriptor.contains("RecyclerView") ||
                descriptor.contains("AdapterView") ||
                descriptor.contains("TabHost") ||
                descriptor.contains("Gallery")
        }

        private fun isSwipeRefreshInvoke(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return isSwipeRefreshDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isSwipeRefreshSetRefreshing(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            if (methodName(methodIdx) != "setRefreshing") return false
            return isSwipeRefreshDescriptor(methodClassDescriptor(methodIdx).orEmpty())
        }

        private fun isSwipeRefreshDescriptor(descriptor: String): Boolean {
            return descriptor == BIGDAY_SWIPE_REFRESH_DESCRIPTOR ||
                descriptor.endsWith("/BigdaySwipeRefreshLayout;") ||
                descriptor.contains("SwipeRefreshLayout")
        }

        private fun isScrollTabNotify(methodIdx: Int): Boolean {
            if (methodIdx !in 0 until methodIdsSize) return false
            return methodName(methodIdx) == "b" &&
                methodClassDescriptor(methodIdx) == SCROLL_FRAGMENT_TAB_CALLBACK_DESCRIPTOR
        }

        private fun resolveDrawableFieldValue(fieldIdx: Int, cl: ClassLoader): Int? {
            if (fieldIdx !in 0 until fieldIdsSize) return null
            if (fieldClassDescriptor(fieldIdx) != R_DRAWABLE_DESCRIPTOR) return null
            val name = fieldName(fieldIdx) ?: return null
            val drawableClass = XposedCompat.findClassOrNull("com.baidu.tieba.R\$drawable", cl) ?: return null
            return runCatching {
                val field = drawableClass.getDeclaredField(name)
                field.isAccessible = true
                field.getInt(null)
            }.getOrNull()
        }

        private fun isDrawableResourceId(value: Int): Boolean {
            if ((value ushr 24) != 0x7F) return false
            return ((value ushr 16) and 0xFF) == 0x08
        }

        private fun isStaticAccess(accessFlags: Int): Boolean {
            return (accessFlags and 0x0008) != 0
        }

        private fun methodClassDescriptor(methodIdx: Int): String? {
            val off = methodIdsOff + methodIdx * 8
            return typeDescriptor(ushortAt(off))
        }

        private fun methodReturnDescriptor(methodIdx: Int): String? {
            if (methodIdx !in 0 until methodIdsSize) return null
            val methodOff = methodIdsOff + methodIdx * 8
            val protoIdx = ushortAt(methodOff + 2)
            if (protoIdx !in 0 until protoIdsSize) return null
            val protoOff = protoIdsOff + protoIdx * 12
            return typeDescriptor(intAt(protoOff + 4))
        }

        private fun methodParameterDescriptors(methodIdx: Int): List<String> {
            if (methodIdx !in 0 until methodIdsSize) return emptyList()
            val methodOff = methodIdsOff + methodIdx * 8
            val protoIdx = ushortAt(methodOff + 2)
            if (protoIdx !in 0 until protoIdsSize) return emptyList()
            val protoOff = protoIdsOff + protoIdx * 12
            val parametersOff = intAt(protoOff + 8)
            if (parametersOff == 0) return emptyList()
            val size = intAt(parametersOff)
            if (size < 0 || parametersOff + 4 + size * 2 > bytes.size) return emptyList()
            val out = ArrayList<String>(size)
            for (i in 0 until size) {
                typeDescriptor(ushortAt(parametersOff + 4 + i * 2))?.let(out::add)
            }
            return out
        }

        private fun methodName(methodIdx: Int): String? {
            val off = methodIdsOff + methodIdx * 8
            return stringAt(intAt(off + 4))
        }

        private fun fieldClassDescriptor(fieldIdx: Int): String? {
            val off = fieldIdsOff + fieldIdx * 8
            return typeDescriptor(ushortAt(off))
        }

        private fun fieldTypeDescriptor(fieldIdx: Int): String? {
            val off = fieldIdsOff + fieldIdx * 8
            return typeDescriptor(ushortAt(off + 2))
        }

        private fun fieldName(fieldIdx: Int): String? {
            val off = fieldIdsOff + fieldIdx * 8
            return stringAt(intAt(off + 4))
        }

        private fun typeDescriptor(typeIdx: Int): String? {
            if (typeIdx !in 0 until typeIdsSize) return null
            typeCache[typeIdx]?.let { return it }
            val stringIdx = intAt(typeIdsOff + typeIdx * 4)
            val value = stringAt(stringIdx)
            typeCache[typeIdx] = value
            return value
        }

        private fun extendsDescriptor(
            descriptor: String,
            expectedSuperDescriptor: String,
        ): Boolean {
            var current: String? = descriptor
            repeat(12) {
                if (current == null) return false
                if (current == expectedSuperDescriptor) return true
                current = classSuperDescriptor(current!!)
            }
            return false
        }

        private fun classSuperDescriptor(descriptor: String): String? {
            if (classSuperCache.containsKey(descriptor)) return classSuperCache[descriptor]
            for (i in 0 until classDefsSize) {
                val classDefOff = classDefsOff + i * 32
                if (typeDescriptor(intAt(classDefOff)) != descriptor) continue
                val superDescriptor = typeDescriptor(intAt(classDefOff + 8))
                classSuperCache[descriptor] = superDescriptor
                return superDescriptor
            }
            classSuperCache[descriptor] = null
            return null
        }

        private fun descriptorToClassName(descriptor: String): String? {
            if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return null
            return descriptor.substring(1, descriptor.length - 1).replace('/', '.')
        }

        private fun stringAt(stringIdx: Int): String? {
            if (stringIdx !in 0 until stringIdsSize) return null
            if (stringCache.containsKey(stringIdx)) return stringCache[stringIdx]
            val dataOff = intAt(stringIdsOff + stringIdx * 4)
            var cursor = readUleb128(dataOff).next
            val chars = ArrayList<Byte>()
            while (cursor < bytes.size && bytes[cursor].toInt() != 0) {
                chars.add(bytes[cursor])
                cursor++
            }
            val value = runCatching {
                chars.toByteArray().toString(Charsets.UTF_8)
            }.getOrNull()
            stringCache[stringIdx] = value
            return value
        }

        private fun readUleb128(offset: Int): Uleb {
            var cursor = offset
            var result = 0
            var shift = 0
            while (cursor < bytes.size) {
                val b = bytes[cursor].toInt() and 0xFF
                cursor++
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            return Uleb(result, cursor)
        }

        private fun ushortAt(offset: Int): Int {
            if (offset < 0 || offset + 1 >= bytes.size) return 0
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        }

        private fun signedShortAt(offset: Int): Int {
            val value = ushortAt(offset)
            return if ((value and 0x8000) != 0) value or -0x10000 else value
        }

        private fun intAt(offset: Int): Int {
            if (offset < 0 || offset + 3 >= bytes.size) return 0
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }

        private data class Uleb(
            val value: Int,
            val next: Int,
        )

        private data class EncodedMethod(
            val methodIdx: Int,
            val accessFlags: Int,
            val codeOff: Int,
            val next: Int,
        )
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }

    private fun formatScanException(t: Throwable): String {
        return HookSymbolScanDiagnostics.formatScanException(t)
    }

    private fun sanitizeScanStatusText(raw: String): String {
        return HookSymbolScanDiagnostics.sanitizeScanStatusText(raw)
    }

    private fun recordScanIssue(
        logger: ScanLogger?,
        tag: String,
        errors: MutableList<String>,
        detail: String,
    ) {
        HookSymbolScanDiagnostics.recordScanIssue(logger, tag, errors, detail)
    }
}

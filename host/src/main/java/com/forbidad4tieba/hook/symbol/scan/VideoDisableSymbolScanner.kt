package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.model.VideoDisableScan
import com.forbidad4tieba.hook.symbol.model.VideoDisableCache
import com.forbidad4tieba.hook.symbol.model.VideoDisableFamily
import com.forbidad4tieba.hook.symbol.model.VideoDisableSymbols
import com.forbidad4tieba.hook.symbol.model.VideoDisableTarget
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object VideoDisableSymbolScanner {
    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): VideoDisableScan {
        val paths = listOfNotNull(context.applicationInfo?.sourceDir) + context.applicationInfo?.splitSourceDirs.orEmpty()
        return HookSymbolScanSession.withDexKitBridge(paths, logger) { source ->
            val hierarchies = HashMap<String, List<String>>()
            val families = VideoDisableTarget.entries.mapNotNull { target ->
                scanSubStep("VideoDisable.${target.name}", logger, null as Pair<String, VideoDisableFamily>?) {
                    val candidates = source.bridge.getClassData(target.className)?.methods.orEmpty().filter {
                        it.methodName == target.methodName &&
                            target.matchesSignature(it.modifiers, it.returnTypeName, it.paramTypeNames)
                    }
                    if (candidates.size != 1) {
                        HookSymbolScanDiagnostics.log(logger,
                            "VideoDisable.${target.name}: unavailable candidates=${candidates.size}")
                        null
                    } else {
                        val root = restoreMethod(cl, target)
                        val hierarchy = if (isOverridable(root)) hierarchies.getOrPut(target.className) {
                            collectHierarchy(source.bridge, target.className)
                        } else listOf(target.className)
                        val declarations = hierarchy.mapNotNull { owner ->
                            val declared = source.bridge.getClassData(owner)?.methods.orEmpty().filter {
                                it.methodName == target.methodName && it.paramTypeNames == target.parameterTypeNames
                            }
                            check(declared.size <= 1) { "ambiguous override: $owner.${target.methodName}" }
                            declared.singleOrNull()?.let {
                                check(target.matchesSignature(it.modifiers, it.returnTypeName, it.paramTypeNames)) {
                                    "invalid override: $owner.${target.methodName}"
                                }
                                it
                            }
                        }
                        val descriptors = declarations.map { "${it.declaredClassName}#${it.methodName}" }.sorted()
                        val family = VideoDisableFamily(hierarchy.sorted(), descriptors)
                        restoreFamily(cl, target, family)
                        val dispatchDeclarations = collectVirtualDispatchDeclarations(source.bridge, target, hierarchy)
                        val consumerDeclarations = (declarations + dispatchDeclarations).distinctBy { it.descriptor }
                        val callerCount = consumerDeclarations.sumOf { it.callers.size }
                        val externalCount = consumerDeclarations.sumOf { method -> method.callers.count { it.declaredClassName != method.declaredClassName } }
                        val hasConsumer = target.hasConsumer(callerCount, externalCount)
                        HookSymbolScanDiagnostics.log(logger,
                            "VideoDisable.${target.name}: family=${descriptors.size} hierarchy=${hierarchy.size} " +
                                "rootCallers=${candidates.single().callers.size} callers=$callerCount external=$externalCount usable=$hasConsumer " +
                                "dispatchOwners=${dispatchDeclarations.filter { it.callers.isNotEmpty() }.map { it.declaredClassName }.distinct().joinToString()}")
                        if (hasConsumer) target.name to family else null
                    }
                }
            }.toMap()
            val pcdn = scanSubStep("VideoDisable.PCDN_START", logger, null as String?) {
                scanPcdn(source.bridge, cl, logger)
            }
            VideoDisableScan(VideoDisableCache(families), pcdn)
        } ?: VideoDisableScan(VideoDisableCache(), null)
    }

    private fun collectHierarchy(bridge: DexKitBridge, root: String): List<String> {
        val pending = ArrayDeque<String>()
        val found = linkedSetOf(root)
        pending.add(root)
        while (pending.isNotEmpty()) {
            val parent = pending.removeFirst()
            val children = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().superClass(parent)))
                .filter { it.superClass?.name == parent }
            for (child in children) if (found.add(child.name)) pending.add(child.name)
        }
        return found.sorted()
    }

    internal fun isOverridable(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) && !Modifier.isPrivate(method.modifiers) && !Modifier.isFinal(method.modifiers)

    /** Calls made against an actual supertype/interface dispatch to the same virtual slot. */
    private fun collectVirtualDispatchDeclarations(
        bridge: DexKitBridge,
        target: VideoDisableTarget,
        hierarchy: List<String>,
    ): List<MethodData> {
        if (target.isStatic || target.isPrivate) return emptyList()
        val pending = ArrayDeque<org.luckypray.dexkit.result.ClassData>()
        hierarchy.mapNotNull { bridge.getClassData(it) }.forEach { pending.add(it) }
        val seen = HashSet<String>()
        val found = ArrayList<MethodData>()
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            if (!seen.add(type.name)) continue
            found += type.methods.filter {
                it.methodName == target.methodName &&
                    isVirtualDispatchSignature(target, it.modifiers, it.returnTypeName, it.paramTypeNames)
            }
            type.superClass?.let(pending::add)
            type.interfaces.forEach { pending.add(it) }
        }
        return found.distinctBy { it.descriptor }
    }

    internal fun isVirtualDispatchSignature(
        target: VideoDisableTarget,
        modifiers: Int,
        returnType: String,
        parameters: List<String>,
    ): Boolean = !target.isStatic && !target.isPrivate && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers) &&
        (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) && returnType == target.returnTypeName &&
        parameters == target.parameterTypeNames

    /** Validates the cached hierarchy and compares every real declaration with its cached owner. */
    internal fun restoreFamily(cl: ClassLoader, target: VideoDisableTarget, family: VideoDisableFamily): List<Method> {
        check(family.hasValidMetadata(target)) { "invalid video family metadata: ${target.name}" }
        val root = restoreMethod(cl, target)
        val rootClass = root.declaringClass
        if (!isOverridable(root)) check(family.hierarchy == listOf(target.className)) { "non-virtual target has descendants" }
        val expected = family.hierarchy.mapNotNull { owner ->
            val cls = Class.forName(owner, false, cl)
            check(rootClass.isAssignableFrom(cls)) { "unrelated override owner: $owner" }
            if (cls != rootClass) check(cls.superclass?.name in family.hierarchy) { "incomplete cached hierarchy: $owner" }
            val declared = cls.declaredMethods.filter {
                it.name == target.methodName && it.parameterTypes.map { type -> type.name } == target.parameterTypeNames
            }
            check(declared.size <= 1) { "ambiguous restored override: $owner.${target.methodName}" }
            declared.singleOrNull()?.apply {
                check(target.matchesSignature(modifiers, returnType.name, parameterTypes.map { it.name })) {
                    "invalid restored override: $owner.${target.methodName}"
                }
                isAccessible = true
            }
        }.sortedBy { it.declaringClass.name }
        check(expected.map { "${it.declaringClass.name}#${it.name}" } == family.methods.sorted()) {
            "cached override coverage differs from hierarchy: ${target.name}"
        }
        return expected
    }

    private fun scanPcdn(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): String? {
        val parsers = bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba").matcher(
            MethodMatcher.create().returnType("void").paramTypes("org.json.JSONObject")
                .addEqString("p2p_config").addEqString("domain_list"),
        )).filter { !Modifier.isStatic(it.modifiers) }
        val ownerNames = parsers.map { it.declaredClassName }.distinct()
        val owner = uniquePcdnOwner(ownerNames) ?: run {
            HookSymbolScanDiagnostics.log(logger, "VideoDisable.PCDN_START: unavailable configOwners=${ownerNames.size}")
            return null
        }
        val candidates = bridge.getClassData(owner)?.methods.orEmpty().filter { method ->
            isPcdnStartSignature(method.modifiers, method.returnTypeName, method.paramTypeNames) &&
                "pcdn" in method.usingStrings && method.invokes.any {
                    it.declaredClassName == "android.content.Context" && it.methodName == "startService" &&
                        it.paramTypeNames == listOf("android.content.Intent") && it.returnTypeName == "android.content.ComponentName"
                } && method.callers.isNotEmpty()
        }
        val method = uniquePcdnStart(candidates.map { "${it.declaredClassName}#${it.methodName}" }) ?: run {
            HookSymbolScanDiagnostics.log(logger, "VideoDisable.PCDN_START: unavailable startCandidates=${candidates.size}")
            return null
        }
        restorePcdn(cl, method)
        return method
    }

    internal fun uniquePcdnOwner(names: List<String>): String? = names.distinct().singleOrNull()
    internal fun uniquePcdnStart(descriptors: List<String>): String? = descriptors.singleOrNull()

    internal fun isPcdnStartSignature(modifiers: Int, returnType: String, parameters: List<String>): Boolean =
        Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers) && returnType == "void" && parameters == listOf("boolean")

    internal fun restoreMethod(cl: ClassLoader, target: VideoDisableTarget): Method {
        val owner = Class.forName(target.className, false, cl)
        return owner.declaredMethods.singleOrNull(target::matches)?.apply { isAccessible = true }
            ?: error("invalid required video signature: ${target.name}")
    }

    internal fun restorePcdn(cl: ClassLoader, descriptor: String): Method {
        val parts = descriptor.split('#')
        require(parts.size == 2 && parts[0].startsWith("com.baidu.tieba.") && parts[1].isNotBlank()) { "invalid PCDN descriptor" }
        val owner = Class.forName(parts[0], false, cl)
        return owner.declaredMethods.singleOrNull {
            it.name == parts[1] && isPcdnStartSignature(it.modifiers, it.returnType.name, it.parameterTypes.map { type -> type.name })
        }?.apply { isAccessible = true } ?: error("invalid PCDN start signature")
    }

    fun restore(cl: ClassLoader, cache: VideoDisableCache, pcdn: String?): VideoDisableSymbols? {
        if (!cache.isComplete() || pcdn.isNullOrBlank()) return null
        return scanSubStep("VideoDisable.Restore", null, null) {
            VideoDisableSymbols(VideoDisableTarget.entries.associateWith { restoreFamily(cl, it, cache.families.getValue(it.name)) },
                restorePcdn(cl, pcdn))
        }
    }

    fun isCacheValid(cl: ClassLoader, cache: VideoDisableCache, pcdn: String?): Boolean =
        scanSubStep("VideoDisable.Cache", null, false) {
            if (!cache.hasValidMetadata()) return@scanSubStep false
            cache.families.forEach { (name, family) -> restoreFamily(cl, VideoDisableTarget.valueOf(name), family) }
            if (pcdn != null) restorePcdn(cl, pcdn)
            true
        }
}

package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.os.Bundle
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.GlobalDirectProfileSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

object ProfileContract : SymbolContract("Profile") {
    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = Unit
    fun resolveGlobalDirectProfileSymbols(cl: ClassLoader): GlobalDirectProfileSymbols? {
        return try {
            fun find(name: String): Class<*> = ScanReflection.safeFindClass(name, cl) ?: error("class not found: $name")
            val messageManagerClass = find(StableTiebaHookPoints.MESSAGE_MANAGER_CLASS)
            val messageClass = find(StableTiebaHookPoints.MESSAGE_CLASS)
            val customMessageClass = find(StableTiebaHookPoints.CUSTOM_MESSAGE_CLASS)
            if (!messageClass.isAssignableFrom(customMessageClass)) error("CustomMessage is not Message")
            val clickableHeaderClass = find(StableTiebaHookPoints.CLICKABLE_HEADER_IMAGE_VIEW_CLASS)
            val threadDataClass = find(StableTiebaHookPoints.THREAD_DATA_CLASS)
            val clickableHeaderSetDataMethod = clickableHeaderClass.getDeclaredMethod(
                StableTiebaHookPoints.METHOD_SET_DATA,
                threadDataClass,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
            }?.apply { isAccessible = true }
                ?: error("ClickableHeaderImageView.setData(ThreadData,boolean,boolean) mismatch")
            val clickableHeaderGetUserIdMethod = clickableHeaderClass.methods.singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == StableTiebaHookPoints.METHOD_GET_USER_ID &&
                    method.parameterTypes.isEmpty() && method.returnType == String::class.java
            }?.apply { isAccessible = true }
                ?: error("ClickableHeaderImageView.getUserId not found")
            val messageManagerGetInstanceMethod = messageManagerClass.methods.singleOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == StableTiebaHookPoints.METHOD_GET_INSTANCE &&
                    method.parameterTypes.isEmpty() && messageManagerClass.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true } ?: error("MessageManager.getInstance not found")
            val sendMethod = messageManagerClass.methods.singleOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == StableTiebaHookPoints.METHOD_SEND_MESSAGE &&
                    method.parameterTypes.contentEquals(arrayOf(messageClass))
            }?.apply { isAccessible = true } ?: error("MessageManager.sendMessage(Message) not unique")
            val customMessageConstructor = customMessageClass
                .getDeclaredConstructor(Int::class.javaPrimitiveType, Any::class.java)
                .apply { isAccessible = true }
            val personInfoClass = find(StableTiebaHookPoints.PERSON_INFO_ACTIVITY_CONFIG_CLASS)
            val personInfoGetContextMethod = personInfoClass.methods.singleOrNull { method ->
                method.name == "getContext" && method.parameterTypes.isEmpty() &&
                    Context::class.java.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true } ?: error("PersonInfoActivityConfig.getContext not found")
            val personInfoGetIntentMethod = personInfoClass.methods.singleOrNull { method ->
                method.name == "getIntent" && method.parameterTypes.isEmpty() &&
                    android.content.Intent::class.java.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true } ?: error("PersonInfoActivityConfig.getIntent not found")
            val personPolymericClass = find(StableTiebaHookPoints.PERSON_POLYMERIC_ACTIVITY_CONFIG_CLASS)
            val polymericConstructor = personPolymericClass.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }
            val createNormalMethod = personPolymericClass.methods.singleOrNull { method ->
                method.name == "createNormalConfig" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
                    ) && personPolymericClass.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true } ?: error("PersonPolymericActivityConfig.createNormalConfig not found")
            val personPolymericGetIntentMethod = personPolymericClass.methods.singleOrNull { method ->
                method.name == "getIntent" && method.parameterTypes.isEmpty() &&
                    android.content.Intent::class.java.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true } ?: error("PersonPolymericActivityConfig.getIntent not found")
            val personPolymericSetUriMethod = personPolymericClass.methods.singleOrNull { method ->
                method.name == "setUri" && method.parameterTypes.contentEquals(arrayOf(android.net.Uri::class.java)) &&
                    method.returnType == Void.TYPE
            }?.apply { isAccessible = true } ?: error("PersonPolymericActivityConfig.setUri not found")
            val applicationClass = find(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS)
            val currentAccountMethod = applicationClass.methods.singleOrNull {
                    Modifier.isStatic(it.modifiers) &&
                        it.name == StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT &&
                        it.parameterTypes.isEmpty() && it.returnType == String::class.java
                }?.apply { isAccessible = true }
            val applicationGetInstMethod = applicationClass.methods.singleOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == StableTiebaHookPoints.METHOD_GET_INST &&
                    method.parameterTypes.isEmpty() && applicationClass.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true } ?: error("TbadkCoreApplication.getInst not found")
            val urlManagerClass = find(StableTiebaHookPoints.URL_MANAGER_CLASS)
            val urlManagerDealOneLinkMethods = urlManagerClass.methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == StableTiebaHookPoints.METHOD_DEAL_ONE_LINK_WITH_DIALOG &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 7 &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == Array<String>::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[5] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[6] == android.os.Bundle::class.java
            }.singleOrNull()?.apply { isAccessible = true }
                ?.let(::listOf)
                ?: error("UrlManager.dealOneLinkWithDialog target not unique")
            GlobalDirectProfileSymbols(
                clickableHeaderSetDataMethod = clickableHeaderSetDataMethod,
                clickableHeaderGetUserIdMethod = clickableHeaderGetUserIdMethod,
                customMessageConstructor = customMessageConstructor,
                personInfoConfigClass = personInfoClass,
                personInfoGetContextMethod = personInfoGetContextMethod,
                personInfoGetIntentMethod = personInfoGetIntentMethod,
                personPolymericConfigConstructor = polymericConstructor,
                personPolymericCreateNormalConfigMethod = createNormalMethod,
                personPolymericGetIntentMethod = personPolymericGetIntentMethod,
                personPolymericSetUriMethod = personPolymericSetUriMethod,
                currentAccountMethod = currentAccountMethod,
                applicationGetInstMethod = applicationGetInstMethod,
                messageManagerGetInstanceMethod = messageManagerGetInstanceMethod,
                messageManagerSendMethod = sendMethod,
                urlManagerDealOneLinkMethods = urlManagerDealOneLinkMethods,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[CommentAvatarDirectProfileHook] global symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val plainUrlDirectMissing = ArrayList<String>(6)
        if (symbols[PlainUrlContract.plainUrlClickableSpanClass].isNullOrBlank()) plainUrlDirectMissing.add("plainUrlClickableSpanClass")
        if (symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickMethod")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses].isNullOrEmpty()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickOwnerClasses")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanTypeField].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTypeField")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanUrlField].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanUrlField")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanTextField].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTextField")
        }
        val plainUrlMessageMissing = ArrayList<String>(8)
        if (symbols[PlainUrlContract.plainUrlMessageManagerClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageManagerClass")
        }
        if (symbols[PlainUrlContract.plainUrlMessageDispatchMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageDispatchMethod")
        }
        if (symbols[PlainUrlContract.plainUrlResponsedMessageClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageClass")
        }
        if (symbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageGetCmdMethod")
        }
        if (symbols[PlainUrlContract.plainUrlCustomResponsedMessageClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageClass")
        }
        if (symbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageGetDataMethod")
        }
        if (symbols[PlainUrlContract.plainUrlApplicationClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationClass")
        }
        if (symbols[PlainUrlContract.plainUrlApplicationGetInstMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationGetInstMethod")
        }
        val plainUrlDirectReady = plainUrlDirectMissing.isEmpty()
        val plainUrlMessageReady = plainUrlMessageMissing.isEmpty()
        val directProfileNavigationMissing = buildList {
            if (!plainUrlDirectReady) addAll(plainUrlDirectMissing)
            if (!plainUrlMessageReady) addAll(plainUrlMessageMissing)
        }.distinct()
        out[HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE] =
            if (directProfileNavigationMissing.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(
                    state = HookFeatureState.PARTIAL,
                    missingOptional = directProfileNavigationMissing,
                )
            }
        return out
    }

    internal override val pointOwners = listOf(
        PointOwner("CommentAvatarDirectProfileHook", false, listOf(HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true

    // Diagnostics are supplied by the consuming capability or the fixed entry installer.
    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = emptyList()
}

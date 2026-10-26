package com.forbidad4tieba.hook.symbol.contract

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.*
import com.forbidad4tieba.hook.symbol.scan.CollectionSearchSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Collection's cached roles and stable host APIs are restored before any page callback. */
internal object CollectionTargetRestoration {
    fun restore(cl: ClassLoader, symbols: HookSymbols): CollectionSearchSymbols = with(CollectionContract) {
        val activity = requireNotNull(ScanReflection.safeFindClass(StableTiebaHookPoints.COLLECT_TAB_ACTIVITY_CLASS, cl))
        val fragment = requireNotNull(ScanReflection.safeFindClass(StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS, cl))
        fun requiredField(owner: Class<*>, symbol: SymbolField<String?>): Field =
            requireNotNull(field(owner, symbols[symbol])) { "invalid ${symbol.cacheKey}" }
        fun requiredMethod(owner: Class<*>, name: SymbolField<String?>, spec: SymbolField<String?>): Method {
            val result = RestoredMembers.resolveMethodByCachedSpec(owner,
                requireNotNull(symbols[spec]) { "missing ${spec.cacheKey}" },
                requireNotNull(symbols[name]) { "missing ${name.cacheKey}" })
            return requireNotNull(result) { "invalid ${name.cacheKey}" }.also {
                check(!Modifier.isStatic(it.modifiers)) { "static ${name.cacheKey}" }
                it.isAccessible = true
            }
        }
        val presenter = requiredField(fragment, collectionPresenterField)
        val setter = requiredMethod(presenter.type, collectionPresenterListSetterMethod, collectionPresenterListSetterMethodSpec)
        check(setter.returnType == Void.TYPE && setter.parameterCount == 1 && List::class.java.isAssignableFrom(setter.parameterTypes[0]) &&
            setter.parameterTypes[0].isAssignableFrom(ArrayList::class.java)) { "invalid collection setter shape" }
        val model = requiredField(fragment, collectionModelField)
        val getter = requiredMethod(model.type, collectionModelListGetterMethod, collectionModelListGetterMethodSpec)
        check(getter.parameterCount == 0 && List::class.java.isAssignableFrom(getter.returnType)) { "invalid collection list getter" }
        val parse = requiredMethod(model.type, collectionModelParseMethod, collectionModelParseMethodSpec)
        check(parse.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
            List::class.java.isAssignableFrom(parse.returnType)) { "invalid collection parser" }
        val modelList = requiredField(model.type, collectionModelListField)
        val displayList = requiredField(fragment, collectionFragmentDisplayListField)
        check(listSlot(modelList) && listSlot(displayList)) { "invalid collection list fields" }
        val controller = symbols[collectionActivityNavControllerField]?.takeIf { it.isNotBlank() }?.let {
            requireNotNull(field(activity, it)) { "invalid collection navigation owner" }
        }
        val navigation = requiredField(controller?.type ?: activity, collectionNavBarField)
        val adapterField = field(presenter.type, symbols[collectionPresenterAdapterField])
        val mark = ScanReflection.safeFindClass(CollectionSearchSymbolScanner.MARK_CLASS, cl)
        val account = ScanReflection.safeFindClass(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS, cl)
        CollectionSearchSymbols(
            activityClass = activity, fragmentClass = fragment,
            presenterField = presenter, presenterListSetterMethod = setter,
            modelField = model, modelListGetterMethod = getter, modelParseMethod = parse,
            modelListField = modelList, fragmentDisplayListField = displayList,
            activityNavControllerField = controller, navBarField = navigation, presenterAdapterField = adapterField,
            editModeMethod = method(activity, symbols[collectionEditModeMethod], Boolean::class.javaPrimitiveType!!),
            currentAccountMethod = method(account, StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT, String::class.java, true),
            mark = CollectionMarkTargets(
                method(mark, "getTitle", String::class.java), method(mark, "getAuthorName", String::class.java),
                method(mark, "getForumName", String::class.java), method(mark, "getThreadId", String::class.java),
                method(mark, "getPostId", String::class.java), method(mark, "getId", String::class.java),
            ),
            adapter = adapterField?.type?.let { type ->
                CollectionAdapterTargets(
                    method(type, symbols[collectionAdapterShowFooterMethod], Void.TYPE, false, Boolean::class.javaPrimitiveType!!),
                    method(type, symbols[collectionAdapterLoadingMethod], Void.TYPE, false, Boolean::class.javaPrimitiveType!!),
                    method(type, symbols[collectionAdapterHasMoreMethod], Void.TYPE, false, Boolean::class.javaPrimitiveType!!),
                    method(type, "getCount", Int::class.javaPrimitiveType!!),
                    method(type, "isEnabled", Boolean::class.javaPrimitiveType!!, false, Int::class.javaPrimitiveType!!),
                    method(type, "getView", View::class.java, false, Int::class.javaPrimitiveType!!, View::class.java, ViewGroup::class.java),
                    method(type, "notifyDataSetChanged", Void.TYPE),
                )
            },
            network = network(cl), page = page(activity, fragment),
        )
    }

    private fun field(owner: Class<*>, name: String?): Field? {
        if (name.isNullOrBlank()) return null
        return ScanReflection.collectInstanceFields(owner).singleOrNull { it.name == name }?.apply { isAccessible = true }
    }

    private fun listSlot(field: Field): Boolean = List::class.java.isAssignableFrom(field.type) &&
        field.type.isAssignableFrom(ArrayList::class.java)

    private fun method(owner: Class<*>?, name: String?, result: Class<*>, static: Boolean = false, vararg params: Class<*>): Method? {
        if (owner == null || name.isNullOrBlank()) return null
        return ReflectionUtils.findMethodInHierarchy(owner, name, *params)
            ?.takeIf { result.isAssignableFrom(it.returnType) && Modifier.isStatic(it.modifiers) == static }
            ?.apply { isAccessible = true }
    }

    private fun network(cl: ClassLoader): CollectionNetworkTargets? = runCatching {
        val net = requireNotNull(ScanReflection.safeFindClass(StableTiebaHookPoints.NETWORK_CLASS, cl))
        val config = requireNotNull(ScanReflection.safeFindClass(StableTiebaHookPoints.TB_CONFIG_CLASS, cl))
        fun configField(name: String) = config.getDeclaredField(name).also {
            check(Modifier.isStatic(it.modifiers) && it.type == String::class.java)
            it.isAccessible = true
        }
        CollectionNetworkTargets(
            net.getDeclaredConstructor(String::class.java).apply { isAccessible = true },
            requireNotNull(method(net, StableTiebaHookPoints.METHOD_ADD_POST_DATA, Void.TYPE, false, String::class.java, String::class.java)),
            requireNotNull(method(net, StableTiebaHookPoints.METHOD_POST_NET_DATA, String::class.java)),
            configField(StableTiebaHookPoints.FIELD_SERVER_ADDRESS), configField(StableTiebaHookPoints.FIELD_MARK_GET_STORE),
        )
    }.getOrNull()

    private fun page(activity: Class<*>, fragment: Class<*>): CollectionPageTargets {
        val support = method(activity, "getSupportFragmentManager", Any::class.java)
        val child = method(fragment, "getChildFragmentManager", Any::class.java)
        return CollectionPageTargets(support, child,
            method(support?.returnType ?: child?.returnType, "getFragments", List::class.java),
            method(fragment, "isAdded", Boolean::class.javaPrimitiveType!!), method(fragment, "getActivity", Activity::class.java))
    }
}

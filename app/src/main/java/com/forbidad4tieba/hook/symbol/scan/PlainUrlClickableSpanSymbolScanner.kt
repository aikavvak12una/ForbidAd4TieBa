package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.text.style.ClickableSpan
import android.view.View
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PlainUrlClickableSpanScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PlainUrlClickableSpanSymbolScanner {
    private const val TAG = "PlainUrlClickableSpanHook"
    private const val MESSAGE_MANAGER = "com.baidu.adp.framework.MessageManager"
    private const val RICH_TEXT_ITEM = "com.baidu.tbadk.widget.richText.TbRichTextItem"
    private const val TEXT_MESSAGE_TEMPLATE = "com.baidu.tieba.impersonal.template.PersonalTextMsgTemplate"

    fun scan(context: Context, candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): PlainUrlClickableSpanScanSymbols =
        scanSubStep(TAG, logger, PlainUrlClickableSpanScanSymbols()) {
            val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
                context.applicationInfo?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) { source ->
                scan(source.bridge, candidates, cl, logger)
            } ?: PlainUrlClickableSpanScanSymbols()
        }

    private fun scan(bridge: DexKitBridge, candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): PlainUrlClickableSpanScanSymbols {
        val empty = PlainUrlClickableSpanScanSymbols()
        val clicks = bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().name("onClick").addEqString("rich_text_click").addEqString("obj_url")),
        ).filter { method ->
            isOnClick(method) && method.invokes.any {
                it.declaredClassName == MESSAGE_MANAGER && it.methodName == "dispatchResponsedMessage" &&
                    it.paramTypeNames == listOf("com.baidu.adp.framework.message.ResponsedMessage")
            } && method.usingFields.any {
                it.usingType.isWrite() && it.field.declaredClassName == "com.baidu.tbadk.TbSingleton" &&
                    it.field.fieldName == "isClickSpan" && it.field.typeName == "boolean"
            }
        }.filter {
            val clazz = ScanReflection.safeFindClass(it.declaredClassName, cl)
            clazz != null && ClickableSpan::class.java.isAssignableFrom(clazz) &&
                !clazz.isInterface && !Modifier.isAbstract(clazz.modifiers)
        }
        val click = unique("Click", clicks.distinctBy { it.descriptor }, logger) { it.descriptor } ?: return empty
        val owner = click.declaredClassName
        val classData = click.declaredClass ?: run {
            HookSymbolScanDiagnostics.log(logger, "$TAG declaring class unavailable: $owner")
            return empty
        }
        val fields = classData.fields.filter {
            !Modifier.isStatic(it.modifiers) && it.declaredClassName == owner
        }
        val clickReads = reads(click, owner).mapTo(HashSet()) { it.descriptor }

        // The discriminator is read both by dispatch and by the span's type predicates.
        val typeField = unique("TypeField", fields.filter { field ->
            field.typeName == "int" && field.descriptor in clickReads && field.readers.any { reader ->
                reader.declaredClassName == owner && !Modifier.isStatic(reader.modifiers) &&
                    reader.paramCount == 0 && reader.returnTypeName == "boolean" &&
                    reads(reader, owner).singleOrNull()?.descriptor == field.descriptor
            }
        }, logger) { it.descriptor } ?: return empty

        // The link string is initialized by the span constructor and later consumed by dispatch.
        val urlField = unique("UrlField", fields.filter { field ->
            field.typeName == "java.lang.String" && field.descriptor in clickReads && field.writers.any {
                it.declaredClassName == owner && it.methodName == "<init>" &&
                    "java.lang.String" in it.paramTypeNames
            }
        }, logger) { it.descriptor } ?: return empty

        // Rich-text construction writes the display text; message rendering reads the same field
        // for its text statistic. Intersect the two call paths instead of guessing a field ordinal.
        val textField = unique("TextField", fields.filter { field ->
            field.typeName == "java.lang.String" && field.descriptor != urlField.descriptor &&
                field.writers.any { setter ->
                    setter.declaredClassName == owner && !Modifier.isStatic(setter.modifiers) &&
                        setter.returnTypeName == "void" && setter.paramTypeNames == listOf("java.lang.String") &&
                        setter.callers.any { caller ->
                            caller.declaredClassName == RICH_TEXT_ITEM &&
                                caller.returnTypeName == "android.text.SpannableString" &&
                                caller.invokes.any { it.declaredClassName == "android.text.SpannableString" && it.methodName == "setSpan" }
                        }
                } && field.readers.any { getter ->
                    getter.declaredClassName == owner && !Modifier.isStatic(getter.modifiers) &&
                        getter.paramCount == 0 && getter.returnTypeName == "java.lang.String" &&
                        reads(getter, owner).singleOrNull()?.descriptor == field.descriptor &&
                        getter.callers.any { caller ->
                            (caller.declaredClassName == TEXT_MESSAGE_TEMPLATE ||
                                caller.declaredClassName.startsWith(TEXT_MESSAGE_TEMPLATE + "$")) &&
                                "obj_param1" in caller.usingStrings &&
                                caller.invokes.any {
                                    it.declaredClassName == "com.baidu.tbadk.core.util.StatisticItem" &&
                                        it.methodName == "param" &&
                                        it.paramTypeNames == listOf("java.lang.String", "java.lang.String")
                                }
                        }
                }
        }, logger) { it.descriptor } ?: return empty

        val onClick = click.getMethodInstance(cl)
        val spanClass = onClick.declaringClass
        if (!isStructureValid(spanClass, onClick, typeField.getFieldInstance(cl),
                urlField.getFieldInstance(cl), textField.getFieldInstance(cl))) {
            HookSymbolScanDiagnostics.log(logger, "$TAG restored structure mismatch: $owner")
            return empty
        }

        // Preserve the shared scan pool's feature scope; hierarchy verifies each owner without name hints.
        val ownerScope = candidates.toHashSet()
        val pending = ArrayDeque<String>()
        val visited = HashSet<String>()
        val owners = linkedSetOf(owner)
        pending.add(owner)
        while (pending.isNotEmpty()) {
            val parent = pending.removeFirst()
            if (!visited.add(parent)) continue
            val children = bridge.findClass(
                FindClass.create().searchPackages("com.baidu").matcher(ClassMatcher.create().superClass(parent)),
            )
            for (child in children) {
                pending.add(child.name)
                if (child.name !in ownerScope) continue
                val methods = child.methods.filter(::isOnClick)
                if (methods.isEmpty()) continue
                val method = unique("Owner.${child.name}", methods, logger) { it.descriptor } ?: return empty
                val restored = method.getMethodInstance(cl)
                if (!spanClass.isAssignableFrom(restored.declaringClass) || !isOnClickMethod(restored, "onClick")) {
                    HookSymbolScanDiagnostics.log(logger, "$TAG owner structure mismatch: ${child.name}")
                    return empty
                }
                owners.add(child.name)
            }
        }
        val orderedOwners = listOf(owner) + candidates.filter { it != owner && it in owners }.distinct()
        HookSymbolScanDiagnostics.log(
            logger, "$TAG matched: $owner.onClick owners=${owners.size} " +
                "fields=${typeField.fieldName}/${urlField.fieldName}/${textField.fieldName} evidence=dispatch/type-predicate/rich-text",
        )
        return PlainUrlClickableSpanScanSymbols(owner, click.methodName, orderedOwners,
            typeField.fieldName, urlField.fieldName, textField.fieldName)
    }

    private fun reads(method: MethodData, owner: String): List<FieldData> =
        method.usingFields.filter {
            it.usingType.isRead() && it.field.declaredClassName == owner && !Modifier.isStatic(it.field.modifiers)
        }.map { it.field }.distinctBy { it.descriptor }

    private fun isOnClick(method: MethodData): Boolean =
        !Modifier.isStatic(method.modifiers) && method.methodName == "onClick" &&
            method.returnTypeName == "void" && method.paramTypeNames == listOf("android.view.View")

    internal fun isOnClickMethod(method: Method, methodName: String): Boolean =
        methodName == "onClick" && method.name == methodName && !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(View::class.java))

    internal fun isStructureValid(
        spanClass: Class<*>, onClickMethod: Method, typeField: Field, urlField: Field, textField: Field,
    ): Boolean =
        !spanClass.isInterface && !Modifier.isAbstract(spanClass.modifiers) &&
            ClickableSpan::class.java.isAssignableFrom(spanClass) &&
            spanClass.isAssignableFrom(onClickMethod.declaringClass) && isOnClickMethod(onClickMethod, "onClick") &&
            listOf(typeField, urlField, textField).all {
                !Modifier.isStatic(it.modifiers) && it.declaringClass.isAssignableFrom(spanClass)
            } && ScanReflection.isIntType(typeField.type) &&
            urlField.type == String::class.java && textField.type == String::class.java && textField != urlField

    private fun <T> unique(
        role: String, candidates: List<T>, logger: ScanLogger?, describe: (T) -> String,
    ): T? = selectUniqueScanCandidate("$TAG.$role", candidates, logger, describe)
}

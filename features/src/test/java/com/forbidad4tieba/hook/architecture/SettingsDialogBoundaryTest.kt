package com.forbidad4tieba.hook.architecture

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.widget.Toast
import com.forbidad4tieba.hook.ui.showSettingsDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.jar.JarFile
import java.util.spi.ToolProvider
import android.app.AlertDialog as DialogAlias

class SettingsDialogBoundaryTest {
    @Test fun onlyTheSharedEntryShowsPlatformDialogs() {
        val classpath = checkNotNull(System.getProperty("features.production.classpath"))
            .split(File.pathSeparator).filter(String::isNotBlank).map(::File)
        val classes = compiledClasses(classpath)
        assertTrue("The production output must include the shared entry", ENTRY_CLASS in classes)
        assertEquals(
            "Use showSettingsDialog instead of calling a platform dialog's show directly",
            listOf("$ENTRY_CLASS#showSettingsDialog"),
            platformDialogShows(classpath, classes),
        )
    }

    @Test fun compiledExamplesRejectAliasesBuildersHelpersAndBaseReferences() {
        val fixture = DialogBoundaryExamples::class.java
        val location = checkNotNull(fixture.protectionDomain?.codeSource?.location)
        val classpath = listOf(File(location.toURI()))
        val classes = compiledClasses(classpath).filter { it.startsWith(fixture.name) }
        val calls = platformDialogShows(classpath, classes)
        assertEquals(
            setOf("direct", "builder", "aliased", "qualified", "helper", "base", "inherited", "<method-reference>", "showSettingsDialog"),
            calls.map { it.substringAfterLast('#') }.toSet(),
        )
        assertEquals(9, calls.size)
        // The same examples include a Toast and the real shared entry; neither is a violation.
    }

    private fun compiledClasses(classpath: List<File>): List<String> = classpath.flatMap { entry ->
        if (entry.isDirectory) {
            entry.walkTopDown().filter { it.isFile && it.extension == "class" && referencesShow(it.readBytes()) }
                .map { it.relativeTo(entry).invariantSeparatorsPath }.toList()
        } else {
            JarFile(entry).use { jar ->
                jar.entries().asSequence().filter { it.name.endsWith(".class") }
                    .filter { jar.getInputStream(it).use { stream -> referencesShow(stream.readBytes()) } }
                    .map { it.name }.toList()
            }
        }
    }.map { it.removeSuffix(".class").replace('/', '.') }.distinct().sorted()

    // Every JVM reference to show has that UTF-8 method name in its constant pool.
    private fun referencesShow(bytes: ByteArray): Boolean = bytes.toString(Charsets.ISO_8859_1).contains("show")

    private fun platformDialogShows(classpath: List<File>, classes: List<String>): List<String> {
        assertTrue("No compiled classes were inspected", classes.isNotEmpty())
        val output = StringWriter()
        val writer = PrintWriter(output)
        val javap = ToolProvider.findFirst("javap").orElseThrow { IllegalStateException("A JDK with javap is required") }
        val result = javap.run(writer, writer, "-v", "-p", "-classpath",
            classpath.joinToString(File.pathSeparator), *classes.toTypedArray())
        assertEquals(output.toString().take(2000), 0, result)
        val classHeader = Regex("""^(?:(?:public|protected|private|abstract|final|static) )*(?:class|interface) ([^\s<{]+)""")
        val methodHeader = Regex("""^  .*?([^\s(]+)\([^)]*\)(?: throws .*)?;$""")
        val invocation = Regex("""^\s*\d+: invoke\w+\s+.*// (?:InterfaceMethod|Method) ([^:]+):\(\)""")
        val methodHandle = Regex("""^\s*#\d+ = MethodHandle\s+.*// REF_invoke\w+ ([^:]+):\(\)""")
        var owner = ""
        var method = ""
        val calls = mutableListOf<String>()
        for (line in output.toString().lineSequence()) {
            classHeader.find(line)?.let { owner = it.groupValues[1]; method = "" }
            methodHeader.find(line)?.let { method = it.groupValues[1] }
            if (line == "  static {};") method = "<clinit>"
            val reference = methodHandle.find(line)
            val target = (reference ?: invocation.find(line))?.groupValues?.get(1) ?: continue
            if (target.substringAfterLast('.') != "show") continue
            val targetOwner = target.substringBeforeLast('.', owner).replace('/', '.')
            val type = Class.forName(targetOwner, false, javaClass.classLoader)
            if (Dialog::class.java.isAssignableFrom(type) || AlertDialog.Builder::class.java.isAssignableFrom(type)) {
                val caller = if (reference != null) "<method-reference>" else method
                check(owner.isNotBlank() && caller.isNotBlank()) { "Unattributed dialog call: $line" }
                calls += "$owner#$caller"
            }
        }
        return calls.sorted()
    }

    private companion object {
        const val ENTRY_CLASS = "com.forbidad4tieba.hook.ui.SettingsMenuUiKt"
    }
}

// Compiled, never executed: exercise the same bytecode check as production without constructing Android views.
private object DialogBoundaryExamples {
    fun direct(dialog: AlertDialog) = dialog.show()
    fun builder(context: Context): AlertDialog = AlertDialog.Builder(context).show()
    fun aliased(dialog: DialogAlias) = dialog.show()
    fun qualified(context: Context): AlertDialog = android.app.AlertDialog.Builder(context).show()
    fun throughHelper(dialog: AlertDialog) = helper(dialog)
    private fun helper(dialog: AlertDialog) = dialog.show()
    fun base(dialog: Dialog) = dialog.show()
    fun inherited(dialog: DerivedDialog) = dialog.show()
    fun reference(dialog: AlertDialog): Runnable = Runnable(dialog::show)
    fun showSettingsDialog(dialog: Dialog) = dialog.show()
    fun valid(dialog: AlertDialog, density: Float) = dialog.showSettingsDialog(density)
    fun unrelated(toast: Toast) = toast.show()
    class DerivedDialog(context: Context) : AlertDialog(context)
}

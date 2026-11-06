package com.forbidad4tieba.hook.feature.ui

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object CollectionSearchCacheFile {
    fun write(file: File, text: String) = write(file, { it.write(text.toByteArray(Charsets.UTF_8)) })

    @Synchronized
    internal fun write(
        file: File,
        writeContent: (FileOutputStream) -> Unit,
        replace: (File, File) -> Unit = { source, destination ->
            Files.move(source.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        },
    ) {
        val parent = requireNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create collection cache directory" }
        val temporary = File(parent, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                writeContent(stream)
                stream.fd.sync()
            }
            replace(temporary, file)
        } finally {
            temporary.delete()
        }
    }
}

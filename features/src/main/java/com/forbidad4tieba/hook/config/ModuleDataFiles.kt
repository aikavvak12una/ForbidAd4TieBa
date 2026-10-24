package com.forbidad4tieba.hook.config

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Deletes an exact owned file/tree; links inside a tree are removed without following them. */
internal object ModuleDataFiles {
    fun resolve(root: File, relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute && '\\' !in relative && ':' !in relative &&
            relative.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid module data path" }
        val parent = root.canonicalFile
        check(!parent.exists() || parent.isDirectory) { "Module data root is not a directory" }
        val requested = File(parent, relative).absoluteFile.toPath().normalize()
        val resolved = requested.toFile().canonicalFile.toPath()
        check(resolved.startsWith(parent.toPath()) && resolved != parent.toPath() && resolved == requested) {
            "Module data path leaves its owned location"
        }
        return requested.toFile()
    }

    fun delete(root: File, relative: String): Boolean {
        val path = resolve(root, relative).toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(directory: Path, failure: IOException?): FileVisitResult {
                if (failure != null) throw failure
                Files.delete(directory)
                return FileVisitResult.CONTINUE
            }
        })
        check(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "Module data path still exists" }
        return true
    }
}

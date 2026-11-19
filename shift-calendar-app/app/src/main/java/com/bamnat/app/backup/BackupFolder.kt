package com.bamnat.app.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/**
 * 2.0.0 [RVL-52] (검토 U12-06·I12a-03) The folder 매일 자동 백업 keeps its dated files in: the files in it by name, one made or found
 * by name, one taken away. [TreeFolder] is a folder the user picked with the system's folder picker (on the phone or in Google Drive);
 * [FileFolder] is a plain directory, for the tests.
 */
interface BackupFolder {
    /** The folder's name as its app shows it. */
    val name: String?

    /** The names of the files in it. */
    fun list(): List<String>

    /** The file named [name], made empty when it is not there yet; the second is true when it was made just now. */
    fun open(name: String, mime: String): Pair<Uri, Boolean>

    fun delete(name: String): Boolean
}

class TreeFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val root: Uri by lazy { DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) }

    override val name: String?
        get() = runCatching {
            resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun children(): List<Pair<String, Uri>> {
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(root))
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(list, cols, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val n = c.getString(1) ?: continue
                    add(n to DocumentsContract.buildDocumentUriUsingTree(tree, id))
                }
            }
        } ?: throw FileNotFoundException()
    }

    override fun list(): List<String> = children().map { it.first }

    override fun open(name: String, mime: String): Pair<Uri, Boolean> {
        children().firstOrNull { it.first == name }?.let { return it.second to false }
        val made = DocumentsContract.createDocument(resolver, root, mime, name) ?: throw FileNotFoundException()
        return made to true
    }

    override fun delete(name: String): Boolean {
        val uri = children().firstOrNull { it.first == name }?.second ?: return false
        return runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
    }
}

class FileFolder(private val dir: File) : BackupFolder {
    override val name: String get() = dir.name

    override fun list(): List<String> {
        if (!dir.isDirectory) throw FileNotFoundException()
        return dir.listFiles().orEmpty().filter { it.isFile }.map { it.name }
    }

    override fun open(name: String, mime: String): Pair<Uri, Boolean> {
        if (!dir.isDirectory) throw FileNotFoundException()
        val f = File(dir, name)
        val made = f.createNewFile()
        return Uri.fromFile(f) to made
    }

    override fun delete(name: String): Boolean = File(dir, name).delete()
}

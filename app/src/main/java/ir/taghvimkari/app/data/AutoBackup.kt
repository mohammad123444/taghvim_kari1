package ir.taghvimkari.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Writes the automatic backup into a folder the person picked once (it stays after the app is removed). */
object AutoBackup {
    /** How many automatic backup files are kept in the folder; older ones are deleted. */
    const val KEEP = 3

    /** Creates [fileName] in the picked folder [treeUri] and returns its uri, or null when that failed. */
    fun write(context: Context, treeUri: String, fileName: String, content: String): String? {
        val resolver = context.contentResolver
        val tree = Uri.parse(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", fileName) ?: return null
        val stream = resolver.openOutputStream(doc, "wt") ?: return null
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return doc.toString()
    }

    fun delete(context: Context, documentUri: String) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(documentUri)) }
    }
}

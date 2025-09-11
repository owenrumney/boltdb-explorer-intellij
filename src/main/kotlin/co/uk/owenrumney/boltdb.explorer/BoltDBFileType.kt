package co.uk.owenrumney.boltdb.explorer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * File type for BoltDB database files
 */
class BoltDBFileType : FileType {

    companion object {
        val INSTANCE = BoltDBFileType()
        const val DEFAULT_EXTENSION = "db"
    }

    override fun getName(): String = "BoltDB"

    override fun getDescription(): String = "BoltDB Database File"

    override fun getDefaultExtension(): String = DEFAULT_EXTENSION

    override fun getIcon(): Icon? = IconLoader.getIcon("/icons/boltdb.png", BoltDBFileType::class.java)

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}

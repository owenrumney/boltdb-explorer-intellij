package co.uk.owenrumney.boltdb.explorer

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.icons.AllIcons
import com.jetbrains.rd.util.threading.coroutines.RdCoroutineScope.Companion.override
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

    override fun getIcon(): Icon? = AllIcons.Toolwindows.ToolWindowDataView

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}

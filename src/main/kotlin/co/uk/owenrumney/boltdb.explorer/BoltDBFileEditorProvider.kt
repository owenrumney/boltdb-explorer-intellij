package co.uk.owenrumney.boltdb.explorer

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * File editor provider for BoltDB database files
 */
class BoltDBFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.isDirectory) return false

        val extension = file.extension?.lowercase()
        return extension in setOf("db", "bolt", "boltdb")
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return BoltDBFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "BoltDBFileEditor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

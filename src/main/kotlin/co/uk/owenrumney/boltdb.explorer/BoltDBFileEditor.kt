package co.uk.owenrumney.boltdb.explorer

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Custom file editor for BoltDB database files
 */
class BoltDBFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val explorerPanel = BoltDBExplorerPanel(project)

    init {
        // Open the database file automatically when the editor is created
        explorerPanel.openDatabase(file.path)
        
        // Register for proper cleanup
        Disposer.register(this, explorerPanel)
    }

    override fun getComponent(): JComponent = explorerPanel

    override fun getPreferredFocusedComponent(): JComponent = explorerPanel

    override fun getName(): String = "BoltDB Explorer"

    override fun getState(level: FileEditorStateLevel): FileEditorState =
        FileEditorState.INSTANCE

    override fun setState(state: FileEditorState) {
        // No state to restore
    }

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        // No properties to listen to
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        // No properties to listen to
    }

    override fun getFile(): VirtualFile = file

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun getStructureViewBuilder(): StructureViewBuilder? = null

    override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

    override fun dispose() {
        // Cleanup if needed
    }
}

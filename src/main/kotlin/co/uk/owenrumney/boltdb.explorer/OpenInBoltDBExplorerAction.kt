package co.uk.owenrumney.boltdb.explorer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action to open a BoltDB file in the BoltDB Explorer editor
 */
class OpenInBoltDBExplorerAction : AnAction("Open in BoltDB Explorer") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Show action only if we have a project and a file with supported extension
        e.presentation.isVisible = project != null && virtualFile != null && isBoltDBFile(virtualFile)
        e.presentation.isEnabled = e.presentation.isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!isBoltDBFile(virtualFile)) return

        // Open the file in the editor - our custom editor provider will handle it
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    private fun isBoltDBFile(file: VirtualFile): Boolean {
        if (file.isDirectory) return false

        val extension = file.extension?.lowercase()
        return extension in setOf("db", "bolt", "boltdb")
    }
}

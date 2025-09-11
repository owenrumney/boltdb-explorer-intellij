package co.uk.owenrumney.boltdb.explorer

import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import java.util.Locale.getDefault
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Dialog for displaying search results
 */
class SearchResultsDialog(
    private val parent: BoltDBExplorerPanel,
    private val searchResult: SearchResult
) : JDialog(SwingUtilities.getWindowAncestor(parent) as? Window, "Search Results", ModalityType.APPLICATION_MODAL) {

    private val resultsTableModel = DefaultTableModel(arrayOf("Name", "Path", "Type"), 0)
    private val resultsTable = JBTable(resultsTableModel)

    init {
        setupUI()
        populateResults()
    }

    private fun setupUI() {
        layout = BorderLayout()
        size = Dimension(600, 400)
        setLocationRelativeTo(parent)

        // Header
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.empty(8)

        val titleLabel = JBLabel("Search Results (${searchResult.safeItems.size})")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        if (searchResult.limited) {
            val limitedLabel = JBLabel("Results limited to ${searchResult.safeItems.size} items")
            limitedLabel.foreground = Color.ORANGE
            headerPanel.add(limitedLabel, BorderLayout.EAST)
        }

        add(headerPanel, BorderLayout.NORTH)

        // Results table
        resultsTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultsTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = resultsTable.selectedRow
                    if (row >= 0) {
                        navigateToResult(row)
                    }
                }
            }
        })

        // Set column widths - Path is the most important column
        resultsTable.columnModel.getColumn(0).preferredWidth = 150 // Name
        resultsTable.columnModel.getColumn(1).preferredWidth = 300 // Path (most important)
        resultsTable.columnModel.getColumn(2).preferredWidth = 80  // Type

        val scrollPane = JBScrollPane(resultsTable)
        add(scrollPane, BorderLayout.CENTER)

        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.border = JBUI.Borders.empty(8)

        val navigateButton = JButton("Navigate to Selected")
        navigateButton.addActionListener {
            val row = resultsTable.selectedRow
            if (row >= 0) {
                navigateToResult(row)
            }
        }
        buttonPanel.add(navigateButton)

        val closeButton = JButton("Close")
        closeButton.addActionListener { dispose() }
        buttonPanel.add(closeButton)

        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun populateResults() {
        searchResult.safeItems.forEach { item ->
            val decodedName = try {
                java.util.Base64.getDecoder().decode(item.keyBase64).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                "[invalid base64]"
            }

            val typeLabel = item.type.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(getDefault()) else ch.toString()
            }

            resultsTableModel.addRow(arrayOf(
                decodedName,
                item.path.joinToString("/"),
                typeLabel
            ))
        }
    }

    private fun navigateToResult(row: Int) {
        val item = searchResult.safeItems[row]
        val bucketPath = item.path.joinToString("/")
        val decodedName = try {
            java.util.Base64.getDecoder().decode(item.keyBase64).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            "[invalid base64]"
        }

        // Close dialog and navigate in parent
        dispose()

        // Navigate to the specific result in the parent panel
        parent.navigateToAndSelect(bucketPath, decodedName)
    }
}

/**
 * Dialog for adding a new key-value pair
 */
class AddKeyDialog(private val parent: BoltDBExplorerPanel) : JDialog(SwingUtilities.getWindowAncestor(parent) as? Window, "Add Key", ModalityType.APPLICATION_MODAL) {

    private val keyNameField = JBTextField()
    private val valueArea = JBTextArea(10, 30)

    init {
        setupUI()
    }

    private fun setupUI() {
        layout = BorderLayout()
        size = Dimension(500, 400)
        setLocationRelativeTo(parent)

        val contentPanel = JPanel(BorderLayout())
        contentPanel.border = JBUI.Borders.empty(16)

        // Form fields
        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(0, 0, 8, 8)
        formPanel.add(JBLabel("Key Name:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        keyNameField.preferredSize = Dimension(300, keyNameField.preferredSize.height)
        formPanel.add(keyNameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        gbc.insets = JBUI.insets(8, 0, 0, 8)
        formPanel.add(JBLabel("Value:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.insets = JBUI.insets(8, 0, 0, 0)
        valueArea.lineWrap = true
        valueArea.wrapStyleWord = true
        val scrollPane = JBScrollPane(valueArea)
        formPanel.add(scrollPane, gbc)

        contentPanel.add(formPanel, BorderLayout.CENTER)
        add(contentPanel, BorderLayout.CENTER)

        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.border = JBUI.Borders.empty(8)

        val addButton = JButton("Add Key")
        addButton.addActionListener { addKey() }
        buttonPanel.add(addButton)

        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener { dispose() }
        buttonPanel.add(cancelButton)

        add(buttonPanel, BorderLayout.SOUTH)

        // Focus on key name field
        SwingUtilities.invokeLater { keyNameField.requestFocus() }
    }

    private fun addKey() {
        val keyName = keyNameField.text.trim()
        val value = valueArea.text

        if (keyName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Key name cannot be empty", "Validation Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        // TODO: Call parent method to add the key
        // This would require exposing an addKey method in BoltDBExplorerPanel

        dispose()
    }
}

/**
 * Dialog for editing a key's value
 */
class EditValueDialog(
    private val parent: BoltDBExplorerPanel,
    private val keyName: String,
    private val currentValue: String
) : JDialog(SwingUtilities.getWindowAncestor(parent) as? Window, "Edit Value", ModalityType.APPLICATION_MODAL) {

    private val valueArea = JBTextArea(15, 40)

    init {
        setupUI()
    }

    private fun setupUI() {
        layout = BorderLayout()
        size = Dimension(600, 500)
        setLocationRelativeTo(parent)

        val contentPanel = JPanel(BorderLayout())
        contentPanel.border = JBUI.Borders.empty(16)

        // Header
        val headerLabel = JBLabel("Editing: $keyName")
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 14f)
        contentPanel.add(headerLabel, BorderLayout.NORTH)

        // Value editor
        valueArea.text = currentValue
        valueArea.lineWrap = true
        valueArea.wrapStyleWord = true
        valueArea.caretPosition = 0

        val scrollPane = JBScrollPane(valueArea)
        scrollPane.border = JBUI.Borders.empty(8, 0)
        contentPanel.add(scrollPane, BorderLayout.CENTER)

        add(contentPanel, BorderLayout.CENTER)

        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.border = JBUI.Borders.empty(8)

        val saveButton = JButton("Save")
        saveButton.addActionListener { saveValue() }
        buttonPanel.add(saveButton)

        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener { dispose() }
        buttonPanel.add(cancelButton)

        add(buttonPanel, BorderLayout.SOUTH)

        // Focus on text area
        SwingUtilities.invokeLater { valueArea.requestFocus() }
    }

    private fun saveValue() {
        val newValue = valueArea.text

        // TODO: Call parent method to save the value
        // This would require exposing a saveValue method in BoltDBExplorerPanel

        dispose()
    }
}

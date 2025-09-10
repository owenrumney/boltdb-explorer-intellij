package co.uk.owenrumney.boltdb.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.Disposable
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.AsyncProcessIcon
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.border.EmptyBorder
import javax.swing.Box
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Main panel for BoltDB Explorer - equivalent to the React App component
 */
class BoltDBExplorerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    // Core state management
    private var currentPath: String = ""
    private var isWriteMode: Boolean = false
    private var selectedKey: BoltKey? = null
    private var nextAfterKey: String? = null
    private var isLoading: Boolean = false
    private var currentDbPath: String? = null

    // UI components
    private val keysTableModel = DefaultTableModel(arrayOf("Key", "Size", "Type"), 0)
    private val keysTable = createKeysTable()
    private val previewPanel = createPreviewPanel()
    private val mainSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
    private val searchField = JBTextField()
    private val caseSensitiveCheckBox = JBCheckBox("Case sensitive")
    
    // Loading components
    private val loadingPanel = createLoadingPanel()
    private val keysPanel = JPanel(BorderLayout())

    // Communication with Go helper
    private val boltHelper = BoltDBClient()
    
    // Thread management for proper cleanup
    private val executorService: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "BoltDB-Explorer-Thread").apply {
            isDaemon = true
        }
    }

    init {
        setupUI()
    }

    override fun dispose() {
        // Shutdown executor service gracefully
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    println("BoltDB Explorer: Thread pool did not terminate cleanly")
                }
            }
        } catch (ie: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        // Clear any remaining references
        selectedKey = null
        currentDbPath = null
        
        println("BoltDB Explorer: Panel disposed successfully")
    }

    /**
     * Public method to open a database file programmatically
     * Called from the file editor when a database file is opened
     */
    fun openDatabase(dbPath: String) {
        println("BoltDB Explorer: openDatabase called with path: $dbPath")
        selectDatabase(dbPath)
    }

    /**
     * Public method to navigate to a specific path in the database
     * Used by search results and other components
     */
    fun navigateTo(path: String) {
        navigateToPath(path)
    }

    /**
     * Public method to navigate to a path and select a specific item
     * Used by search results to highlight the found item
     */
    fun navigateToAndSelect(path: String, itemName: String) {
        currentPath = path
        clearPreviewPanel()
        refreshHeaderPanel()
        // Load keys and select the item after loading completes
        loadKeys(path, afterKey = null, append = false) {
            selectItemInTable(itemName)
        }
    }

    private fun setupUI() {
        // Clear any existing content
        removeAll()

        // Create top panel with header and search
        val topPanel = JPanel(BorderLayout())
        topPanel.add(createHeaderPanel(), BorderLayout.NORTH)
        topPanel.add(createSearchPanel(), BorderLayout.CENTER)
        add(topPanel, BorderLayout.NORTH)

        // Setup main content area with equal-width split pane
        setupKeysPanel()
        mainSplitPane.leftComponent = keysPanel
        mainSplitPane.rightComponent = previewPanel
        mainSplitPane.resizeWeight = 0.5  // Equal width distribution
        mainSplitPane.setDividerLocation(0.5)
        mainSplitPane.border = JBUI.Borders.empty(0, 0, 0, 0)
        add(mainSplitPane, BorderLayout.CENTER)

        // Force layout refresh
        revalidate()
        repaint()
    }

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(16, 20, 12, 20)
        panel.background = UIUtil.getPanelBackground()

        // Title and mode toggle
        val topPanel = JPanel(BorderLayout())
        topPanel.background = UIUtil.getPanelBackground()
        
        val titleLabel = JBLabel("BoltDB Viewer")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 18f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        topPanel.add(titleLabel, BorderLayout.WEST)

        // Modern toggle switch for write mode
        val modePanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        modePanel.background = UIUtil.getPanelBackground()
        
        val writeModeToggle = createModernToggle("Write Mode", isWriteMode) { enabled ->
            isWriteMode = enabled
            refreshUI()
        }
        modePanel.add(writeModeToggle)
        topPanel.add(modePanel, BorderLayout.EAST)

        panel.add(topPanel, BorderLayout.NORTH)

        // Breadcrumb navigation with modern styling
        val breadcrumbPanel = createBreadcrumbPanel()
        panel.add(breadcrumbPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createModernToggle(text: String, isSelected: Boolean, onToggle: (Boolean) -> Unit): JPanel {
        val togglePanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        togglePanel.background = UIUtil.getPanelBackground()
        
        val label = JBLabel(text)
        label.foreground = UIUtil.getLabelForeground()
        label.font = label.font.deriveFont(Font.PLAIN, 13f)
        
        val toggleSwitch = JBCheckBox()
        toggleSwitch.isSelected = isSelected
        toggleSwitch.addActionListener { onToggle(toggleSwitch.isSelected) }
        
        togglePanel.add(label)
        togglePanel.add(toggleSwitch)
        
        return togglePanel
    }

    private fun createBreadcrumbPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 4))
        panel.background = UIUtil.getPanelBackground()

        // Root link with modern styling
        val rootLink = createModernBreadcrumbLink("Root") { navigateToPath("") }
        panel.add(rootLink)

        if (currentPath.isNotEmpty()) {
            val pathParts = currentPath.split("/")
            var currentSegment = ""

            for (i in pathParts.indices) {
                if (i > 0) currentSegment += "/"
                currentSegment += pathParts[i]

                // Modern separator
                val separator = JBLabel(" / ")
                separator.foreground = UIUtil.getLabelDisabledForeground()
                separator.font = separator.font.deriveFont(Font.PLAIN, 13f)
                panel.add(separator)

                val pathToNavigate = currentSegment
                val segmentLink = createModernBreadcrumbLink(pathParts[i]) { navigateToPath(pathToNavigate) }
                panel.add(segmentLink)
            }
        }

        return panel
    }

    private fun createModernBreadcrumbLink(text: String, onClick: () -> Unit): JLabel {
        val link = JBLabel(text)
        link.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        link.font = link.font.deriveFont(Font.PLAIN, 13f)
        link.foreground = Color(0x589df6) // Modern blue color
        
        link.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                onClick()
            }
            
            override fun mouseEntered(e: MouseEvent?) {
                link.foreground = Color(0x4a90e2) // Darker blue on hover
            }
            
            override fun mouseExited(e: MouseEvent?) {
                link.foreground = Color(0x589df6) // Back to original blue
            }
        })
        
        return link
    }

    // Legacy method for compatibility - can be removed later
    private fun createBreadcrumbLink(text: String, onClick: () -> Unit): JLabel {
        return createModernBreadcrumbLink(text, onClick)
    }

    private fun createSearchPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 20, 16, 20)  // Match header padding
        panel.background = UIUtil.getPanelBackground()

        // Use GroupLayout for proper baseline alignment
        val searchControls = JPanel()
        searchControls.background = UIUtil.getPanelBackground()
        
        // Configure search field with minimal styling - just bottom border for clarity
        searchField.preferredSize = Dimension(400, -1) // Let height be natural
        searchField.emptyText.text = "Search keys/values..."
        searchField.font = searchField.font.deriveFont(Font.PLAIN, 13f)
        // Add subtle bottom border to indicate it's an input field
        searchField.border = JBUI.Borders.customLineBottom(Color.GRAY)
        
        // Create button with natural sizing
        val searchButton = createModernButton("Search", isPrimary = true, isCompact = false)
        
        // Configure case sensitive checkbox
        caseSensitiveCheckBox.font = caseSensitiveCheckBox.font.deriveFont(Font.PLAIN, 12f)
        caseSensitiveCheckBox.foreground = UIUtil.getLabelForeground()
        
        // Use GroupLayout for baseline alignment
        val layout = GroupLayout(searchControls)
        searchControls.layout = layout
        layout.autoCreateGaps = true
        layout.autoCreateContainerGaps = false
        
        // Horizontal grouping
        layout.setHorizontalGroup(
            layout.createSequentialGroup()
                .addComponent(searchField)
                .addComponent(searchButton)
                .addComponent(caseSensitiveCheckBox)
        )
        
        // Vertical grouping with baseline alignment
        layout.setVerticalGroup(
            layout.createBaselineGroup(true, false)
                .addComponent(searchField)
                .addComponent(searchButton)
                .addComponent(caseSensitiveCheckBox)
        )
        
        // Clear existing action listeners to avoid duplicates when UI is refreshed
        searchField.actionListeners.forEach { searchField.removeActionListener(it) }
        searchButton.actionListeners.forEach { searchButton.removeActionListener(it) }
        
        // Add Enter key support to search field
        searchField.addActionListener { 
            if (searchButton.isEnabled && searchField.text.trim().isNotEmpty()) {
                performSearch(searchField.text.trim(), caseSensitiveCheckBox.isSelected, searchButton)
            }
        }
        
        searchButton.addActionListener { 
            if (searchField.text.trim().isNotEmpty()) {
                performSearch(searchField.text.trim(), caseSensitiveCheckBox.isSelected, searchButton)
            }
        }

        panel.add(searchControls, BorderLayout.NORTH)

        return panel
    }

    private fun createModernButton(text: String, isPrimary: Boolean = false, isCompact: Boolean = true): JButton {
        val button = JButton(text)
        
        // Use smaller font
        button.font = button.font.deriveFont(Font.PLAIN, 12f)
        
        // Let IntelliJ handle the theme-appropriate styling, just adjust size
        if (isCompact) {
            // Reduce margin for compact buttons
            button.margin = java.awt.Insets(2, 6, 2, 6)
        } else {
            button.margin = java.awt.Insets(4, 10, 4, 10)
        }
        
        // Only customize delete buttons to be red
        if (text.contains("Delete") || text.contains("🗑")) {
            button.foreground = Color(0xD73A49) // Red color for delete
        }
        
        return button
    }

    private fun createKeysContentPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = UIUtil.getPanelBackground()
        panel.border = JBUI.Borders.empty(0, 20, 0, 10)

        // Header with modern styling
        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = UIUtil.getPanelBackground()
        headerPanel.border = JBUI.Borders.empty(0, 0, 12, 0)
        
        val titleLabel = JBLabel("Database Contents")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))  // Smaller gap between buttons
        actionsPanel.background = UIUtil.getPanelBackground()
        
        if (isWriteMode) {
            val addBucketButton = createModernButton("+ Add Bucket")
            addBucketButton.addActionListener { showAddBucketDialog() }
            actionsPanel.add(addBucketButton)

            if (currentPath.isNotEmpty()) {
                val addKeyButton = createModernButton("+ Add Key")
                addKeyButton.addActionListener { showAddKeyDialog() }
                actionsPanel.add(addKeyButton)
            }
        }
        headerPanel.add(actionsPanel, BorderLayout.EAST)

        panel.add(headerPanel, BorderLayout.NORTH)

        // Enhanced table with modern styling
        val scrollPane = JBScrollPane(keysTable)
        scrollPane.border = null  // Remove outer border
        scrollPane.background = UIUtil.getTableBackground()
        setupInfiniteScroll(scrollPane)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun setupInfiniteScroll(scrollPane: JBScrollPane) {
        val scrollBar = scrollPane.verticalScrollBar
        scrollBar.addAdjustmentListener { e ->
            // Check if we're near the bottom (within 20% of the total)
            val currentValue = e.value
            val maximum = scrollBar.maximum - scrollBar.visibleAmount
            val threshold = maximum * 0.8 // Load more when 80% scrolled
            
            // Load more if we're near bottom, not already loading, and have more data
            if (currentValue >= threshold && !isLoading && nextAfterKey != null) {
                loadMoreKeys()
            }
        }
    }

    private fun createKeysTable(): JBTable {
        val table = object : JBTable(keysTableModel) {
            // Disable cell editing completely
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }

        // Modern table styling
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.background = UIUtil.getTableBackground()
        table.selectionBackground = UIUtil.getListSelectionBackground(true)
        table.selectionForeground = UIUtil.getListSelectionForeground(true)
        table.gridColor = Color.LIGHT_GRAY
        table.showHorizontalLines = true
        table.showVerticalLines = false
        table.intercellSpacing = Dimension(0, 1)
        table.rowHeight = 28
        table.font = table.font.deriveFont(Font.PLAIN, 13f)
        
        // Enhanced header styling
        val header = table.tableHeader
        header.background = UIUtil.getPanelBackground()
        header.foreground = UIUtil.getLabelForeground()
        header.font = header.font.deriveFont(Font.BOLD, 12f)
        header.border = JBUI.Borders.customLineBottom(Color.GRAY)
        
        // Custom cell renderer for better styling
        val cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean,
                row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                
                if (!isSelected) {
                    background = if (row % 2 == 0) UIUtil.getTableBackground() else UIUtil.getTableBackground().brighter()
                }
                
                border = JBUI.Borders.empty(4, 12)
                font = font.deriveFont(Font.PLAIN, 13f)
                
                // Special styling for different content types using theme colors
                if (column == 2) { // Type column
                    when (value?.toString()) {
                        "Bucket" -> {
                            foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
                            font = font.deriveFont(Font.BOLD)
                        }
                        "Value" -> {
                            foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
                        }
                    }
                } else {
                    // All other columns use standard theme colors
                    foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()
                }
                
                return component
            }
        }
        
        // Apply renderer to all columns
        for (i in 0 until table.columnCount) {
            table.columnModel.getColumn(i).cellRenderer = cellRenderer
        }
        
        // Set column widths
        table.columnModel.getColumn(0).preferredWidth = 200 // Key
        table.columnModel.getColumn(1).preferredWidth = 100 // Size  
        table.columnModel.getColumn(2).preferredWidth = 80  // Type
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.selectedRow
                if (row >= 0) {
                    val keyName = keysTableModel.getValueAt(row, 0) as String
                    
                    // Skip interaction with loading indicator row
                    if (keyName == "Loading more...") {
                        return
                    }
                    
                    val type = keysTableModel.getValueAt(row, 2) as String
                    val isBucket = type == "Bucket"
                    
                    if (e.clickCount == 1) {
                        // Store selected key for write mode toggle refresh
                        val keyBase64 = java.util.Base64.getEncoder().encodeToString(keyName.toByteArray(Charsets.UTF_8))
                        selectedKey = BoltKey(keyBase64, 0, isBucket) // Size doesn't matter for refresh
                        
                        // Single click - show details/preview
                        if (isBucket) {
                            showBucketDetails(keyName)
                        } else {
                            loadKeyPreview(keyName)
                        }
                    } else if (e.clickCount == 2 && isBucket) {
                        // Double click on bucket - navigate into it
                        val newPath = if (currentPath.isEmpty()) keyName else "$currentPath/$keyName"
                        navigateToPath(newPath)
                    }
                }
            }
        })

        return table
    }

    private fun createPreviewPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(400, 300)
        panel.background = UIUtil.getPanelBackground()
        panel.border = JBUI.Borders.empty(0, 10, 0, 0)  // Remove right padding

        val emptyLabel = JBLabel("Select a key to view its content", SwingConstants.CENTER)
        emptyLabel.foreground = UIUtil.getLabelDisabledForeground()
        emptyLabel.font = emptyLabel.font.deriveFont(Font.PLAIN, 13f)
        panel.add(emptyLabel, BorderLayout.CENTER)

        return panel
    }

    private fun createLoadingPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = UIUtil.getPanelBackground()
        panel.isOpaque = true
        
        // Create center panel for spinner and text
        val centerPanel = JPanel(FlowLayout(FlowLayout.CENTER))
        centerPanel.background = UIUtil.getPanelBackground()
        
        // Create spinner
        val spinner = AsyncProcessIcon("Loading database")
        centerPanel.add(spinner)
        
        // Add loading text
        val loadingLabel = JBLabel("Opening database...")
        loadingLabel.foreground = UIUtil.getLabelForeground()
        loadingLabel.font = loadingLabel.font.deriveFont(Font.PLAIN, 14f)
        loadingLabel.border = JBUI.Borders.empty(0, 8, 0, 0)
        centerPanel.add(loadingLabel)
        
        panel.add(centerPanel, BorderLayout.CENTER)
        return panel
    }

    private fun setupKeysPanel() {
        keysPanel.removeAll()
        keysPanel.background = UIUtil.getPanelBackground()
        
        // Add the actual keys content panel
        val contentPanel = createKeysContentPanel()
        keysPanel.add(contentPanel, BorderLayout.CENTER)
        
        // Initially show loading if we're in loading state
        if (isLoading) {
            showLoadingOverlay()
        }
    }

    private fun showLoadingOverlay() {
        SwingUtilities.invokeLater {
            keysPanel.removeAll()
            keysPanel.add(loadingPanel, BorderLayout.CENTER)
            keysPanel.revalidate()
            keysPanel.repaint()
        }
    }

    private fun hideLoadingOverlay() {
        SwingUtilities.invokeLater {
            keysPanel.removeAll()
            val contentPanel = createKeysContentPanel()
            keysPanel.add(contentPanel, BorderLayout.CENTER)
            keysPanel.revalidate()
            keysPanel.repaint()
        }
    }

    private fun selectDatabase(dbPath: String) {
        boltHelper.setDatabasePath(dbPath)
        loadKeys("")
    }

    private fun loadKeys(bucketPath: String, afterKey: String? = null, append: Boolean = false, onComplete: (() -> Unit)? = null) {
        println("BoltDB Explorer: loadKeys called with bucketPath='$bucketPath', afterKey='$afterKey', append='$append'")
        
        if (!append) {
            // Only set loading state and show spinner for initial loads, not for infinite scroll appends
            SwingUtilities.invokeLater {
                isLoading = true
                showLoadingOverlay()
            }
        }

        executorService.submit {
            try {
                val result = boltHelper.listKeys(bucketPath, afterKey, limit = 100)
                SwingUtilities.invokeLater {
                    updateKeysTable(result, clearExisting = !append)
                    if (!append) {
                        isLoading = false
                        hideLoadingOverlay()
                    }
                    // Execute callback after table is updated
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to load keys: ${e.message}")
                    if (!append) {
                        isLoading = false
                        hideLoadingOverlay()
                    }
                }
            }
        }
    }

    private fun updateKeysTable(result: KeysResult, clearExisting: Boolean) {
        if (clearExisting) {
            keysTableModel.setRowCount(0)
        }
        
        for (key in result.safeItems) {
            val type = if (key.isBucket) "Bucket" else "Key"
            // Decode the base64 key for display
            val decodedKeyName = try {
                java.util.Base64.getDecoder().decode(key.keyBase64).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                key.keyBase64 // Fallback to base64 if decoding fails
            }
            
            // Show different size info for buckets vs keys
            val sizeDisplay = if (key.isBucket) {
                // For buckets, we need to get the item count - store as placeholder for now
                "Loading..." // Will be updated when bucket details are loaded
            } else {
                // For keys, show the value size
                formatSize(key.valueSize)
            }
            
            val row = arrayOf(decodedKeyName, sizeDisplay, type)
            keysTableModel.addRow(row)
        }
        
        // Load bucket item counts asynchronously
        loadBucketItemCounts(result.safeItems)
        
        nextAfterKey = result.nextAfterKey
    }

    private fun loadBucketItemCounts(items: List<BoltKey>) {
        // Load item counts for buckets in background
        executorService.submit {
            items.forEach { key ->
                if (key.isBucket) {
                    try {
                        val decodedBucketName = try {
                            java.util.Base64.getDecoder().decode(key.keyBase64).toString(Charsets.UTF_8)
                        } catch (e: Exception) {
                            key.keyBase64
                        }
                        
                        val bucketPath = if (currentPath.isEmpty()) decodedBucketName else "$currentPath/$decodedBucketName"
                        val bucketContents = boltHelper.listKeys(bucketPath, limit = 10000) // Higher limit for counting
                        
                        SwingUtilities.invokeLater {
                            // Find the row with this bucket name and update it
                            val itemCount = bucketContents.safeItems.size
                            val displayText = if (bucketContents.nextAfterKey != null) {
                                "$itemCount+ items" // Show "+" when there are more items
                            } else {
                                "$itemCount items"
                            }
                            
                            // Search for the row with matching bucket name
                            for (row in 0 until keysTableModel.rowCount) {
                                val tableBucketName = keysTableModel.getValueAt(row, 0) as? String
                                val tableType = keysTableModel.getValueAt(row, 2) as? String
                                
                                if (tableBucketName == decodedBucketName && tableType == "Bucket") {
                                    try {
                                        keysTableModel.setValueAt(displayText, row, 1) // Column 1 is the Size column
                                        break // Found and updated, no need to continue
                                    } catch (e: ArrayIndexOutOfBoundsException) {
                                        // Table was updated while we were processing, ignore this update
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Silently ignore errors in background loading
                        // The table will show "Loading..." which is acceptable
                    }
                }
            }
        }
    }

    private fun showBucketDetails(bucketName: String) {
        executorService.submit {
            try {
                // Load bucket contents to show statistics
                val bucketPath = if (currentPath.isEmpty()) bucketName else "$currentPath/$bucketName"
                val result = boltHelper.listKeys(bucketPath, limit = 10000) // Higher limit for accurate stats
                
                SwingUtilities.invokeLater {
                    updateBucketPreviewPanel(bucketName, result)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to load bucket details: ${e.message}")
                }
            }
        }
    }

    private fun loadKeyPreview(keyName: String) {
        executorService.submit {
            try {
                // keyName is already decoded, so we need to encode it for the backend
                val keyBase64 = java.util.Base64.getEncoder().encodeToString(keyName.toByteArray(Charsets.UTF_8))
                val result = boltHelper.getKeyHead(currentPath, keyBase64)
                SwingUtilities.invokeLater {
                    updateKeyPreviewPanel(keyName, result)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to load key preview: ${e.message}")
                }
            }
        }
    }

    private fun updateBucketPreviewPanel(bucketName: String, result: KeysResult) {
        previewPanel.removeAll()
        previewPanel.background = UIUtil.getPanelBackground()

        // Modern header with styling
        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = UIUtil.getPanelBackground()
        headerPanel.border = JBUI.Borders.empty(0, 0, 12, 0)
        
        val titleLabel = JBLabel("Selected: $bucketName")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        headerPanel.add(titleLabel, BorderLayout.WEST)

        if (isWriteMode) {
            val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))  // Smaller gap
            actionsPanel.background = UIUtil.getPanelBackground()
            
            val deleteButton = createModernButton("🗑 Delete", isCompact = true)
            deleteButton.addActionListener { confirmDelete(bucketName, true) }

            actionsPanel.add(deleteButton)
            headerPanel.add(actionsPanel, BorderLayout.EAST)
        }

        previewPanel.add(headerPanel, BorderLayout.NORTH)

        // Create bucket statistics
        val bucketStats = mutableMapOf<String, Any>()
        val buckets = result.safeItems.filter { it.isBucket }
        val keys = result.safeItems.filter { !it.isBucket }
        
        bucketStats["name"] = bucketName
        bucketStats["path"] = if (currentPath.isEmpty()) bucketName else "$currentPath/$bucketName"
        
        // Show accurate counts or indicate when limited
        val totalItems = result.safeItems.size
        bucketStats["totalItems"] = if (result.nextAfterKey != null) {
            "$totalItems+" // Indicate there are more items
        } else {
            totalItems
        }
        
        bucketStats["buckets"] = buckets.size
        bucketStats["keys"] = keys.size
        bucketStats["approximateReturned"] = result.approxReturned
        
        // Add a note if results are limited
        if (result.nextAfterKey != null) {
            bucketStats["note"] = "Counts may be incomplete - bucket has more than $totalItems items"
        }
        
        if (keys.isNotEmpty()) {
            bucketStats["totalKeySize"] = keys.sumOf { it.valueSize }
            bucketStats["averageKeySize"] = keys.map { it.valueSize }.average().toInt()
            bucketStats["largestKey"] = keys.maxOf { it.valueSize }
            bucketStats["smallestKey"] = keys.minOf { it.valueSize }
        }

        val contentArea = JBTextArea()
        contentArea.isEditable = false
        contentArea.text = formatJson(bucketStats)

        val scrollPane = JBScrollPane(contentArea)
        previewPanel.add(scrollPane, BorderLayout.CENTER)

        previewPanel.revalidate()
        previewPanel.repaint()
    }

    private fun updateKeyPreviewPanel(keyName: String, content: HeadResult) {
        previewPanel.removeAll()
        previewPanel.background = UIUtil.getPanelBackground()

        // Modern header with styling
        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = UIUtil.getPanelBackground()
        headerPanel.border = JBUI.Borders.empty(0, 0, 12, 0)
        
        val titleLabel = JBLabel("Selected: $keyName")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.foreground = UIUtil.getLabelForeground()
        headerPanel.add(titleLabel, BorderLayout.WEST)

        // Actions panel - Copy button always visible, Edit/Delete only in write mode
        val actionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))  // Smaller gap
        actionsPanel.background = UIUtil.getPanelBackground()
        
        // Copy button - always visible and functional
        val copyButton = createModernButton("Copy")
        copyButton.addActionListener { copyKeyValue(keyName, content.valueHeadBase64) }
        actionsPanel.add(copyButton)
        
        if (isWriteMode) {
            val editButton = createModernButton("Edit")
            val deleteButton = createModernButton("🗑 Delete", isCompact = true)

            editButton.addActionListener { showEditDialog(keyName, content.valueHeadBase64) }
            deleteButton.addActionListener { confirmDelete(keyName, false) }

            actionsPanel.add(editButton)
            actionsPanel.add(deleteButton)
        }
        
        headerPanel.add(actionsPanel, BorderLayout.EAST)

        previewPanel.add(headerPanel, BorderLayout.NORTH)

        val decodedContent = java.util.Base64.getDecoder().decode(content.valueHeadBase64).toString(Charsets.UTF_8)
        
        val contentArea = JBTextArea()
        contentArea.isEditable = false
        
        // Try to format as JSON if possible
        contentArea.text = try {
            formatJsonString(decodedContent)
        } catch (e: Exception) {
            decodedContent // Fallback to raw content
        }

        val scrollPane = JBScrollPane(contentArea)
        previewPanel.add(scrollPane, BorderLayout.CENTER)

        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        infoPanel.add(JBLabel("Size: ${formatSize(content.totalSize)}"))
        previewPanel.add(infoPanel, BorderLayout.SOUTH)

        previewPanel.revalidate()
        previewPanel.repaint()
    }

    private fun navigateToPath(path: String) {
        currentPath = path
        selectedKey = null // Clear selection when navigating
        clearPreviewPanel()
        refreshHeaderPanel()
        loadKeys(path)
    }

    private fun clearPreviewPanel() {
        previewPanel.removeAll()
        val emptyLabel = JBLabel("Select a key to view its content", SwingConstants.CENTER)
        previewPanel.add(emptyLabel, BorderLayout.CENTER)
        previewPanel.revalidate()
        previewPanel.repaint()
    }

    private fun refreshHeaderPanel() {
        // Rebuild breadcrumb navigation
        removeAll()
        setupUI()
    }

    private fun refreshUI() {
        // Update UI state based on current mode and loading state
        
        // Refresh the keys panel to show/hide action buttons
        if (isLoading) {
            showLoadingOverlay()
        } else {
            hideLoadingOverlay()
        }
        
        // If we have a selected key, refresh its preview to show/hide action buttons
        selectedKey?.let { key ->
            val keyName = try {
                java.util.Base64.getDecoder().decode(key.keyBase64).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                key.keyBase64
            }
            
            if (key.isBucket) {
                showBucketDetails(keyName)
            } else {
                loadKeyPreview(keyName)
            }
        }
        
        revalidate()
        repaint()
    }

    private fun loadMoreKeys() {
        nextAfterKey?.let { afterKey ->
            if (!isLoading) {
                isLoading = true
                
                // Add loading indicator row
                SwingUtilities.invokeLater {
                    keysTableModel.addRow(arrayOf("Loading more...", "", ""))
                }
                
                executorService.submit {
                    try {
                        loadKeys(currentPath, afterKey, append = true, onComplete = null)
                    } finally {
                        SwingUtilities.invokeLater {
                            // Remove loading indicator row (always the last row)
                            if (keysTableModel.rowCount > 0) {
                                val lastRowIndex = keysTableModel.rowCount - 1
                                val lastRowKey = keysTableModel.getValueAt(lastRowIndex, 0) as? String
                                if (lastRowKey == "Loading more...") {
                                    keysTableModel.removeRow(lastRowIndex)
                                }
                            }
                            isLoading = false
                        }
                    }
                }
            }
        }
    }

    private fun performSearch(query: String, caseSensitive: Boolean, searchButton: JButton) {
        // Update button state to show search is in progress
        SwingUtilities.invokeLater {
            searchButton.text = "Searching..."
            searchButton.isEnabled = false
        }
        
        executorService.submit {
            try {
                val results = boltHelper.search(query, caseSensitive = caseSensitive, limit = 100)
                SwingUtilities.invokeLater {
                    searchButton.text = "Search"
                    searchButton.isEnabled = true
                    showSearchResults(results)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    searchButton.text = "Search"
                    searchButton.isEnabled = true
                    val errorMessage = e.message ?: "Unknown error"
                    if (errorMessage.contains("JsonNull") || errorMessage.contains("null")) {
                        showError("Search failed: No results found or database connection issue")
                    } else {
                        showError("Search failed: $errorMessage")
                    }
                }
            }
        }
    }

    private fun showSearchResults(results: SearchResult) {
        val dialog = SearchResultsDialog(this, results)
        dialog.isVisible = true
    }

    private fun showAddBucketDialog() {
        val bucketName = Messages.showInputDialog(
            this,
            "Enter bucket name:",
            "Add Bucket",
            Messages.getQuestionIcon()
        )

        if (!bucketName.isNullOrBlank()) {
            createBucket(bucketName.trim())
        }
    }

    private fun showAddKeyDialog() {
        val dialog = AddKeyDialog(this)
        dialog.isVisible = true
    }

    private fun createBucket(bucketName: String) {
        executorService.submit {
            try {
                val bucketPath = if (currentPath.isEmpty()) bucketName else "$currentPath/$bucketName"
                boltHelper.createBucket(bucketPath)

                SwingUtilities.invokeLater {
                    navigateToPath(bucketPath)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to create bucket: ${e.message}")
                }
            }
        }
    }

    private fun showEditDialog(keyName: String, valueBase64: String) {
        val currentValue = java.util.Base64.getDecoder().decode(valueBase64).toString(Charsets.UTF_8)
        val dialog = EditValueDialog(this, keyName, currentValue)
        dialog.isVisible = true
    }

    /**
     * Public method called by AddKeyDialog to add a new key
     */
    fun addKey(keyName: String, value: String) {
        executorService.submit {
            try {
                val keyBase64 = java.util.Base64.getEncoder().encodeToString(keyName.toByteArray(Charsets.UTF_8))
                val valueBase64 = java.util.Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
                
                boltHelper.putKey(currentPath, keyBase64, valueBase64)
                
                SwingUtilities.invokeLater {
                    clearPreviewPanel()
                    loadKeys(currentPath) // Refresh to show new key
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to add key: ${e.message}")
                }
            }
        }
    }

    /**
     * Public method called by EditValueDialog to save an edited value
     */
    fun saveKeyValue(keyName: String, newValue: String) {
        executorService.submit {
            try {
                val keyBase64 = java.util.Base64.getEncoder().encodeToString(keyName.toByteArray(Charsets.UTF_8))
                val valueBase64 = java.util.Base64.getEncoder().encodeToString(newValue.toByteArray(Charsets.UTF_8))
                
                boltHelper.putKey(currentPath, keyBase64, valueBase64)
                
                SwingUtilities.invokeLater {
                    // Refresh the preview if this key is currently selected
                    selectedKey?.let { key ->
                        val selectedKeyName = try {
                            java.util.Base64.getDecoder().decode(key.keyBase64).toString(Charsets.UTF_8)
                        } catch (e: Exception) {
                            key.keyBase64
                        }
                        
                        if (selectedKeyName == keyName) {
                            loadKeyPreview(keyName) // Refresh preview with new value
                        }
                    }
                    loadKeys(currentPath) // Refresh table to show updated key
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to save key: ${e.message}")
                }
            }
        }
    }

    /**
     * Copy key value to system clipboard
     */
    private fun copyKeyValue(keyName: String, valueBase64: String) {
        try {
            val decodedValue = java.util.Base64.getDecoder().decode(valueBase64).toString(Charsets.UTF_8)
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = java.awt.datatransfer.StringSelection(decodedValue)
            clipboard.setContents(stringSelection, null)
            
            // Show a brief success message
            SwingUtilities.invokeLater {
                // You could show a toast notification here, but for now we'll just update the button text briefly
                // This is a simple feedback mechanism
                showCopyFeedback(keyName)
            }
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                showError("Failed to copy value: ${e.message}")
            }
        }
    }
    
    /**
     * Show brief feedback that copy was successful
     */
    private fun showCopyFeedback(keyName: String) {
        // For now, just show a simple message dialog
        // In a more sophisticated implementation, you might show a toast notification
        javax.swing.JOptionPane.showMessageDialog(
            this,
            "Value copied to clipboard!",
            "Copy Successful",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun confirmDelete(itemName: String, isBucket: Boolean) {
        val message = "Are you sure you want to delete ${if (isBucket) "bucket" else "key"} \"$itemName\"?"
        val result = Messages.showYesNoDialog(
            this,
            message,
            "Confirm Delete",
            Messages.getWarningIcon()
        )

        if (result == Messages.YES) {
            deleteItem(itemName, isBucket)
        }
    }

    private fun deleteItem(itemName: String, isBucket: Boolean) {
        executorService.submit {
            try {
                // itemName is already decoded, so we need to encode it for the backend
                val keyBase64 = java.util.Base64.getEncoder().encodeToString(itemName.toByteArray(Charsets.UTF_8))

                if (isBucket) {
                    // CRITICAL FIX: Construct the full path to the bucket being deleted
                    val bucketPath = if (currentPath.isEmpty()) itemName else "$currentPath/$itemName"
                    boltHelper.deleteBucket(bucketPath)
                    SwingUtilities.invokeLater {
                        // Stay in current path after deleting a sub-bucket
                        clearPreviewPanel()
                        loadKeys(currentPath)
                    }
                } else {
                    boltHelper.deleteKey(currentPath, keyBase64)
                    SwingUtilities.invokeLater {
                        clearPreviewPanel()
                        loadKeys(currentPath)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Failed to delete: ${e.message}")
                }
            }
        }
    }

    private fun showError(message: String) {
        Messages.showErrorDialog(this, message, "Error")
    }

    private fun formatSize(size: Int): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${(size / 1024.0).format(1)} KB"
            else -> "${(size / 1024.0 / 1024.0).format(1)} MB"
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun selectItemInTable(itemName: String) {
        println("BoltDB Explorer: selectItemInTable called with itemName='$itemName'")
        println("BoltDB Explorer: Table has ${keysTableModel.rowCount} rows")
        
        // Find and select the item in the table
        for (row in 0 until keysTableModel.rowCount) {
            val tableName = keysTableModel.getValueAt(row, 0) as? String
            println("BoltDB Explorer: Row $row has name='$tableName'")
            
            if (tableName == itemName) {
                println("BoltDB Explorer: Found matching item at row $row, selecting...")
                keysTable.setRowSelectionInterval(row, row)
                keysTable.scrollRectToVisible(keysTable.getCellRect(row, 0, true))
                
                // Show details/preview for the selected item
                val type = keysTableModel.getValueAt(row, 2) as String
                val isBucket = type == "Bucket"
                
                println("BoltDB Explorer: Item type is '$type', showing ${if (isBucket) "bucket details" else "key preview"}")
                
                if (isBucket) {
                    showBucketDetails(itemName)
                } else {
                    loadKeyPreview(itemName)
                }
                break
            }
        }
        
        println("BoltDB Explorer: selectItemInTable completed")
    }

    private fun formatJson(obj: Map<String, Any>): String {
        val json = StringBuilder("{\n")
        obj.entries.forEachIndexed { index, (key, value) ->
            json.append("  \"$key\": ")
            when (value) {
                is String -> json.append("\"$value\"")
                is Number -> json.append(value)
                else -> json.append("\"$value\"")
            }
            if (index < obj.size - 1) json.append(",")
            json.append("\n")
        }
        json.append("}")
        return json.toString()
    }

    private fun formatJsonString(jsonString: String): String {
        // Simple JSON formatter - checks if string looks like JSON and formats it
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return jsonString // Not JSON
        }
        
        try {
            // Basic JSON formatting with proper indentation
            var formatted = trimmed
            var indentLevel = 0
            val result = StringBuilder()
            var inString = false
            var escapeNext = false
            
            for (char in formatted) {
                when {
                    escapeNext -> {
                        result.append(char)
                        escapeNext = false
                    }
                    char == '\\' && inString -> {
                        result.append(char)
                        escapeNext = true
                    }
                    char == '"' -> {
                        result.append(char)
                        inString = !inString
                    }
                    !inString && (char == '{' || char == '[') -> {
                        result.append(char)
                        result.append('\n')
                        indentLevel++
                        result.append("  ".repeat(indentLevel))
                    }
                    !inString && (char == '}' || char == ']') -> {
                        result.append('\n')
                        indentLevel--
                        result.append("  ".repeat(indentLevel))
                        result.append(char)
                    }
                    !inString && char == ',' -> {
                        result.append(char)
                        result.append('\n')
                        result.append("  ".repeat(indentLevel))
                    }
                    !inString && char == ':' -> {
                        result.append(char)
                        result.append(' ')
                    }
                    char.isWhitespace() && !inString -> {
                        // Skip extra whitespace outside strings
                    }
                    else -> {
                        result.append(char)
                    }
                }
            }
            return result.toString()
        } catch (e: Exception) {
            return jsonString // Return original if formatting fails
        }
    }
}
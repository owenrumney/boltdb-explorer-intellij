# BoltDB Explorer for IntelliJ IDEA

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![JetBrains Plugin Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

A powerful IntelliJ IDEA plugin for browsing and managing BoltDB database files directly within your IDE.

## Features

- 🗂️ **Browse Database Structure**: Explore buckets and nested buckets in a tree view
- 🔍 **Search and Filter**: Find keys and values quickly with built-in search functionality
- 📝 **View and Edit**: Inspect key-value pairs with syntax highlighting and editing capabilities
- 🔧 **Database Management**: Create, delete, and modify buckets and key-value pairs
- 🎨 **IDE Integration**: Seamlessly integrated with IntelliJ IDEA's UI and workflow
- 📊 **Data Visualization**: Clear table view of database contents with pagination support

## Installation

### From JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Go to `File` → `Settings` → `Plugins`
3. Search for "BoltDB Explorer"
4. Click `Install` and restart your IDE

### Manual Installation

1. Download the latest release from the [GitHub releases page](https://github.com/owenrumney/boltdb-explorer-intellij/releases)
2. Open IntelliJ IDEA
3. Go to `File` → `Settings` → `Plugins`
4. Click the gear icon and select `Install Plugin from Disk...`
5. Select the downloaded `.jar` file
6. Restart your IDE

## Usage

### Opening BoltDB Files

1. **File Association**: BoltDB files (`.db`, `.bolt`, `.boltdb`) are automatically recognized
2. **Context Menu**: Right-click any database file and select "Open in BoltDB Explorer"
3. **File Editor**: Double-click a BoltDB file to open it in the integrated editor

### Navigating the Database

- **Tree View**: Browse buckets and nested structures in the left panel
- **Table View**: View key-value pairs in the main panel
- **Search**: Use the search bar to filter keys and values
- **Pagination**: Navigate through large datasets with built-in pagination

### Editing Data

- **Add Keys**: Right-click in the table to add new key-value pairs
- **Edit Values**: Double-click on values to edit them inline
- **Delete Items**: Select items and use the delete key or context menu
- **Bucket Management**: Create and delete buckets through the tree context menu

## Supported File Extensions

- `.db` - Standard BoltDB files
- `.bolt` - BoltDB files with .bolt extension
- `.boltdb` - BoltDB files with .boltdb extension

## Requirements

- IntelliJ IDEA 2025.1 or later
- Java 21 or later

## Development

### Building from Source

```bash
git clone https://github.com/owenrumney/boltdb-explorer-intellij.git
cd boltdb-explorer-intellij
./gradlew buildPlugin
```

### Running Tests

```bash
./gradlew test
```

### Development Environment

```bash
./gradlew runIde
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Architecture

The plugin consists of several key components:

- **BoltDBFileType**: Registers the file type with IntelliJ
- **BoltDBFileEditor**: Custom editor for BoltDB files
- **BoltDBExplorerPanel**: Main UI panel with tree and table views
- **BoltHelper**: Native binary wrapper for BoltDB operations
- **BoltDBClient**: Kotlin client for database operations

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with the [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- Uses [BoltDB](https://github.com/boltdb/bolt) for database operations
- Inspired by various database management tools

## Support

If you encounter any issues or have feature requests, please [open an issue](https://github.com/owenrumney/boltdb-explorer-intellij/issues) on GitHub.

---

Made with ❤️ by [Owen Rumney](https://github.com/owenrumney)




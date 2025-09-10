package co.uk.owenrumney.boltdb.explorer

import javax.swing.table.AbstractTableModel

class KeysTableModel : AbstractTableModel() {
    private val columns = arrayOf("Key", "Size", "Type")
    private val data = mutableListOf<Array<String>>()

    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(col: Int): String = columns[col]
    override fun getValueAt(row: Int, col: Int): Any = data[row][col]
    override fun getColumnClass(col: Int): Class<*> = String::class.java
    override fun isCellEditable(row: Int, col: Int): Boolean = false

    fun clear() {
        data.clear()
        fireTableDataChanged()
    }

    fun addRow(row: Array<String>) {
        data.add(row)
        fireTableRowsInserted(data.size - 1, data.size - 1)
    }

    fun setRows(rows: List<Array<String>>) {
        data.clear()
        data.addAll(rows)
        fireTableDataChanged()
    }

    fun logAllRows() {
        println("KeysTableModel: Logging all rows:")
        data.forEachIndexed { i, row ->
            println("Row $i: ${row.joinToString(", ")}")
        }
    }
}


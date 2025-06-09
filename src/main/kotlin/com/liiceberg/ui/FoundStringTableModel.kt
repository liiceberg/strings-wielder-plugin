package com.liiceberg.ui

import com.liiceberg.ui.entity.HardcodedStringEntity
import javax.swing.table.AbstractTableModel

class FoundStringTableModel(
    private val entries: List<HardcodedStringEntity>
) : AbstractTableModel() {

    private val colNames = arrayOf("Key", "Value", "Add to strings.xml")
    private val colClasses = arrayOf(String::class.java, String::class.java, Boolean::class.java)

    override fun getRowCount(): Int = entries.size

    override fun getColumnCount(): Int = colNames.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
        0 -> entries[rowIndex].key
        1 -> entries[rowIndex].value
        2 -> entries[rowIndex].isSelected
        else -> null
    }

    override fun getColumnName(columnIndex: Int): String = colNames[columnIndex]

    override fun getColumnClass(columnIndex: Int): Class<*> = colClasses[columnIndex]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        when (columnIndex) {
            0 -> entries[rowIndex].key = aValue as String
            1 -> entries[rowIndex].value = aValue as String
            2 -> entries[rowIndex].isSelected = aValue as Boolean
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }
}
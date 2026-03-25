package com.liiceberg.ui

import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.ui.entity.SuggestionType
import javax.swing.table.AbstractTableModel

class FoundStringTableModel(
    private val entries: List<HardcodedStringEntity>
) : AbstractTableModel() {

    private val colNames = arrayOf("Key", "Value", "Add to strings.xml", "Suggestions", "Action")
    private val colClasses =
        arrayOf(String::class.java, String::class.java, Boolean::class.java, String::class.java, String::class.java)

    override fun getRowCount(): Int = entries.size

    override fun getColumnCount(): Int = colNames.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
        0 -> entries[rowIndex].key
        1 -> entries[rowIndex].value
        2 -> entries[rowIndex].isSelected
        3 -> buildSuggestionText(entries[rowIndex])
        4 -> buildActionText(entries[rowIndex])
        else -> null
    }

    override fun getColumnName(columnIndex: Int): String = colNames[columnIndex]

    override fun getColumnClass(columnIndex: Int): Class<*> = colClasses[columnIndex]

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex != 3
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        when (columnIndex) {
            0 -> entries[rowIndex].key = aValue as String
            1 -> entries[rowIndex].value = aValue as String
            2 -> entries[rowIndex].isSelected = aValue as Boolean
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    private fun buildSuggestionText(e: HardcodedStringEntity): String {
        val list = mutableListOf<String>()
        if (SuggestionType.PLURAL in e.suggestions) list += "🔢 plural recommended"
        if (SuggestionType.TEMPLATE in e.suggestions) list += "🧩 template detected"
        if (SuggestionType.DUPLICATE in e.suggestions) list += "🔁 possible duplicate"
        return if (list.isEmpty()) "no suggestions" else list.joinToString(", ")
    }

    private fun buildActionText(e: HardcodedStringEntity): String {
        return when {
            SuggestionType.PLURAL in e.suggestions -> "Apply…"
            SuggestionType.DUPLICATE in e.suggestions -> "Review…"
            SuggestionType.TEMPLATE in e.suggestions -> "Apply"
            else -> "—"
        }
    }
}
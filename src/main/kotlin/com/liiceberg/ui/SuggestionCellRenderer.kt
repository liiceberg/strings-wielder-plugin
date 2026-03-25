package com.liiceberg.ui

import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class SuggestionCellRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        return JLabel(value as? String ?: "").apply {
            border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
            isOpaque = true
            background = if (isSelected) table.selectionBackground else table.background
        }
    }
}

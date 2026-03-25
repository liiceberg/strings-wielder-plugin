package com.liiceberg.ui

import java.awt.Component
import javax.swing.AbstractCellEditor
import javax.swing.JButton
import javax.swing.JTable
import javax.swing.table.TableCellEditor

class ActionButtonEditor(
    private val dialog: FoundStringDialog
) : AbstractCellEditor(), TableCellEditor {

    private val button = JButton()
    private var row = -1

    init {
        button.addActionListener {
            fireEditingStopped()
            dialog.onActionClicked(row)
        }
    }

    override fun getCellEditorValue(): Any = ""

    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        this.row = row
        button.text = value as? String ?: ""
        return button
    }
}

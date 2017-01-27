// -*- mode: java; indent-tabs-mode: nil; c-basic-offset: 4; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
 ** ISFS: NCAR Integrated Surface Flux System software
 **
 ** 2016, Copyright University Corporation for Atmospheric Research
 **
 ** This program is free software; you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation; either version 2 of the License, or
 ** (at your option) any later version.
 **
 ** This program is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ** GNU General Public License for more details.
 **
 ** The LICENSE.txt file accompanying this software contains
 ** a copy of the GNU General Public License. If it is not found,
 ** write to the Free Software Foundation, Inc.,
 ** 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **
 ********************************************************************
*/

package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Collection;

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.QObject;
import com.trolltech.qt.core.QModelIndex;
import com.trolltech.qt.core.QAbstractItemModel;
import com.trolltech.qt.gui.QAbstractTableModel;
import com.trolltech.qt.gui.QAbstractItemView;
import com.trolltech.qt.gui.QItemSelectionModel;
import com.trolltech.qt.gui.QItemSelection;
import com.trolltech.qt.gui.QSortFilterProxyModel;
import com.trolltech.qt.gui.QTableWidgetItem;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QDialog;

import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QGridLayout;
import com.trolltech.qt.gui.QFormLayout;
import com.trolltech.qt.gui.QGroupBox;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QRadioButton;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QPlainTextEdit;
import com.trolltech.qt.gui.QSizePolicy;

import edu.ucar.nidas.model.Var;


public class NewGaugePageDialog extends QDialog {


    /**
     * all the variables from input
     */
    private List<Var> _vars = new ArrayList<Var>(); 

    private VariableProxyModel _model;

    private VariableTableView _table;

    private QLineEdit _searchText;

    private Cockpit _cockpit = null;

    /**
     * The dialog that provides users with UI interface to 
     * select variables for a new page
     * @param owner
     */
    public NewGaugePageDialog(Cockpit cockpit)
    {
        super(cockpit);
        _cockpit = cockpit;
        setModal(true);

        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        createComp(cockpit);

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
        exec();
    }

    private void createComp(Cockpit cockpit)
    {
        setWindowTitle("Choose Variables to Plot");
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        // QHBoxLayout mainHlayout = new QHBoxLayout();
        QGridLayout mainHlayout = new QGridLayout(this);
        mainHlayout.setColumnStretch(0,0);
        mainHlayout.setColumnStretch(1,1);
        setLayout(mainHlayout);

        // create a table
        _table = new VariableTableView(this);
        _model = new VariableProxyModel(_table, cockpit.getVars());
        _model.setSortByHeight(false);

        _table.setModel(_model);
        _table.setSelectionBehavior(
                QAbstractItemView.SelectionBehavior.SelectRows);
        _table.setSelectionMode(
                QAbstractItemView.SelectionMode.ExtendedSelection);
        _table.hideColumn(1);

        // _table.setColumnWidth(0, 250);
        _table.sortByColumn(0,Qt.SortOrder.AscendingOrder);
        _table.resizeColumnToContents(0);

        // horizontal fix, vertical expanding
        _table.setSizePolicy(new QSizePolicy(QSizePolicy.Policy.Fixed,
            QSizePolicy.Policy.Expanding));

        // _table.setMinimumSize(200, 500);
        mainHlayout.addWidget(_table, 0, 0);

        QHBoxLayout hlayout;
        QVBoxLayout vlayout;
        QGridLayout glayout;

        QGroupBox selectBox = new QGroupBox(tr("Variables"), this);
        // selectBox.setFlat(false);
        vlayout = new QVBoxLayout();
        selectBox.setLayout(vlayout);

        QFormLayout form = new QFormLayout();

        // Sort by name or height
        hlayout = new QHBoxLayout();
        // QGridLayout glayout = new QGridLayout();
        // glayout.setColumnStretch(0,0);
        // glayout.setColumnStretch(1,0);

        QRadioButton radio = new QRadioButton("Name", selectBox);
        radio.setChecked(true);
        radio.clicked.connect(this, "sortByName()");
        hlayout.addWidget(radio);

        radio = new QRadioButton("Height", selectBox);
        radio.clicked.connect(this, "sortByHeight()");
        hlayout.addWidget(radio);

        form.addRow("Sort By:",hlayout);

        // Search
        glayout = new QGridLayout();
        glayout.setColumnStretch(0,1);
        glayout.setColumnStretch(1,0);

        _searchText = new QLineEdit(selectBox);
        _searchText.textChanged.connect(this, "setNameFilter()");
        glayout.addWidget(_searchText,0,0);

        QPushButton clearSearch = new QPushButton("X", selectBox);
        clearSearch.clicked.connect(_searchText, "clear()");
        glayout.addWidget(clearSearch,0,1);

        form.addRow("Contains:",glayout);

        // Select: All None
        hlayout = new QHBoxLayout();
        QPushButton all = new QPushButton("All", selectBox);
        all.clicked.connect(this, "selectAll()");
        hlayout.addWidget(all);

        QPushButton none = new QPushButton("None", selectBox);
        none.clicked.connect(this, "unselectAll()");
        hlayout.addWidget(none);

        form.addRow("Select:",hlayout);

        vlayout.addLayout(form);

        QPlainTextEdit help = new QPlainTextEdit(this);
        help.setPlainText("Click to select/deselect a variable.\n" +
            "Ctrl-click to select additional variables.\n" +
            "Shift-click to select a range of variables.\n");
        help.setReadOnly(true);

        vlayout.addWidget(help);

        //add ok /cancel
        hlayout = new QHBoxLayout();
        QPushButton pb = new QPushButton("Ok", this);
        pb.clicked.connect(this, "pressOK()");
        hlayout.addWidget(pb);
        pb = new QPushButton("Cancel", this);
        pb.clicked.connect(this, "pressCancel()");
        hlayout.addWidget(pb);

        vlayout.addLayout(hlayout);

        mainHlayout.addWidget(selectBox, 0, 1);

        setMinimumSize(300, 500);
        show();
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));

    }

    private void sortByName()
    {
        _model.setSortByHeight(false);
    }

    private void sortByHeight()
    {
        _model.setSortByHeight(true);
    }

    private void pressCancel()
    {
        _table.clear();
        close();
    }

    private void pressOK()
    {
        close();
    }

    List<Var> getSelectedVariables()
    {
        return _table.getSelectedVars();
    }

    String getName()
    {
        if (_table.getSelectedVars().isEmpty()) return "";
        return _table.getSelectedVars().get(0).getNameWithStn();
    }

    private void selectAll(boolean select)
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        QAbstractItemModel model = _table.model();
        QItemSelectionModel smodel = _table.selectionModel();

        QModelIndex top = model.index(0, 0, null);
        QModelIndex bot = model.index(model.rowCount()-1, 0, null);

        QItemSelection selection = new QItemSelection(top, bot);

        QItemSelectionModel.SelectionFlag flag = QItemSelectionModel.SelectionFlag.Deselect;
        if (select)
            flag = QItemSelectionModel.SelectionFlag.Select;

        smodel.select(selection, flag, QItemSelectionModel.SelectionFlag.Rows);

        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void selectAll()
    {
        selectAll(true);
    }

    private void unselectAll()
    {
        selectAll(false);
    }

    private void setNameFilter()
    {
        String str = _searchText.text().trim();
        _model.setVarNameFilter(str);
        // _modelByHeight.setVarNameFilter(str);
        // _table.model().reset();
    }

}

class VariableModel extends QAbstractTableModel
{
    VariableTableView _table;

    private List<Var> _vars = new ArrayList<Var>();

    private boolean _sortByHeight = false;

    public VariableModel(VariableTableView table, Collection<Var> vars)
    {
        super(table);
        _table = table;
        _vars.addAll(vars);
    }

    public boolean getSortByHeight()
    {
        return _sortByHeight;
    }

    public void setSortByHeight(boolean val)
    {
        _sortByHeight = val;
    }

    public List<Var> getVars()
    {
        return _vars;
    }

    public Var getVar(int row)
    {
        return _vars.get(row);
    }

    @Override
    public int rowCount(QModelIndex idx)
    {
        return _vars.size();
    }

    @Override
    public int columnCount(QModelIndex idx)
    {
        return 2;
    }

    @Override
    public Object data(QModelIndex idx, int role)
    {
        // if (!idx.isValid()) return null;

        // If you don't do this, checkboxes will appear in 
        // the cells
        if (role != Qt.ItemDataRole.DisplayRole) return null;

        int row = idx.row();
        int col = idx.column();

        Var var = _vars.get(row);
        /*
        System.err.printf("data(), row=%d, col=%d, var=%s\n",
                row, col, var.getNameWithStn());
        */

        if (col == 0) return var.getNameWithStn();
        else if (col == 1) return var;
        return null;
    }

    @Override
    public Object headerData(int section, Qt.Orientation orientation, int role)
    {

        if (role == Qt.ItemDataRole.DisplayRole) {
            if (orientation == Qt.Orientation.Horizontal) {
                /*
                System.err.println("headerData() " + orientation.toString());
                System.err.printf("headerData(), section=%d, role=%d\n", section, role);
                */
                if (section == 0) return "Variable";
                // else if (section == 1) return "";
            }
        }
        // return super.headerData(section, orientation, role);
        // return new Object();
        return null;
    }

    @Override
    public void sort(int column, Qt.SortOrder order)
    {

        // System.err.printf("VariableModel sort(), column=%d\n", column);
        if (column == 0) {

            if (_sortByHeight) {
                if (order == Qt.SortOrder.AscendingOrder)
                    Collections.sort(_vars, Var.HEIGHT_ORDER);
                else
                    Collections.sort(_vars, Var.HEIGHT_INVERSE_ORDER);
            }
            else {
                // System.err.printf("sort(), order=%d\n", order.value());
                if (order == Qt.SortOrder.AscendingOrder)
                    Collections.sort(_vars, Var.NAME_ORDER);
                else
                    Collections.sort(_vars, Var.NAME_INVERSE_ORDER);
            }
            reset();
            _table.resetSelection();
        }
    }
}

/**
 *
 */
class VariableProxyModel extends QSortFilterProxyModel
{
    private VariableModel _sourceModel = null;

    VariableTableView _table;

    private String _nameContains = null;

    private Qt.SortOrder _nameOrder = Qt.SortOrder.AscendingOrder;

    private Qt.SortOrder _heightOrder = Qt.SortOrder.DescendingOrder;

    public VariableProxyModel(VariableTableView table, Collection<Var> vars)
    {
        super(table);
        _table = table;
        _sourceModel = new VariableModel(table, vars);
        setSourceModel(_sourceModel);
        setDynamicSortFilter(true);
    }

    public boolean getSortByHeight()
    {
        return _sourceModel.getSortByHeight();
    }

    String orderToString(Qt.SortOrder order) 
    {
        return order == Qt.SortOrder.AscendingOrder ?
            "ascending" :
            (order == Qt.SortOrder.DescendingOrder ?
             "descending" : "unknown");
    }

    public void setSortByHeight(boolean val)
    {
        if (getSortByHeight() != val) {
            _sourceModel.setSortByHeight(val);

            if (getSortByHeight()) {
                // System.err.printf("sorting by _heightOrder=%s\n",orderToString(_heightOrder));
                _table.sortByColumn(0, _heightOrder);
            }
            else {
                // System.err.printf("sorting by _nameOrder=%s\n",orderToString(_heightOrder));
                _table.sortByColumn(0, _nameOrder);
            }
        }
    }

    public void setVarNameFilter(String val)
    {
        _nameContains = val;
        reset();
        _table.resetSelection();
    }

    @Override
    protected boolean filterAcceptsRow(int sourceRow, QModelIndex sourceParent)
    {
        // System.err.printf("filterAcceptsRow, sourceRow=%d\n", sourceRow);
        Var var = _sourceModel.getVar(sourceRow);

        // if (getSortByHeight() && Float.isNaN(var.getHeight())) return false;

        if (_nameContains == null) return true;
        return var.getNameWithStn().contains(_nameContains);
    }

    @Override
    public void sort(int column, Qt.SortOrder order)
    {
        /*
        System.err.printf("proxy sort(), column=%d, byheight=%b order=%s\n",
                column, getSortByHeight(), orderToString(order));
        */
        if (getSortByHeight())
            _heightOrder = order;
        else
            _nameOrder = order;
        _sourceModel.sort(column, order);
    }
}

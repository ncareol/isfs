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

import java.util.List;
import java.util.ArrayList;

import edu.ucar.nidas.model.Var;

import com.trolltech.qt.core.QAbstractItemModel;
import com.trolltech.qt.core.QModelIndex;
import com.trolltech.qt.gui.QTableView;
import com.trolltech.qt.gui.QItemSelection;
import com.trolltech.qt.gui.QItemSelectionModel;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.core.QModelIndex;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QMouseEvent;

public class VariableTableView extends QTableView
{
    // QItemSelectionModel _selection = new QItemSelectionModel();

    NewGaugePageDialog _dialog = null;

    private ArrayList<Var> _selectedVars = new ArrayList<Var>();

    public VariableTableView(NewGaugePageDialog parent)
    {
        super(parent);
        _dialog = parent;
        // setShowGrid(false);
        setSortingEnabled(true);
        // setCornerButtonEnabled(false);
        // sortByColumn(0,Qt.SortOrder.AscendingOrder);
    }

    public void select(Var var)
    {
        if (!_selectedVars.contains(var))
            _selectedVars.add(var);
    }

    public void deselect(Var var)
    {
        if (_selectedVars.contains(var))
            _selectedVars.remove(var);
    }

    public List<Var> getSelectedVars()
    {
        return _selectedVars;
    }

    public void resetSelection()
    {
        QItemSelectionModel smodel = selectionModel();
        QAbstractItemModel model = model();

        for (Var svar: getSelectedVars()) {

            QModelIndex start = model.index(0, 1, null);

            List<QModelIndex> idxs =
                model.match(start, Qt.ItemDataRole.DisplayRole, svar, 1,
                    Qt.MatchFlag.MatchExactly);
            for (QModelIndex idx: idxs) {
                /*
                System.err.printf("selecting row=%d, var=%s\n",
                        idx.row(), svar.getNameWithStn());
                */
                QItemSelection selection = new QItemSelection(idx, idx);
                smodel.select(selection,
                    QItemSelectionModel.SelectionFlag.Select,
                    QItemSelectionModel.SelectionFlag.Rows);
            }
        }
    }

    public void clear()
    {
        _selectedVars.clear();
    }

    @Override
    public void selectionChanged(QItemSelection selected,
        QItemSelection deselected)
    {
        super.selectionChanged(selected, deselected);
        if (model() != null) {

            List<QModelIndex> idxs = selected.indexes();
            for (QModelIndex idx : idxs) {
                if (idx.column() == 0) {
                    Var var = (Var)(idx.model().data(idx.row(), 1));
                    /*
                    System.err.printf("row %d, column %d, var=%s selected\n",
                        idx.row(),idx.column(), var.getNameWithStn());
                    */
                    select(var);
                }
            }

            idxs = deselected.indexes();
            for (QModelIndex idx : idxs) {
                if (idx.column() == 0) {
                    Var var = (Var)(idx.model().data(idx.row(), 1));
                    /*
                    System.err.printf("row %d, column %d, var=%s deselected\n",
                        idx.row(),idx.column(), var.getNameWithStn());
                    */
                    deselect(var);
                }
            }
        }
    }

    /**
     * By default, clicking again on an item did not deselect it.
     * This is a way to support deselect.
     */
    @Override
    public void mousePressEvent(QMouseEvent event)
    {
        QModelIndex item = indexAt(event.pos());
        boolean selected = selectionModel().isSelected(item);
        super.mousePressEvent(event);
        if (selected)
            selectionModel().select(item, QItemSelectionModel.SelectionFlag.Deselect,
                    QItemSelectionModel.SelectionFlag.Rows);
    }
}

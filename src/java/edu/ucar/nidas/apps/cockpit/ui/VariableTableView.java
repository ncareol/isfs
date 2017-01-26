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

    public VariableTableView(QWidget parent)
    {
        super(parent);
        // setShowGrid(false);
        setSortingEnabled(true);
        // setCornerButtonEnabled(false);
        // sortByColumn(0,Qt.SortOrder.AscendingOrder);
    }

    @Override
    public void selectionChanged(QItemSelection selected,
        QItemSelection deselected)
    {
        super.selectionChanged(selected, deselected);
        if (model() != null) {
            List<QModelIndex> idxs = selected.indexes();
            /*
            for (QModelIndex m : idxs) {
                System.err.printf("row %d, column %d selected\n",
                    m.row(),m.column());
            }
            */
            idxs = deselected.indexes();
            /*
            for (QModelIndex m : idxs) {
                System.err.printf("row %d, column %d deselected\n",
                    m.row(),m.column());
            }
            */
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

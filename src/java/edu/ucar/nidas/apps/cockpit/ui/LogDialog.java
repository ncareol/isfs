// -*- mode: java; indent-tabs-mode: nil; tab-width: 4; -*-
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

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QDialogButtonBox;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QPlainTextEdit;
import com.trolltech.qt.gui.QPushButton;

/**
 * A log window for cockpit.
 */
public class LogDialog extends QWidget
{
    private QPlainTextEdit _text;

    /**
     */
    public LogDialog(QWidget owner)
    {
        super(owner, Qt.WindowType.Window);

        setWindowTitle("Cockpit Log");
        QVBoxLayout layout = new QVBoxLayout();

        _text = new QPlainTextEdit();
        _text.setReadOnly(true);
        layout.addWidget(_text);

        QPushButton clear = new QPushButton("&Clear");
        clear.clicked.connect(this, "clear()");

        QPushButton dismiss = new QPushButton("&Dismiss");
        dismiss.clicked.connect(this, "dismiss()");

        QDialogButtonBox buttons =
            new QDialogButtonBox(Qt.Orientation.Horizontal);
        buttons.addButton(clear, QDialogButtonBox.ButtonRole.ActionRole);
        buttons.addButton(dismiss, QDialogButtonBox.ButtonRole.AcceptRole);
        layout.addWidget(buttons);

        setLayout(layout);
        setGeometry(0, 0, 400, 300);
        hide();
    }

    public QPlainTextEdit getTextWidget()
    {
        return _text;
    }

    public void clear()
    {
        _text.clear();
    }

    public void dismiss()
    {
        hide();
    }
}

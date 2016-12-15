package edu.ucar.nidas.apps.cockpit.ui;

/*

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.Qt.Alignment;
import com.trolltech.qt.gui.QCheckBox;
import com.trolltech.qt.gui.QComboBox;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QFrame;
import com.trolltech.qt.gui.QGroupBox;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QRadioButton;

*/

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QDialogButtonBox;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QPlainTextEdit;
import com.trolltech.qt.gui.QPushButton;


/**
 * A log window for cockpit.
 */
public class LogDialog extends QDialog
{
    private QPlainTextEdit _text;

    /**
     */
    public LogDialog(QWidget owner)
    {
        super(owner);
        setModal(false);

        //create the UI-components

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
        // move(-width(), -height());
        setVisible(false);
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
        setVisible(false);
    }
}

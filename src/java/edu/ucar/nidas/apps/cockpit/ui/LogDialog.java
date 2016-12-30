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

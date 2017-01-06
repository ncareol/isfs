package edu.ucar.nidas.apps.cockpit.ui;

import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QVBoxLayout;

/**
 * Provide users with a UI dialog to manually enter the max-min. 
 *
 */
public class RescaleDialog extends QDialog {

    QLineEdit 	_tfymax; 
    QLineEdit 	_tfymin;
    boolean 	_ok=false;

    RescaleDialog(float max, float min, int xmouse, int ymouse)
    {
        // super(null, true);
        super.setModal(true);
        setModal(true);

        setWindowTitle( " Change Y scale");
        QVBoxLayout mlout = new QVBoxLayout();

        QHBoxLayout hlayout;
        /*
        //add empty space at top
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel(""));
        mlout.addLayout(hlayout);
        */

        //add row-1 min
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel("Ymin= "));
        _tfymin = new QLineEdit(String.valueOf(min)); 
        // _tfymin.setMaximumSize(100, 28);
        // _tfymin.adjustSize();
        hlayout.addWidget(_tfymin);
        // hlayout.addStretch();
        mlout.addLayout(hlayout);

        //add row-2 max 
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel("Ymax= "));
        _tfymax = new QLineEdit(String.valueOf(max)); 
        // _tfymax.setMaximumSize(100, 28);
        // _tfymax.adjustSize();
        hlayout.addWidget(_tfymax);
        // hlayout.addStretch();
        mlout.addLayout(hlayout);

        /*
        //add empty row-3
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel(""));
        mlout.addLayout(hlayout);
        */

        //add row-4 ok-cancel
        hlayout = new QHBoxLayout();
        // hlayout.addWidget(new QLabel());// .addItem((QLayoutItemInterface)new QLabel());
        QPushButton okButton = new QPushButton("Ok", this);
        okButton.clicked.connect(this, "pressOk()");

        QPushButton cancelButton = new QPushButton("Cancel", this);
        cancelButton.clicked.connect(this, "pressCancel()");
        hlayout.addWidget(okButton);
        hlayout.addWidget(cancelButton);

        mlout.addLayout(hlayout);
        setGeometry(xmouse, ymouse, 100, 50 );

        setLayout(mlout);
        setVisible(true);
        exec();
    }

    void pressOk()
    {
        _ok= true;
        close();
    };
    void pressCancel() {
        _ok= false;
        close();
    }

    float getMax(){
        return Float.valueOf(_tfymax.text().trim());
    }
    float getMin() {
        return Float.valueOf(_tfymin.text().trim());
    }

    boolean getOk() {return _ok;}

}

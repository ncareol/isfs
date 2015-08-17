package edu.ucar.nidas.apps.cockpit.ui;

import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QTextEdit;
import com.trolltech.qt.gui.QVBoxLayout;

/**
 * Provide users with a UI dialog to manually enter the max-min. 
 * @author dongl
 *
 */
public class RescaleDialog extends QDialog {

    QTextEdit 	_tfymax; 
    QTextEdit 	_tfymin;
    boolean 	_ok=false;

    RescaleDialog( float max, float min,  int xmouse, int ymouse)  {
        // super(null, true);
        super.setModal(true);
        setModal(true);

        setWindowTitle( " Change the scales ");
        QVBoxLayout mlout=  new QVBoxLayout();

        //add empty space at top
        QVBoxLayout layout = new QVBoxLayout();
        layout.addWidget(new QLabel(""));
        mlout.addLayout(layout);

        //add row-1  max 
        QHBoxLayout hlayout = new QHBoxLayout();
        hlayout.addWidget( new QLabel(""));
        hlayout.addWidget(new QLabel("Ymax= "));
        _tfymax= new QTextEdit(String.valueOf(max)); 
        _tfymax.setMinimumSize(200, 28);
        _tfymax.adjustSize();
        hlayout.addWidget( _tfymax);
        hlayout.addWidget( new QLabel(""));
        mlout.addLayout(hlayout);

        //add row-2 min
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel(""));
        hlayout.addWidget( new QLabel(" Ymin= "));
        _tfymin= new QTextEdit(String.valueOf(min)); 
        _tfymin.setMinimumSize(200, 28);
        hlayout.addWidget( _tfymin);
        hlayout.addWidget( new QLabel(""));
        mlout.addLayout(hlayout);

        //add row-3 label
        layout = new QVBoxLayout();
        layout.addWidget(new QLabel(""));
        mlout.addLayout(layout);

        //add row-4 ok-cancle
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel());// .addItem((QLayoutItemInterface)new QLabel());
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


    void pressOk() {
        _ok= true;
        close();
    };
    void pressCancel() {
        _ok= false;
        close();
    }

    float getMax(){
        return Float.valueOf(_tfymax.toPlainText().trim());
    }
    float getMin() {
        return Float.valueOf(_tfymin.toPlainText().trim());
    }

    boolean getOk() {return _ok;}

}//dbg-diag	

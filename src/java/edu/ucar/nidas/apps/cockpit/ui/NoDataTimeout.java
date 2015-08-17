package edu.ucar.nidas.apps.cockpit.ui;

import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QTabWidget;
import com.trolltech.qt.gui.QTextEdit;
import com.trolltech.qt.gui.QVBoxLayout;

import edu.ucar.nidas.util.Util;

public class NoDataTimeout extends QDialog{
    int _tmSec;//5 minutes default
    QTextEdit _teTm;
    
    public NoDataTimeout(QTabWidget owner,  int oldTm) {
        setModal(true);
        _tmSec=oldTm;
      
        //create the UI-components
        createComp();
    }
    
    public int getNewTimeSec() {
        return _tmSec;
    }
    
    private  void  createComp() {
        setWindowTitle("   Enter a New No-Data Timeout  ");
        QVBoxLayout mlayout = new QVBoxLayout();

        //new time
        QHBoxLayout hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel("New Time(Seconds):"));
        _teTm = new QTextEdit(""+_tmSec);
        _teTm.setMaximumSize(100, 30);
        
       //_teTm.
        hlayout.addWidget(_teTm);
        mlayout.addLayout(hlayout);
        
        //row-last   --ok-cancel buttons
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel());
        QPushButton bok= new QPushButton("Ok", this);
        bok.clicked.connect(this, "pressOk()");
        hlayout.addWidget(bok);
        QPushButton bcancel= new QPushButton("Cancel", this);
        bcancel.clicked.connect(this, "pressCancel()");
        hlayout.addWidget(bcancel);
        hlayout.addWidget(new QLabel());
        mlayout.addItem(hlayout);

        setLayout(mlayout);
        setGeometry(400, 300, 300, 100);
        setVisible(true);
        exec();
    }

    
    private void pressOk(){
        String str= _teTm.toPlainText().trim();
        if (str==null) {
            Util.prtErr("Null string "+this.getClass().getName());
            return;
        }
        int tm= Integer.parseInt(str);
        if (tm < 0 ) {
            Util.prtErr("The time should be bigger than 0...");
            _teTm.setText(""+(_tmSec));
            _teTm.setFocus();
            return;
        }
        _tmSec=tm;
        close();
    }
    
    private void pressCancel() {
        _tmSec=-1;
        close();
    }
    
    
    
}

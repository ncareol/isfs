package edu.ucar.nidas.apps.cockpit.ui;

import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QTabWidget;
import com.trolltech.qt.gui.QTextEdit;
import com.trolltech.qt.gui.QVBoxLayout;

import edu.ucar.nidas.util.Util;

public class NewTimeMSec extends QDialog{
  
    int _tmMSec;
    QTextEdit _teTm;
    
    public NewTimeMSec(QTabWidget owner,  int oldTmMSec) {
        setModal(true);
        _tmMSec=oldTmMSec;
      
        //create the UI-components
        createComp();
    }
    
    public int getNewTimeMSec() {
        return _tmMSec;
    }
    private  void  createComp() {

        setWindowTitle("   Enter a New Time Range   ");
        QVBoxLayout mlayout = new QVBoxLayout();

        //new time
        QHBoxLayout hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel("New Time Range (Seconds):"));
        _teTm = new QTextEdit(""+_tmMSec/1000);
        _teTm.setMaximumSize(100, 30);
        
       //_teTm.
        hlayout.addWidget(_teTm);
        //hlayout.addStretch();
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
        if (tm % 60 != 0 ) {
            Util.prtErr("The time-span has to be in minutes\n For example: 60, 120, 180...");
            _teTm.setText(""+(_tmMSec/1000));
            _teTm.setFocus();
            return;
        }
        _tmMSec=tm*1000;
   
        close();
    }
    
    private void pressCancel() {
        _tmMSec=-1;
        close();
    }
    
    
}

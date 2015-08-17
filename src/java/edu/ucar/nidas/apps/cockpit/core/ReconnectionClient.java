package edu.ucar.nidas.apps.cockpit.core;

import com.trolltech.qt.gui.QApplication;

import edu.ucar.nidas.apps.cockpit.ui.CockPit;

public class ReconnectionClient implements Runnable {

    /**
     * tcp-socket to get data-descriptor, and kept it alive in the program
     */
    CockPit     _cp=null;
    boolean     _running =false;

    public ReconnectionClient ( CockPit cp){
        _cp=cp;
    }
    
    
    public void setReconnect() {
        if (_running) return;
        QApplication.invokeLater(this);
    }

    /**
     * This implements a Runable to keep the thread alive
     */
    public void run() 
    {
        synchronized (this) {
            if (_cp==null) return;
            if (_running) return;
            _running =true;
            _cp.reconnect(); 
            _cp._statusbar.showMessage("Reconnection is done. ", 20000);
            _running =false;
        }
    }


} //eol-class


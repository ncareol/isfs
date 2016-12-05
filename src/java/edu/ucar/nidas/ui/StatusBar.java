package edu.ucar.nidas.ui;

import edu.ucar.nidas.model.StatusDisplay;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QStatusBar;

/**
 * This class acts as a status's client to receive messages,
 * and implements a QT thread to invoke the cockpit-UI for display  
 * @author dongl
 *
 * Not sure why a separate thread is needed... GDM
 *
 */
public class StatusBar implements StatusDisplay, Runnable
{
    private QStatusBar _sb = null; 
    String _str;
    int    _ms;
   
    public StatusBar(QStatusBar sb)
    {
        _sb=sb;
    }

    /**
     * receive a new debug_message
     */
    public void show(String str, int ms)
    {
        synchronized(this)
        {
            _str = str;
            _ms = ms;
        }
        QApplication.invokeLater(this);
    }

    /**
     * receive a new debug_message
     */
    public void show(String str)
    {
        synchronized(this) {
            _str = str;
            _ms = -1;
        }
        QApplication.invokeLater(this);
    }

    /**
     * This Qa update;
     */
    public void run() 
    {
        synchronized(this) {
            if (_str==null) return;
            if (_ms > 0) _sb.showMessage(_str,_ms);
            else _sb.showMessage(_str);
            _str=null;
        }
    }

}

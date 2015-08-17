package edu.ucar.nidas.ui;




import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QStatusBar;



/**
 * This class acts as a status's client to receive messages,
 * and implements a QT thread to invoke the cockpit-UI for display  
 * @author dongl
 *
 */
public class StatusBarClient  implements Runnable
{
    private QStatusBar _sb=null; 
    String _str;
    int    _ms;
   
    public StatusBarClient( QStatusBar sb) {
        _sb=sb;
    }

    /**
     * receive a new debug_message
     */
    public void receive(String str, int ms)
    {
        synchronized(this) {
            _str=str;
            _ms=ms;
        }
        QApplication.invokeLater(this);
    }

    /**
     * This Qa update;
     */
    public void run() 
    {
        if (_str==null) return;
        synchronized(this) {
            if (_ms>0) _sb.showMessage(_str,_ms);
            else _sb.showMessage(_str);
            _str=null;
        }
    }

}

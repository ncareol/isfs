package edu.ucar.nidas.ui;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QTextEdit;



/**
 * This class acts as a debug-text's client to receive debug messages,
 * and implements a QT thread to invoke the cockpit-UI for display  
 * @author dongl
 *
 */
public class TextClient implements Runnable
{
    private QTextEdit _text=null; 
    private String _str=null;

    public TextClient( QTextEdit t ) {
        _text=t;
    }

    /**
     * receive a new debug_message
     */
    public void receive(String str)
    {
        synchronized (this) {
            _str+="\n"+str;
            QApplication.invokeLater(this);
        }
     }

    /**
     * This Qa update;
     */
    public void run() 
    {
        if (_str==null) return;
        synchronized(this) {
            _text.append(_str);
            _str=null;
        }
    }

}

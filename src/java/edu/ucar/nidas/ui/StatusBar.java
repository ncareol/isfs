package edu.ucar.nidas.ui;

import edu.ucar.nidas.model.StatusDisplay;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QMainWindow;
import com.trolltech.qt.gui.QStatusBar;

import java.util.ArrayList;

/**
 * This class acts as a status's client to receive messages,
 * and implements a QT thread to invoke the cockpit-UI for display  
 * @author dongl
 *
/**
 * Class supporting writes to the QMainWindow QStatusBar
 * from non-GUI threads.
 */
public class StatusBar implements StatusDisplay {

    private class StatusCache
    {
        String msg;
        int msec;
        public StatusCache(String m, int ms)
        {
            msg = m;
            msec = ms;
        }
    }

    private ArrayList<StatusCache> _messageCache =
        new ArrayList<StatusCache>();

    private QStatusBar _statusbar;

    private class StatusRunnable implements Runnable
    {
        @Override
        public void run()
        {
            ArrayList<StatusCache> messageCache;
            synchronized(_messageCache) {
                messageCache =
                    new ArrayList<StatusCache>(_messageCache);
                _messageCache.clear();
            }
            for (StatusCache msg : messageCache) {
                _statusbar.showMessage(msg.msg, msg.msec);
            }
        }
    }

    public StatusBar(QMainWindow main) 
    {
        _statusbar = main.statusBar();
    }

    @Override
    public void show(String msg, int msec)
    {
        synchronized(_messageCache) {
            _messageCache.add(new StatusCache(msg,msec));
        }
        QApplication.invokeLater(new StatusRunnable());
    }

    @Override
    public void show(String msg)
    {
        synchronized(_messageCache) {
            _messageCache.add(new StatusCache(msg,0));
        }
        QApplication.invokeLater(new StatusRunnable());
    }

    public void close()
    {
        synchronized(_messageCache) {
            _messageCache.clear();
        }
        _statusbar.close();
    }
}


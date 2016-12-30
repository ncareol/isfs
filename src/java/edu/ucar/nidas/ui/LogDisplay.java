package edu.ucar.nidas.ui;

import edu.ucar.nidas.model.Log;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QPlainTextEdit;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * This class provides access for writing to a QTextEdit widget
 * from threads other than the Qt GUI thread.
 */
public class LogDisplay implements Log
{
    private QPlainTextEdit _text = null;

    private static DateFormat _dateFormat =
        new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private ArrayList<String> _queue = new ArrayList<String>();

    public LogDisplay(QPlainTextEdit text)
    {
        _text = text;
    }

    /**
     * display debug message
     */
    @Override
    public void debug(String msg)
    {
        log("debug", msg);
    }

    /**
     * display info message
     */
    @Override
    public void info(String msg)
    {
        log("info", msg);
    }

    /**
     * display error message
     */
    @Override
    public void error(String msg)
    {
        log("ERROR", msg);
    }

    /**
     * clear
     */
    @Override
    public void clear()
    {
        synchronized (_queue) {
            _queue.add("CLEAR");
            QApplication.invokeLater(new LogRunnable());
            /* If a modal window is active, do this to
             * see the log messages.
             */
            QApplication.processEvents();
        }
    }

    /**
     * log a message
     */
    public void log(String type, String msg)
    {
        synchronized (_queue) {
            String logmsg = _dateFormat.format(new Date()) +
                ": " + type + ": " + msg;
            _queue.add(logmsg);
            QApplication.invokeLater(new LogRunnable());
        }
    }

    private class LogRunnable implements Runnable
    {
        @Override
        public void run() 
        {
            synchronized(_queue) {
                for(String msg : _queue) {
                    if (msg == "CLEAR") _text.clear();
                    else {
                        _text.appendPlainText(msg);
                        _text.verticalScrollBar().setValue(
                            _text.verticalScrollBar().maximum());
                    }
                }
                _queue.clear();
                /* If a modal window is active, do this to
                 * see the log messages.  */
                QApplication.processEvents();
            }
        }
    }
}

// -*- mode: java; indent-tabs-mode: nil; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
 ** ISFS: NCAR Integrated Surface Flux System software
 **
 ** 2016, Copyright University Corporation for Atmospheric Research
 **
 ** This program is free software; you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation; either version 2 of the License, or
 ** (at your option) any later version.
 **
 ** This program is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ** GNU General Public License for more details.
 **
 ** The LICENSE.txt file accompanying this software contains
 ** a copy of the GNU General Public License. If it is not found,
 ** write to the Free Software Foundation, Inc.,
 ** 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **
 ********************************************************************
*/

package edu.ucar.nidas.ui;

import edu.ucar.nidas.model.StatusDisplay;

import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QMainWindow;
import com.trolltech.qt.gui.QStatusBar;

import java.util.ArrayList;

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


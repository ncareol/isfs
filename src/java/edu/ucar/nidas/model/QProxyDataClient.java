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

package edu.ucar.nidas.model;

import edu.ucar.nidas.model.DataClient;
import edu.ucar.nidas.model.FloatSample;
import edu.ucar.nidas.model.Sample;

import java.util.ArrayList;
import java.util.HashMap;

import com.trolltech.qt.gui.QApplication;

/**
 * Proxy for DataClients that are QWidgets.
 *
 * In QtJambi, there is a restriction on QWidgets, in that
 * they can only be used from the main Qt GUI thread.
 *
 * If you use a QWidget in a thread that is not the main GUI thread
 * you will see a runtime exception like the following:
 * "Exception in thread "Thread-2" QObject used from outside its own thread..."
 * The solution is to use a QProxyDataClient.
 *
 * The static method
 * QProxyDataClient.getProxy(DataClient) creates a proxy
 * DataClient. Calling the receive(FloatSample, offset) method
 * on the proxy client will result in the receive() method of
 * the original client to be invoked at some point later
 * in the main QApplication GUI thread for the FloatSample and offset.
 *
 * The receive method of the DataClient should be as light weight
 * as possible, to avoid adding too much load to the GUI thread.
 * Typically all heavy number crunching has been done by upstream
 * DataClients, and only the resultant visuals are changed by the
 * receive method.
 */
public class QProxyDataClient implements DataClient
{
    /**
     * Whatever needs to be cached for the receive method.
     */
    private static class ProxyCache
    {
        public DataClient client;
        public FloatSample sample;
        int offset;
        public ProxyCache(DataClient c, FloatSample s, int o)
        {
            client = c;
            sample = s;
            offset = o;
        }
    }

    /**
     * Queue of DataClients whose receive methods need to be called
     * in the Qt GUI thread.
     */
    private static final ArrayList<ProxyCache> _receiveQueue =
        new ArrayList<ProxyCache>();

    /**
     * Actual DataClient for this proxy.
     */
    private DataClient _client;

    private QProxyDataClient(DataClient client)
    {
        _client = client;
    }

    /**
     * Static method that creates and returns a proxy DataClient,
     * whose receive method can be invoked from a non-GUI thread.
     */
    public static DataClient getProxy(DataClient client)
    {
        return new QProxyDataClient(client);
    }

    /**
     * Call receive methods of all cached DataClients and their samples.
     */
    private static class ProxyRunnable implements Runnable
    {
        @Override
        public void run()
        {
            ArrayList<ProxyCache> queue;
            synchronized(_receiveQueue) {
                // if (_receiveQueue.isEmpty()) return;
                // shallow copy
                queue = new ArrayList<ProxyCache>(_receiveQueue);
                _receiveQueue.clear();
            }
            for (ProxyCache cache : queue) {
                cache.client.receive(cache.sample,cache.offset);
            }
        }
    }

    /**
     * Queue the sample to be passed to the associated DataClient
     * receive method in the Qt GUI thread.
     */
    @Override
    public void receive(FloatSample sample, int offset)
    {
        ProxyCache cache = new ProxyCache(_client, sample, offset);
        synchronized(_receiveQueue) {
            _receiveQueue.add(cache);
            if (_receiveQueue.size() == 1)
                QApplication.invokeLater(new ProxyRunnable());
        }
    }
}

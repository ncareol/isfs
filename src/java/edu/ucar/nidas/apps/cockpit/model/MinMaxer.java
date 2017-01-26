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

package edu.ucar.nidas.apps.cockpit.model;

import java.util.HashSet;

import edu.ucar.nidas.model.FloatSample;
import edu.ucar.nidas.model.DataProcessor;
import edu.ucar.nidas.model.DataClient;

/**
 * Compute a variable's minimum and maximum value over a time period,
 * passing the result on to DataClients.
 */
public class MinMaxer implements DataProcessor {

    /**
     * @param interval  -- millisecond time interval to calculate minimums and maximums
     */
    public MinMaxer(int interval)
    {
        _interval = interval;
        _data = new float[2];
        _data[0] = Float.MAX_VALUE;
        _data[1] = -Float.MAX_VALUE;
    }


    /**
     * The end time of current data period
     */
    private long _endTime = Long.MIN_VALUE;

    /**
     * The time length, in milliseconds, of the statistics calculation
     */
    private int _interval;

    /**
     *  min and max values
     */
    private float[] _data = null;

    /**
     * DataClients of this processor.  
     * Use a HashSet to avoid duplicate clients.
     */
    private HashSet<DataClient> _clients = new HashSet<DataClient>();

    /**
     * caller passes a time-tag in microsecond, and a float data point
     * For one second data, record max and min in the list array
     * If the data's ttag exist the max-time of this object, distribute
     * the data to plots, and renew this object
     *
     * @param samp  -- the data sample
     * @param offset	-- index into the sample of the first data value to receive
     */
    @Override
    public void receive(FloatSample samp,int offset)
    {
        long ttag= samp.getTimeTag();
        //System.err.printf("samp-id= %x \n", samp.getId());

        if (ttag >= _endTime) {
            if (_endTime > Long.MIN_VALUE) {
                if (_data[0] != Float.MAX_VALUE) {
                    FloatSample osamp = new FloatSample(
                        _endTime - _interval / 2, samp.getId(), _data);
                    distribute(osamp);
                }
            }
            _endTime = ttag + _interval - ( ttag % _interval);
            _data[0] = Float.MAX_VALUE;
            _data[1] = -Float.MAX_VALUE;
        }

        float f = samp.getData(offset);
        //	System.err.printf("samp-id= %x  f=%f \n", samp.getId(), f);
        if (Float.isNaN(f)) return;

        if (f < _data[0]) _data[0] = f;
        if (f > _data[1]) _data[1] = f;
    }

    /**
     * Add a plot-client
     */
    @Override
    public void addClient(DataClient clnt) 
    {
        if (clnt == null) return;
        synchronized (_clients)
        {
            _clients.add(clnt);
        }
    }

    /**
     * Remove a plot client
     */
    @Override
    public void removeClient(DataClient clnt) 
    {
        if (clnt == null) return;
        synchronized (_clients)
        {
            _clients.remove(clnt);
        }
    }

    private void distribute(FloatSample samp)
    {
        HashSet<DataClient> tmpcopy;
        synchronized (_clients)
        {
            tmpcopy = new HashSet<DataClient>(_clients);
        }

        for (DataClient clnt: tmpcopy) {
            clnt.receive(samp,0); //the out-sample only contains the min and max
        }
    }
}


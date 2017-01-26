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

import java.util.ArrayList;

/**
 * FloatSample class stores the sample-id, time-tag, and sample-data
 */
public class FloatSample  {

    public FloatSample(long tt, int id,float[] data)
    {
        _tt = tt;
        _id = id;
        _data=data.clone();
    }
    
    /**
     * Time tag, non-leap microseconds since 1970 Jan 00:00 UTC.
     */
    long _tt;  		//time-tag

    /**
     * NIDAS sample id.
     */
    int _id;

    float [] _data;

    /**
     * get ONE sample-data at the specific position
     * 
     * @param i
     * @return
     */
    public float getData(int i) {
        if (i < 0 || i >= _data.length) 
            return Float.NaN;
        return _data[i];
    }

    /**
     * get all the data of the sample
     * 
     * @return
     */
    final ArrayList<Float> getData() {
        if ( _data.length < 1 ) 
            return null;
        ArrayList<Float> f = new ArrayList<Float>(_data.length);
        for (int i = 0; i < _data.length; i++) f.add(_data[i]);
        return f;
    }

    /**
     * get the length of the sample-data
     * 
     * @return
     */
    public int getLength()
    {
        return _data.length;
    }

    /**
     * get time-tag of the sample
     * 
     * @return
     */
    public long getTimeTag() {
        return _tt;
    }

    /**
     * get the NIDAS sample id.
     * @return
     */
    public int getId() {
        return _id;
    }

    /**
     * get the DSM id portion of the NIDAS sample id.
     * @return
     */
    public int getDsmId()
    {
        return  (_id >> 16) & 0x3FF;
    }

    /**
     * get the sensor + sample id portion of the NIDAS sample id.
     * @return
     */
    public int getSSid()
    {
        return _id & 0xFFFF;
    }

    /**
     * get the NIDAS sample id without the type (bit31-26=0)
     * @return
     */
    public int getDSSid()
    {
        return _id & 0x03FFFFFF;
    }
}

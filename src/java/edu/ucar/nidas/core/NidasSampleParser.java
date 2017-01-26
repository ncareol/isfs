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

package edu.ucar.nidas.core;

import edu.ucar.nidas.model.FloatSample;

import java.nio.ByteBuffer;

/**
 * This class parses a FloatSample from a ByteBuffer.
 *
 */
public class NidasSampleParser  {

    final static int 	headLen = 16;  // nidas-head in bytes

    final static int floatSize =  Float.SIZE / 8;

    final static int doubleSize =  Double.SIZE / 8;

    private float[] _fdata;

    /**
     * This method gets 
     *     a time-tag,
     *     sample-id
     *     data
     * for one sample 
     *  
     * @param data
     * @param length
     * @return
     */
    public FloatSample parseFloatSample(ByteBuffer data, int length)
    {

        if ((length - data.position()) < headLen) return null;

        /** nidas head _data format :
         * 	8 bytes of time tag
         *  4 bytes of _data _len
         *  4 bytes of id
         */
        long tt = data.getLong() / 1000;    // convert microseconds to milliseconds
        int dataBytes = data.getInt();
        int id = data.getInt() & 0x3FFFFFF;

        if (( length - data.position()) < dataBytes) return null;

        int dlen = dataBytes / floatSize;  //number of floats

        _fdata = new float[dlen];

        for (int i = 0; i < dlen; i++)
                _fdata[i] = data.getFloat();

        return new FloatSample(tt,id,_fdata);
    }

    public FloatSample parseSample(ByteBuffer data, int length)
    {

        if ((length - data.position()) < headLen) return null;

        /** nidas head _data format :
         *  8 bytes of time tag
         *  4 bytes of _data _len
         *  4 bytes of id
         */
        long tt = data.getLong() / 1000;    // convert microseconds to milliseconds
        int dataBytes = data.getInt();
        int rawid = data.getInt();
        int id = rawid & 0x3FFFFFF;
        int sampleType = rawid >> 26;

        if (( length - data.position()) < dataBytes) return null;

        /** identify nidas sample-type:  float=6 or double=7 (enum)
         * typedef enum sampleType {
         * CHAR_ST, UCHAR_ST, SHORT_ST, USHORT_ST,
         * INT32_ST, UINT32_ST, FLOAT_ST, DOUBLE_ST,
         * INT64_ST, UNKNOWN_ST } sampleType;
         */
        int rawSize = floatSize;
        if (sampleType == 7) {
            rawSize = doubleSize;
        }
        int dlen = dataBytes / rawSize;  //number of floats or doubles
        _fdata = new float[dlen];

        for (int i = 0; i < dlen; i++){
            if (sampleType == 7) {
                _fdata[i] = (float)data.getDouble();
            } else {
                _fdata[i] = data.getFloat();
            }
        }
        return new FloatSample(tt,id,_fdata);
    }
}

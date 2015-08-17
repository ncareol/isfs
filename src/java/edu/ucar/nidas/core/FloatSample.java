package edu.ucar.nidas.core;

import java.util.ArrayList;

/**
 * FloatSample class stores the sample-id, time-tag, and sample-data
 * 
 * @author dongl
 *
 */
public class FloatSample  {

	public FloatSample(long tt, int id,float[] data)
	{
		_tt = tt;
		_id = id;
		_data=data.clone();
	}
	// sample _data
	long 	_tt;  		//time-tag
	int 	_id;			//sample-complix_id  bit31-26--0, 25-16=DsmId 15-0=sensor+sampleIds  

	// float _data
	float [] _data;

	/**
	 * get ONE sample-data at the specific position
	 * 
	 * @param i
	 * @return
	 */
	public float getData (int i) {
	    if (i < 0 || i >= _data.length) 
	        return Float.NaN;
	    return _data[i];
	}

	/**
	 * get all the data of the sample
	 * 
	 * @return
	 */
	final ArrayList<Float> getData () {
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
	public int getLength () {
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
	 * get the nidas-complex-sample-id
	 * @return
	 */
	public int getId () {
		return _id;
	}

	/**
	 * get the DSM-id
	 * @return
	 */
	public int getDsmId () {
		return  _id>>16;  // right (16)to get dsmid
	}

	/**
	 * get the sensor+sampleIds (2-bytes 0-15bits)
	 * @return
	 */
	public int getSSid() {
		return _id & (short)0xFF;  //sensor+sampleId

	}

	/**
	 * get the nidas-complex-sample-id without type-id (bit31-26=0)
	 * @return
	 */
	public int getDSSid() {
		return _id ; // id without typeid
	}

}

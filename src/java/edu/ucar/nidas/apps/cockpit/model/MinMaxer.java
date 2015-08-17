package edu.ucar.nidas.apps.cockpit.model;


import java.util.ArrayList;

import edu.ucar.nidas.core.FloatSample;

/**
 * This class stores a variable's stat-data (min&max)
 * It services two main purposes:
 *   1.record the min-max of a variable in the given-time-interval (usually 1 second)
 *   2.distribute the variable's min-max to their plots, when the new data time is out of the range
 *   
 * @author dongl
 *
 */
public class MinMaxer implements DataClient {

	/**
	 * @param interval  -- millisecond time interval, with in this time-window, the var-data is recorded as min-max
	 */
	public MinMaxer(int interval) {
		_interval = interval;
		_data =  new float[2];
		_data[0] = Float.MAX_VALUE;
		_data[1] = -Float.MAX_VALUE;
	}

	
	/**
	 * The beg+interval, the maximun of the ttag in the object
	 */
	private long _endTime = Long.MIN_VALUE;
	
	/**
	 * The time-window(in msec) for the stat-calculation
	 */
	private int _interval;

	/**
	 *  min and max values
	 */
	private float[] _data = null;

	/**
	 * clients who share the same variable's float-value
	 */
	private ArrayList<DataClient> _clients = new ArrayList<DataClient>();
	
	/**
	 * caller passes a time-tag in microsecond, and a float data point
	 * For one second data, record max and min in the list array
	 * If the data's ttag exist the max-time of this object, distribute the data to plots, and renew this object
	 *
	 * @param samp  -- the data sample
	 * @param offset	-- index into the sample of the first data value to receive
	 */
	public void receive(FloatSample samp,int offset)
	{
		long ttag= samp.getTimeTag();
		//System.err.printf("samp-id= %x \n", samp.getId());

		if (ttag >= _endTime) {
			if (_endTime > Long.MIN_VALUE) {
				if (_data[0] != Float.MAX_VALUE) {
					FloatSample osamp = new FloatSample(_endTime - _interval / 2, samp.getId(), _data);
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
	public void addClient(DataClient clnt) 
	{
	    if (clnt==null) return;
		// prevent adding twice
		synchronized (_clients)
		{
			removeClient(clnt);
			_clients.add(clnt);
		}
	}

	/**
	 * Remove a plot client
	 */
	public void removeClient(DataClient clnt) 
	{
	    if (clnt==null) return;
		synchronized (_clients)
		{
			_clients.remove(clnt);
		}
	}

	private void distribute(FloatSample samp)
	{

		ArrayList<DataClient> listcopy;
		synchronized (_clients)
		{
			listcopy = _clients;

			for (int i = 0; i < listcopy.size(); i++) {
				DataClient clnt = listcopy.get(i);
				clnt.receive(samp,0); //the out-sample only contains the min and max
			}
		}
	}
}



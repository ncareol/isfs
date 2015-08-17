
package edu.ucar.nidas.apps.cockpit.model;

import edu.ucar.nidas.core.FloatSample;

/**
 * The interface to mask the data-pusher, and data-retrieval objects.
 * 
 * In the udp-socket-data-receiver, it gets the data from data-feeder, parses the nidas-data into the sample-data, 
 * and puts the sample-data to its minMaxer container.
 * 
 * In the plot, it gets the min-max of the sample from the minMaxer, map the sample-data to y-axis, and the time-data to x-axis.
 *  
 * @author dongl
 *
 */
public interface DataClient {

    public void receive(FloatSample f,int offset);

    public void addClient(DataClient dc);

    public void removeClient(DataClient dc);

}



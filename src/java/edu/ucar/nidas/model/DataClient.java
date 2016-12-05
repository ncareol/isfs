
package edu.ucar.nidas.model;

/**
 * A DataClient receives samples.
 */
public interface DataClient {

    public void receive(FloatSample f,int offset);

}




package edu.ucar.nidas.model;

/**
 * A DataSource provides samples to DataClients
 */
public interface DataSource {

    public void addClient(DataClient dc);

    public void removeClient(DataClient dc);

}



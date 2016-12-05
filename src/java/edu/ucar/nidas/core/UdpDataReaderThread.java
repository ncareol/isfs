package edu.ucar.nidas.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

import edu.ucar.nidas.model.NotifyClient;
import edu.ucar.nidas.model.StatusDisplay;
import edu.ucar.nidas.model.DataClient;
import edu.ucar.nidas.model.FloatSample;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;

/**
 * Data thread.
 * It receives data from the prepared data-socket, 
 * parses the nidas data into FloatSample, 
 * fetches the FSample data to the min-max client,
 * and the min-max performs a stat to keep min and max
 * 
 *  This is an independent thread to listen to the data feeder, that is apart of UI thread
 *    
 * @author dongl
 *
 */
public class UdpDataReaderThread extends Thread
{

    NotifyClient _notify;
    
    //UI
    StatusDisplay _status;
  
    //data 
    public static int _buffLen = 16384;
    private DatagramPacket _packet;    	//get data from server
    private ByteBuffer _bf;				//get data from server
    private DatagramSocket _dsocket;

    //data_parsser
    NidasSampleParser _nsp = new NidasSampleParser();

    /**
     *  mapping from a sample id to a Sample
     */
    HashMap<Integer,Sample> _idToSample = new HashMap<Integer, Sample>();

    /**
     * For each Var, a Set of clients.
     * There is typically one client per variable.
     */
    HashMap<Var, HashSet<DataClient> > _varToClients =
        new HashMap<Var, HashSet<DataClient> >();

    public UdpDataReaderThread(DatagramSocket sock,
            StatusDisplay status, NotifyClient notify)
    {
        _dsocket = sock;
        _status = status;
        _notify = notify;
        buildDataBuf();
    }

    @Override
    public void interrupt()
    {
        _status = null;
        _notify = null;
        super.interrupt();
        _dsocket.close();
    }

    /**
     * create a new buffer with Little-Indian byte order 
     */
    private void buildDataBuf() {
        byte[] buffer = new byte[_buffLen];
        _packet = new DatagramPacket(buffer, buffer.length);
        _bf = ByteBuffer.wrap(buffer, 0, buffer.length);
        _bf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Add a DataClient to the the data-thread
     *  
     * @param samp The Sample containing the Var
     * @param var The Var containing the data for the client.
     * @param index The index into the Var's data which the client is to receive.
     */
    public void addClient(Sample samp, Var var, DataClient client)
    {
        synchronized (_varToClients){
            HashSet<DataClient> varClients =
                _varToClients.get(var);
            if (varClients == null) {
                varClients = new HashSet<DataClient>();
                _varToClients.put(var, varClients);
            }
            varClients.add(client);
            _idToSample.put(samp.getId(), samp);
        }
    }

    /*****************************************************************
     * listen to at the port from the data-socket, 
     * read the data packets from the data server,
     * parse the nidas data into FloatSample
     * fetch the data to MinMax client
     *  
     */
    public void run( )
    {
        Util.prtDbg("dataTh-run()");
        
        // Now loop forever, waiting to receive packets and printing them.
        int retry = 0;
        
        while(!isInterrupted()) {
            // Wait to receive a datagram
            try {
                _dsocket.setSoTimeout(10000);
                _dsocket.receive(_packet);
            }
            catch (IOException e) {
                _dsocket.close();
                if (_notify != null) {
                    if (_status != null)
                        _status.show(e.getMessage() + ". Reconnecting...", -1);
                    _notify.notify();
                }
                else {
                    if (_status != null)
                        _status.show(e.getMessage(), -1);
                }
                return;
            }
                       
            _bf.limit(_packet.getLength());

            for ( ;; ) {
                FloatSample samp = _nsp.parseSample(_bf,_packet.getLength());
                if (samp == null) break;
                int sampId = samp.getId();
                // System.out.printf("sampId=%d\n",sampId);

                synchronized(_varToClients){
                    Sample sample = _idToSample.get(sampId);
                    if (sample == null) continue;

                    for (Var var : sample.getVars()) {

                        int offset = sample.getOffset(var);
                        Set<DataClient> varClients = _varToClients.get(var);
                        if (varClients == null) continue;
                        Iterator<DataClient> itr = varClients.iterator();
                        while (itr.hasNext()) {
                            DataClient client = itr.next();
                            client.receive(samp,offset);
                        }   
                    }
                }
            } //forloop
            _bf.rewind();
        }
        _dsocket.close();
        // Util.prtDbg("dataTh-run()--endof-data-loop");
    }
}

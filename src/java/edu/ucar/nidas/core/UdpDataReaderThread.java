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
import edu.ucar.nidas.model.Log;
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

    private NotifyClient _notify;

    static final Object _lockObj = new Object();
    
    /**
     * Where to send log messages.
     */
    private Log _log;

    /**
     * Where to send status messages.
     */
    private StatusDisplay _status;
  
    public static int buffLen = 16384;

    private DatagramPacket _packet;    	//get data from server

    private ByteBuffer _bf;				//get data from server

    private DatagramSocket _dsocket;

    //data_parsser
    NidasSampleParser _parser = new NidasSampleParser();

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
            StatusDisplay status, Log log, NotifyClient notify)
    {
        _dsocket = sock;
        _log = log;
        _status = status;
        _notify = notify;
        buildDataBuf();
    }

    @Override
    public void interrupt()
    {
        synchronized (_lockObj) {
            _notify = null;
        }
        super.interrupt();
        _dsocket.close();
    }

    /**
     * create a new buffer with little-endian byte order 
     */
    private void buildDataBuf() {
        byte[] buffer = new byte[buffLen];
        _packet = new DatagramPacket(buffer, buffer.length);
        _bf = ByteBuffer.wrap(buffer, 0, buffer.length);
        _bf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Add a DataClient to this thread.
     *  
     * @param samp The Sample containing the Var
     * @param var The Var that the client is interested in.
     * @param client The client.
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
     * Read data packets from a DatagramSocket.
     * Parse the into FloatSamples. Pass the data in
     * the samples to interested DataClients.
     * On an IOException, call the wake() method on 
     * a NotifyClient, which is how a reconnection is done.
     *  
     */
    public void run( )
    {
        /*
         * Loop forever, receiving packets, parsing into
         * to samples, and send them to DataClients.
         */
        int retry = 0;
        
        while(!isInterrupted()) {
            // Read a DatagramPacket.
            try {
                _dsocket.setSoTimeout(10000);
                _dsocket.receive(_packet);
            }
            catch (IOException e) {
                _dsocket.close();
                _log.error(e.toString());
                synchronized (_lockObj) {
                    if (_notify != null) {
                        _status.show(e.toString() + ". Reconnecting...");
                        _notify.wake();
                    }
                    else {
                        _status.show(e.toString());
                    }
                }
                return;
            }
                       
            _bf.limit(_packet.getLength());

            for ( ;; ) {
                FloatSample samp = _parser.parseSample(_bf,_packet.getLength());
                if (samp == null) break;
                int sampId = samp.getId();
                // System.out.printf("sampId=%d\n",sampId);

                synchronized(_varToClients){
                    Sample sample = _idToSample.get(sampId);
                    if (sample == null) continue;

                    for (Var var : sample.getVars()) {

                        Set<DataClient> varClients = _varToClients.get(var);
                        if (varClients == null) continue;

                        int offset = sample.getOffset(var);
                        for (DataClient client: varClients) {
                            client.receive(samp,offset);
                        }   
                    }
                }
            } // loop over samples in packet
            _bf.rewind();
        }
        _dsocket.close();
    }
}

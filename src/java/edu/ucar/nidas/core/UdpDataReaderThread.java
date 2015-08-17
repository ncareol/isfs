package edu.ucar.nidas.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;

import edu.ucar.nidas.apps.cockpit.core.ReconnectionClient;
import edu.ucar.nidas.apps.cockpit.model.DataClient;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.ui.StatusBarClient;
import edu.ucar.nidas.util.Util;

/**
 * This class is the data-feeder center. 
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

    //network
    UdpFeed _udpf;
    ReconnectionClient     _rc;
    
    //UI
    StatusBarClient        _sbc;
  
    //data 
    public static int _buffLen=16384;
    private DatagramPacket _packet;    	//get data from server
    private ByteBuffer _bf;				//get data from server
    private DatagramSocket         _dsocket;


    //data_parsser
    NidasSampleParser _nsp = new NidasSampleParser();

    /**
     *  mapping from a sample id to a list of Vars
     */
    HashMap<Integer,ArrayList<Var> > _idToVars = new HashMap<Integer,ArrayList<Var> >();

    /**
     * For each Var, a two dimensional ArrayList of clients.
     * The first index of the ArrayList is for each value in the Variable, since
     * there can be more than one.  The second index points to the list of clients
     * for that value.
     */
    HashMap<Var,ArrayList<ArrayList<DataClient> > > _varToClients =
        new HashMap<Var,ArrayList<ArrayList<DataClient> > >();

    // loopState
    boolean _abort = false;
    //boolean _stopped=false;

    synchronized public void abort() {_abort=true;}
    //public boolean getStatus() {  return  _stopped; }

    public void setRC(ReconnectionClient rc) {_rc=rc;}
    
    public HashMap<Integer,ArrayList<Var> >  getIdToVars () {
        return _idToVars;
    }

    public void setIdToVars(HashMap<Integer,ArrayList<Var> >  id2Vars) {
        _idToVars=id2Vars;
    }

    public HashMap<Var,ArrayList<ArrayList<DataClient> > > getVarToClients( ) {
        return _varToClients;   
    }

    public void setVarToClients(HashMap<Var,ArrayList<ArrayList<DataClient> > >  var2Clnt) {
        _varToClients=var2Clnt;
    }

    /**
     * set data feed 
     */
    public void setUdpFeed(UdpFeed udpf) {
        _udpf=udpf;
    }

    public void setStatusBar(StatusBarClient sbc) {
        _sbc=sbc;
    }
    /**
     * create a new buffer with Little-Indian byte order 
     */
    private void buildDataBuf() {
        byte[] buffer = new byte[_buffLen];
        _packet = new DatagramPacket(buffer, buffer.length);
        _bf= ByteBuffer.wrap(buffer, 0, buffer.length);
        _bf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Add a MinMax client to the the data-thread
     *  
     * @param samp The Sample containing the Var
     * @param var The Var containing the data for the client.
     * @param index The index into the Var's data which the client is to receive.
     */
    public void addClient(Sample samp, Var var, int index, DataClient client)
    {
        synchronized (_varToClients){
            ArrayList<Var> vars = _idToVars.get(samp.getId());
            if (vars == null) {
                vars = samp.getVars();
                _idToVars.put(samp.getId(),vars);
            }

            ArrayList<ArrayList<DataClient> > varClients = _varToClients.get(var);
            ArrayList<DataClient> clients;
            if (varClients == null || index >= varClients.size()) {
                if (varClients == null) 
                    varClients = new ArrayList<ArrayList<DataClient> >();
                _varToClients.put(var,varClients);

                for (int i = varClients.size(); i < index; i++) varClients.add(new ArrayList<DataClient>());
                clients = new ArrayList<DataClient>();
                varClients.add(clients);
            } else clients = varClients.get(index);
            clients.remove(client);
            clients.add(client);

        }
    }


    /***********************************************************************************
     * listen to at the port from the data-socket, 
     * read the data packets from the data server,
     * parse the nidas data into FloatSample
     * fetch the data to MinMax client
     *  
     */
    public void run( ) {
        Util.prtDbg("dataTh-run()");
        buildDataBuf();
        
        // Now loop forever, waiting to receive packets and printing them.
        int retry=0;
        _dsocket = _udpf.getUdpSocket();
        
        while(!_abort) {
            // Wait to receive a datagram
            try {
                _dsocket.setSoTimeout(10000);
                _dsocket.receive(_packet);
            } catch (Exception e) { //other exceptions
               reconnect();
               return;
            }
                       
            _bf.limit(_packet.getLength());

            for ( ;; ) {
                FloatSample samp = _nsp.parseSample(_bf,_packet.getLength());
                if (samp == null) break;
                int sampId=samp.getId();

                synchronized(_varToClients){
                    if (_idToVars==null || _idToVars.size()<=0) {
                        continue;
                    }
                    ArrayList<Var> vars = _idToVars.get(samp.getId());

                    if (vars==null || vars.size()<=0) {
                        continue;
                    }

                    for (int i = 0; i < vars.size(); i++) {
                        Var var = vars.get(i);
                        int offset = var.getOffset();
                        ArrayList<ArrayList<DataClient> > varClients = _varToClients.get(var);
                        for (int j = 0; j < var.getLength(); j++) {
                            if (varClients != null && j < varClients.size()) {
                                ArrayList<DataClient> clients = varClients.get(j);
                                for (int k = 0; k < clients.size(); k++) {
                                    DataClient client = clients.get(k);
                                    if (client==null ) continue;
                                    client.receive(samp,offset+j);
                                }
                            } 
                        }   
                    }
                }
            } //forloop
            _bf.rewind();
        }
        _udpf.closeSocket();
        Util.prtDbg("dataTh-run()--endof-data-loop");
    }
    
    private void reconnect() {
        if (_rc==null ) {
            if ( _sbc!=null) _sbc.receive("_reconneciton-client is null ", 10);
            return;
        }
        if (_sbc!=null) _sbc.receive("No data from the data feed. Reconnecting...", -1);
        _rc.setReconnect();
        _udpf.closeSocket();
    }

}//class

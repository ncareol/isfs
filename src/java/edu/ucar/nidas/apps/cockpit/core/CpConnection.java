package edu.ucar.nidas.apps.cockpit.core;

import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Document;

import com.trolltech.qt.gui.QMainWindow;

import edu.ucar.nidas.apps.cockpit.model.DataClient;
import edu.ucar.nidas.apps.cockpit.ui.CentTabWidget;
import edu.ucar.nidas.apps.cockpit.ui.CockPit;
import edu.ucar.nidas.apps.cockpit.ui.ServLookup;
import edu.ucar.nidas.core.UdpDataReaderThread;
import edu.ucar.nidas.core.UdpFeed;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.ui.StatusBarClient;
import edu.ucar.nidas.util.Util;


/**
 * This class encapsulates all the data-connection and data-descripter together
 * If use servLook up diag to get users' input and selection of data-feed, 
 * then retrieves the cockpit's data-descripter, and data-dup-feeder
 * creates a xml doc to serve as data-descripters,and a data-thread to read data from data-source.
 * @author dongl
 *
 */
public class CpConnection {
    
    /**
     * a copy of cockpit main thread
     */
    CockPit _cp;
    
    /**
     * diaglog that serves users' interaction of data-feed selection 
     */
    ServLookup   _servLookup;

   /**
    * user's input from the program 
    * unicast-input -s serv:port
    */
    String       _servOpt; 
    
    /**
     * Data-feed thread to handle data from sensors, and UI thread for display
     */
    public static UdpDataReaderThread _dataTh;

    /**
     * UdpFeed
     */
    UdpFeed   _udpFeed;
    
    /**
     * a point of centTab widget in the cockpit program
     */
    CentTabWidget _centWidget;
     
    StatusBarClient _sbc;
    
    
    public CpConnection (CockPit cp){
        _cp=cp;
        _centWidget=_cp.getCentWidget();
        _servOpt=_cp.getServOpt();
    }
    
    
    /**
     *  This method is a key to make network connection and get data-feeder.
     *      call ServerLookup to allow users to choose data-feeder
     *      create a TCP-socket to get the data-descriptor from the chosen data-feeder
     *      create a data-thread to listen to the data-feed
     */
    public boolean connServ() {
      
        // get server-lookup-diaglog 
        _servLookup = new ServLookup(null, _servOpt); //unicast-input -s serv:port
        if (_servLookup==null) {
            Util.prtErr(" Cannot create ServLookup Diaglog");
            return false;
        }
    
        _udpFeed = _servLookup.getUdpFeed();
        if (!connectSampDataTh(_udpFeed)) return false;
   
        _centWidget.setName(_servLookup.getProjectName());
        String name= _servLookup.getProjectName()+ " COCKPIT";
        ((QMainWindow)_centWidget.parent()).setWindowTitle(name);
        _centWidget.setName(name);
        _centWidget.createPrimaryGauges();
          
        return true;
    }
        
    
    /**
     * _tcpSocket is the connection keeper to watch the data-line
     * when the tcpsocket is not alive, the program needs to reconnect data-feed back to the server.
     */
    public boolean  reconnect(){
        synchronized (this) {
            Util.prtDbg("reconnect()");
            if (_servLookup==null) {
                Util.prtErr(" ServLookup Diaglog doesn't exist");
                return false;
            }
                 
            int i=0;
            UdpFeed udpFeed;
            while (true) {
                try {
                    Util.prtDbg("re-connect =" + i++);
                    udpFeed= _servLookup.reconnectUdpFeeder();
                    if (udpFeed!=null) {
                        break; 
                    }
                    Thread.sleep(5000);
                } catch (Exception e) {}
            }
            
            //reconnect data thread
            boolean tr= connectSampDataTh(udpFeed);
            return tr;
        }
        //reset status bar
    }

    
    private boolean connectSampDataTh(UdpFeed udpf) {
        if (udpf==null) {
            //Util.prtErr(" UdpFeed = null");
            return false;
        }

        //get xml
        Document doc = udpf.getXmlDoc();
        if (doc ==null) {
            Util.prtErr(" xml_doc=null... \n Please check data server and retry");
            return false;
        }
        //////map data-descriptor to var to plot 
        Util.prtDbg("getSampsFromDataDescriptor ======");
        if (_centWidget.getSamps()==null || _centWidget.getSamps().size()<=0)
            _centWidget.setSampsFromDataDescriptor(doc); //get vars
        else {}//ToDo add new sample 
        
        if (_centWidget.getSamps()==null || _centWidget.getSamps().size()<=0){ //one more time
            Util.prtErr(" Samples=null after connection \n Please check data descripttion.");
            return false;
        }
            
        //get tcpsocket and keep it alive
        Socket tcpSocket= udpf.geTcptSocket();
        if (tcpSocket ==null) {
            Util.prtErr(" tcpSocket=null... \n Please check data server and retry");
            return false;
        }
  
        Util.prtDbg("to create UdpDataReadera thread======");
        DatagramSocket dsocket = udpf.getUdpSocket();
        if (dsocket ==null) {
            Util.prtErr(" UdpSocket=null... \n Please check data server and retry");
            return false;
        }
        ////// create data-thread
        HashMap<Var,ArrayList<ArrayList<DataClient> > > v2c =new HashMap<Var,ArrayList<ArrayList<DataClient> > >();
        HashMap<Integer,ArrayList<Var> > id2v=new HashMap<Integer,ArrayList<Var> >();
        if (_dataTh!=null) {
            v2c.putAll(_dataTh.getVarToClients());
            id2v.putAll(_dataTh.getIdToVars());
            _dataTh.abort();
        }
        _dataTh = new UdpDataReaderThread(); 
        _sbc = new StatusBarClient(_cp.getStatusBar());
        ReconnectionClient reconnectClient= new ReconnectionClient(_cp);
        _dataTh.setIdToVars(id2v);
        _dataTh.setVarToClients(v2c);
        _dataTh.setUdpFeed(udpf);
        _dataTh.setStatusBar(_sbc);
        _dataTh.setRC(reconnectClient);
        _dataTh.start();
        
        return true;
    }

  
    
    public void exitConnection(){
        if (_dataTh != null )  _dataTh.abort();
        if (_udpFeed != null)  _udpFeed.closeSocket();
    }
    
    public StatusBarClient getStatusBarClient() {return _sbc;}

}

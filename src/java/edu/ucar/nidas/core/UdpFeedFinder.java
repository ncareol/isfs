package edu.ucar.nidas.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownServiceException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import edu.ucar.nidas.util.Util;



/**
 * This class retrieves all the potential Udp_data feeders in the network, 
 * and provide the a list of the data-feeders  
 * 
 * @author dongl
 *
 */
public class UdpFeedFinder {
    /**
     * initial user's pick from ServLookup 
     */
    String _servName;
    int    _port;

    DatagramSocket 	_dsocket;
    InetAddress 	_servAddr;
    InetSocketAddress _servSAddr;

    DatagramPacket  _packet;    	//get data from server
    byte[]			_buffer;
    ByteBuffer		_bf; 
    int             _rcvSckLocalPort ;

    public static int MAX_TIMEOUT = 2000; //2sec
    List<UdpFeedInf> _servs = new ArrayList<UdpFeedInf> ();

    public int getRcvSckLocalPort() {return _rcvSckLocalPort;}
    public DatagramSocket getRcvDataSock() {return _dsocket;}
    public InetAddress getServAddr() { return _servAddr;}
    public InetSocketAddress getServSocketAddress() {return _servSAddr;}

    /***********************************************************************************
     * 1> Establish a datagram connection to a nidas server.
     *  If the server address is a multicast address:
     *    1> Open a MulticastSocket, bound to any available local port.
     *  If the server address is not a multicast address:
     *    1> Open a DatagramSocket (which is also bound to any available local port)
     *
     *  2> Multicast a request packet to the address and port specified as arguments,
     *      The packet contains 4bytes magic + 4bytes nidas request number (value=2) +
     *          2bytes local port + 2bytes socketType (2=datagram)
     * 
     *  3> 	read data-grams _packets from any data server that responds. The packets will contain
     *     	server information (port#(2bytes), servername(terminated\0), and dsms) at the
     *     	open udp port (cp1) 
     *
     *  4> parse the server data and store the data into the list<UdpFeedInf>
     *
     * @throws IOException
     */
    public List<UdpFeedInf> findServers(String servName,  int port ) throws  IOException
    {
        _servName=servName;
        _port=port;
        
        Util.prtDbg("UdpServInfReader- findServers- sernname ="+ _servName+ " port="+_port);
        //create a udp socket to find  servers
        _servAddr =  InetAddress.getByName(_servName);
        if (_servAddr.isMulticastAddress() ) {
            MulticastSocket msock = new MulticastSocket(0);
            _dsocket = msock;
        }
        else {
            _dsocket = new DatagramSocket();
        }

        _rcvSckLocalPort= _dsocket.getLocalPort();	
        Util.prtDbg("dsocket_port="+ _rcvSckLocalPort );

        //build packet and display the contents
        buildClientInfPack((short)_rcvSckLocalPort);
        _servSAddr = new InetSocketAddress(_servAddr,_port);
        _packet.setSocketAddress(_servSAddr);

        _servs.clear(); //clear server inf
        long stime = System.currentTimeMillis();
        _dsocket.setSoTimeout(2500); //.25sec

        for (int i=0; i<3 && _servs.size()==0 ;i++) {
            _dsocket.send(_packet);
            Util.prtDbg("sent # bytes=" +   _packet.getLength());

            while ((stime + 2000)> System.currentTimeMillis()) {
                //receive server infor 
                int buffLen = 1024;
                byte[] dbuffer = new byte[buffLen];
                DatagramPacket dpacket = new DatagramPacket(dbuffer, buffLen);

                try {
                    _dsocket.receive(dpacket);
                    Util.prtDbg("received # bytes=" +
                            dpacket.getLength());
                    UdpFeedInf serv = parseDataFeederInf(dpacket);
                    _servs.add(serv);
                }
                catch(SocketTimeoutException e)
                {
                    // no problem
                }	catch(Exception ee) {
                    Util.prtException(ee,"UdpServInfReader- dsocket-receiving-error..." );
                }
            }
        }
        _dsocket.setSoTimeout(0); //set it to alive
        return _servs;
    }

    public void closeSocket() {
        try {
            _dsocket.close();
        } catch (Exception e) {}
    }

    /**
     * Read bytes from a ByteBuffer, up to a '\0', return at UTF-8 String
     * Return null if nothing left in buffer.
     */
    private String getString(ByteBuffer bb) throws UnsupportedEncodingException
    {
        int ib =  bb.position();
        int lb =  bb.limit();
        if (ib == lb) return null;
        byte[] bstr = new byte[lb - ib];
        int i;
        for (i = 0; ib < lb && (bstr[i++] = bb.get()) != '\0'; ib++) ;
        return new String (bstr,0,i, "UTF-8"); 
    }
    
   
    /**
     * parse a udp-feeder packet, to get one UdpFeedInf (UdpFeeder's network information)
     * 
     * @param packet
     * @return
     * @throws IOException
     */
    public UdpFeedInf parseDataFeederInf (DatagramPacket packet) 	throws IOException  
    {
        if (packet==null|| packet.getLength()<=0) {
            Util.prtErr("ServInfThread->run(): pacakge is null");
            return null;
        }

        byte[] buffer = packet.getData();
        Util.prtDbg("Received server inf data from: " + packet.getAddress().toString() +
                ":" + packet.getPort() + " with length: " +	packet.getLength());
        Util.prtDbg(buffer.toString());

        UdpFeedInf serv = new UdpFeedInf();
        serv.setIpAddr(packet.getAddress());

        // decode _packet data to get servname, dsmname, tcpport -> list<servInf>
        //get port

        ByteBuffer cb = ByteBuffer.wrap(buffer, 0, packet.getLength());
        cb.order(ByteOrder.BIG_ENDIAN);

        int magic = cb.getInt();
        if (magic != 0x76543210)
            throw new UnknownServiceException("incorrect magic number received: " + Integer.toHexString(magic));

        serv.setPort(cb.getShort());
        Util.prtDbg("tcp-port="+serv.getPort());

        serv.setUDPPort(cb.getShort());
        Util.prtDbg("udp-port="+serv.getUDPPort());

        serv.setServ(packet.getAddress().getHostName());
        getString(cb); //skip short server name
        serv.setProjectName(getString(cb));

        for ( ;; ) {
            String str = getString(cb);
            if (str == null) break;
            serv.addDsm(str);
            Util.prtDbg("dsm="+str);
        }
        serv.printServInf();
        return serv;
    }

    /**
     * build a client udp _packet information _buffer that contains  
     *  4 bytes of magic#    0x01234567  --bigendian
     *  4 bytes of req#      2=udp		--bigendian
     *  2 bytes of client port  --get from udpsocket at any available port   --bigendian
     *  2 bytes of cast type 2=udp		--bigendian
     * @param buffLen
     */
    private void buildClientInfPack (short clientPort) { //throws BufferOverflowException, IndexOutOfBoundsException  {

        //get magic
        byte[] magic = new byte[4];
        ByteBuffer mbuf=  ByteBuffer.wrap(magic); 
        mbuf.order(ByteOrder.BIG_ENDIAN);
        mbuf.putInt(0x01234567);

        //get req
        byte[] req = new byte[4];
        ByteBuffer rbuf=  ByteBuffer.wrap(req); 
        rbuf.order(ByteOrder.BIG_ENDIAN);
        rbuf.putInt(2);

        //get client port
        byte[] port = new byte[2];
        ByteBuffer pbuf=  ByteBuffer.wrap(port); 
        pbuf.order(ByteOrder.BIG_ENDIAN);
        pbuf.putShort((short)clientPort);

        //get socket type
        byte[]  socketType= new byte[2];
        ByteBuffer sbuf=  ByteBuffer.wrap(socketType); 
        sbuf.order(ByteOrder.BIG_ENDIAN);
        sbuf.putShort((short) 2);

        int buffLen =12; // 4bytes magic, 4 bytes req#, 2bytes cp, 2bytes udp 
        _buffer = new byte[buffLen];
        _bf= ByteBuffer.wrap(_buffer);
        _bf.put(mbuf.array());
        _bf.put(rbuf.array());
        _bf.put(pbuf.array());
        _bf.put(sbuf.array());

        _packet = new DatagramPacket(_bf.array(), buffLen);

    }
    
}


package edu.ucar.nidas.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import edu.ucar.nidas.util.Util;


/**
 * 
 * This class builds a tcp socket connection to the data-feeder 
 *  and builds a XmlInputStream class to read out the input.    
 * 
 * @author dongl
 *
 */
public class SocketXmlReader {

    protected XMLInputStream _xmlIS;

    public XMLInputStream getXMlIS() {
        return _xmlIS;
    }

    /**
     *      It creates a tcp-socket, 
     *      sends packet to the data-feeder, 
     *      get a connection 
     */

    public Socket connect(InetAddress addr, int tcpPort, int rcvSckLocPort) throws  IOException {
        _xmlIS= null;

         Util.prtDbg("Tcp server="+ addr.toString()+  " tcpPort="+ tcpPort+ " locPort="+ rcvSckLocPort+ "\n");
        Socket s = new Socket(addr, tcpPort); 

        //send sndSckLocPort
        ByteBuffer pbuf=  ByteBuffer.wrap(new byte[6]); 
        pbuf.order(ByteOrder.BIG_ENDIAN);
        pbuf.putInt(0x76543210);
        pbuf.putShort((short)rcvSckLocPort);

        s.setSoTimeout(500); //.5sec
        s.getOutputStream().write(pbuf.array());

        //get input stream 
        s.setSoTimeout(0); // stay alive   
        
        //_xmlIS = new XMLInputStream( s.getInputStream());

        return s;
    }


    /**
     * This class reads the input stream based on the data-feeder protocol
     * 
     * @author dongl
     *
     */
    public class XMLInputStream extends InputStream {

        boolean _eof =false;
        InputStream _is;


        public XMLInputStream(InputStream s ) {
            _is=s;
        }

        public int read() throws IOException {
            if (_eof) return -1;
            byte[] c = new byte[1];
            int count=0;
            count= _is.read(c);
            //System.out.printf("c= %x      \n",c[0]);

            if (count==1 && c[0]==4){
                _eof=true;
                //System.out.println("eof="+ _eof );
                return -1;
            }
            return c[0];
        }

        public int read(byte[] b) throws IOException {
            if (_eof) return -1;
            int count = 0;
            count=_is.read(b);
            //System.out.printf("b= %s ", b.toString());
            for (int i=0; i<count; i++) {
                if (b[i]==4) {
                    count =i;
                    _eof=true;
                }
            }
            //	System.out.println("count="+count + "eof="+_eof);
            return count;
        }

    }
}


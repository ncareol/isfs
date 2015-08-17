package edu.ucar.nidas.apps.cockpit.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;

import edu.ucar.nidas.apps.cockpit.core.DataDescriptorReader;
import edu.ucar.nidas.apps.cockpit.model.XmlDom;
import edu.ucar.nidas.core.UdpFeedFinder;
import edu.ucar.nidas.util.Util;


//test class -1
class SquareClient {

	public static void test(String args0, String args1, String args2) throws  IOException {
		String server = args0;
		int port = Integer.parseInt(args1);
		double value = Double.valueOf(args2).doubleValue();
		InetAddress addr = InetAddress.getByName(server);
		System.out.println("server="+ server+ " addr="+addr.getHostName()+ "  "+ addr.getHostAddress()+ "\n");

		Socket s = new Socket(addr, port); //or Socket s = new Socket(server, port);
		// OutputStream os = s.getOutputStream();
		// DataOutputStream dos = new DataOutputStream(os);
		// dos.writeDouble(value);

		InputStream is = s.getInputStream();

		Charset charset = 
			Charset.forName("ISO-8859-1");
		CharsetDecoder decoder = 
			charset.newDecoder();

		// Allocate buffers
		ByteBuffer buffer = 
			ByteBuffer.allocateDirect(1024);


		CharBuffer charBuffer = 
			CharBuffer.allocate(1024);

		byte[] buff = new byte[1024];
		// Read response
		while ((is.read(buff)) != -1) {
			//System.out.println("buff="+ buff.length + " "+ buff.toString());

			buffer= buffer.put(buff);
			System.out.println("buffer before flip= "+buffer);

			buffer.flip();
			System.out.println("buffer after flip= "+buffer);

			// Decode buffer
			decoder.decode(buffer, charBuffer, false);
			System.out.println("charbuffer before flip= "+charBuffer);

			// Display
			charBuffer.flip();
			System.out.println(charBuffer);

			buffer.clear();
			charBuffer.clear();
		}
		s.close();
	}
}


//test class-2
class Xml {
	public static void test () {
		XmlDom xdom = new XmlDom("/h/eol/dongl/nidas/java/src/edu/ucar/nidas/apps/cockPit/xml/cockpitConfig.xml" , "cockpitsave.xml");
		xdom.parseXml("/h/eol/dongl/nidas/java/src/edu/ucar/nidas/apps/cockPit/xml/cockpitConfig.xml");
		String n=xdom.getValue("server", null,null, "name");
		if  ("127.127.0.0".equals(n)) {
			System.out.println("find server name is ok");
		} else {
			System.out.println("Cannot find server name "+ n);
		}

		//Util.prtMsgBox(xdom.getValueById("T.3m.location", "unit"));
		xdom.setAttribute("plot",  "name", "T.3m.location", "newAttr", "T.3m.new");
		xdom.addAttribute("plot",  "name", "T.3m.location", "Ttry","Ttryadd"); 
		xdom.addAttribute("plot",  "name", "v.3m.location", "try","tryadd"); 
		xdom.removeAttribute("plot",  "name", "v.3m.location", "try"); 
		xdom.removeAttribute("plot",  "name", "T.3m.location", "Ttry"); 

		xdom.addNode("server", null, null, "servernewNodeName", "newNodeIdVal", null);
		xdom.addNode("plot",  "name", "v.3m.location", "plotnewNodeName", "newNodeIdVal", "nodetext");
		System.out.println("Expecting newNodeIdVal, get= "+xdom.getValue("servernewNodeName",null, null, "name"));
		System.out.println("Expecting nodetext, get= "+xdom.getNode("plotnewNodeName","name", "newNodeIdVal" ).getTextContent());

		xdom.removeNode("server", null, null, "servernewNodeName");
		xdom.removeNode("plot",  "name", "v.3m.location", "plotnewNodeName");


		xdom.saveDataDescriptor(xdom.getDoc());

		//add the first node to a dom document
		//xdom = new XmlDom();
		XmlDom xdom1 = new XmlDom(null , "cockpitsave.xml");

		xdom1.addNode("config", null, null, "Tag", "newNodeIdVal", null);
		xdom1.addNode("config", null, null, "Tag", "newNodeIdVal", null);
		xdom1.addNode("config", null, null, "Tag", "newNodeIdVal-1", null);
		xdom1.saveDataDescriptor(xdom.getDoc());
	}
}

//tests
public class Test {
	public static void startSocket() {
		System.out.println("Start testing of startSocket...");

		try {
			DataDescriptorReader fr = new DataDescriptorReader();
//			fr.start("porter", 31000, 9999);
			//System.out.println("socket xml file name="+fr.getXml());
		} catch (Exception e ) {
			Util.prtException(e, "Test.startSocket().err");
		}
	}


	public static void startXml() {
		System.out.println("Start testing of startXml...");

		try {
			Xml.test();
		} catch (Exception e ) {
			Util.prtException(e, "Test.startXml.err");
		}
	}


		
	public static void startServ() {
		System.out.println("Start testing of startServ...");

		try {

			UdpFeedFinder ur = new UdpFeedFinder();
//			ur.start("239.0.0.10", 30000);

			//ur.startms("239.0.0.10",31000);
		} catch (Exception e ) {
			Util.prtException(e, "Test.startServ().err");
		}
	}

	  
	//test model/DataDescriptor
	public static void startCpXml(){
		System.out.println("Start testing of DataDescriptor class...");

//		DataDescriptor cpxml= new DataDescriptor("cockpitsave.xml");
	//	cpxml.walkThrough();
		//cpxml.printAll();

	}

	public static void startCfgXml(){
		System.out.println("Start testing of configXml class...");

	//	DataDescriptor cpxml= new DataDescriptor("cockpitsave.xml");
	//	cpxml.walkThrough();
		
	//	UserConfig cfgxml= new UserConfig("cockpit.xml");
		//cfgxml.pushVars(cpxml.getVars());
		//cfgxml.pushVars(cpxml.getVars());
		//cfgxml.pushVars(cpxml.getVars());
	}
	
	public static void startServDiag() {
		List<String> servOpt = new ArrayList<String>();
		servOpt.add("serv1 port1 dsm1");
		servOpt.add("serv1 port1 dsm2");  
		servOpt.add("serv2 port1 dsm1");
		servOpt.add("serv2 port1 dsm2");
		
		
		//ServDialog servDiag = new ServDialog(null, true, servOpt);
		//int idx= servDiag.getIdx();
		//System.out.printf("\nSelected server = ", servOpt.get(idx));
	}

	
}

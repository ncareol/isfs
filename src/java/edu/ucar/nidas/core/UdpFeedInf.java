package edu.ucar.nidas.core;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import edu.ucar.nidas.util.Util;

/**
 * 
 * This class contains the information about a data-feeder
 *   server-name
 *   project-name
 *   tcp-port
 *   udp-port
 *   ip-address, etc
 *   
 * @author dongl
 *
 */
public class UdpFeedInf {
	String _serv=null;
	String _projectName=null;
	int _tcpPort=-1;
	int _udpPort=-1;
	InetAddress _addr=null;
	
	List<String>  _dsms = new ArrayList<String>();

	public void setServ(String serv) {_serv=serv;}
	public void setProjectName(String val) {_projectName=val;}
	public void setPort(int port) {_tcpPort=port;}
	public void setUDPPort(int port) {_udpPort=port;}
	public void addDsm(String dsm) {_dsms.add(dsm);}
	public void setIpAddr(InetAddress addr) {_addr=addr;}

	public String getServ() {return _serv.trim();}
	public String getProjectName() {return _projectName.trim();}
	public int getPort() {return _tcpPort;}
	public int getUDPPort() {return _udpPort;}
	public List<String> getDsms() {return _dsms;}
	public InetAddress getIpAddr() {return _addr;}
	
	public String getUserServInf() {
		String dsmStr="";
		for(int i=0; i< _dsms.size(); i++) {
			dsmStr += _dsms.get(i)+ ",";
		}
		return _serv.trim()+ " "+ _projectName.trim()+ " "+dsmStr;
	}
	
	
	public void printServInf() {
		String dsmStr="";
		for(int i=0; i< _dsms.size(); i++) {
			dsmStr += _dsms.get(i).trim()+ ",";
		}
		Util.prtDbg("UdpFeedInf-printServInf   serv= "+_serv + "  project="+ _projectName+ "\n  tcp_port="+ _tcpPort+ "   udp_port="+ _udpPort + "  dsms="+ dsmStr +"\n");
	}

}

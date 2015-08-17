package edu.ucar.nidas.apps.cockpit.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.Qt.Alignment;
import com.trolltech.qt.gui.QCheckBox;
import com.trolltech.qt.gui.QComboBox;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QFrame;
import com.trolltech.qt.gui.QGroupBox;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QRadioButton;
import com.trolltech.qt.gui.QTextEdit;
import com.trolltech.qt.gui.QVBoxLayout;

import edu.ucar.nidas.core.UdpFeed;
import edu.ucar.nidas.core.UdpFeedFinder;
import edu.ucar.nidas.core.UdpFeedInf;
import edu.ucar.nidas.ui.TextClient;
import edu.ucar.nidas.util.Util;


/**
 * This class is the data-feeder connection core.
 * It provides a UI interface for users to 
 *   define the preferred potential data feeders 
 *   then, when the data-feeder list come back, a user can choose which one to connect
 *   grouping the multiple data-feeders if needed to the data-socket
 *   
 * @author dongl
 *
 */
public class ServLookup extends QDialog{
    /**
     * User preferred data-feeder, entered as program arguments
     */
    private String _inputServ="localhost";
    /**
     * Default data-feeder port
     */
    private int    _port =30000;
    /**
     * Default multicast IP
     */
    private String _multiServ="239.0.0.10";;	

    private int _ttl;
    /**
     * Udp data server information
     */
    private UdpFeedInf  _udpFeedInf=null;
    private List<UdpFeedInf> _mUdpFeedInf=null;

    /**
     * Udp-feeder searcher
     */
    private UdpFeedFinder  _udpFeedFinder = null;

    /**
     * IMPORTANT member of the class
     * the final data-feed with dsocket, tcpsocket, and doc 
     */
    private UdpFeed _udpFeed;

    /**
     * reconnection
     */
    private String _prevServName; //for reconnection 
    private String _initServ;


    //diag-ui
    private QRadioButton _mserv, _sserv, _rb;
    private QPushButton _bcancel, _bok;
    private QTextEdit _jtPort, _jtmName, _jtsName, _debugTe;
    private QComboBox _jc, _cbData;
    private QLabel _jlServData, _jlPort;
    private QCheckBox _cbDetail;
    private TextClient _txClient;

    /**
     * The dialog that provides users with UI interface to perform selection and search of the data-feeder 
     * 
     * @param owner
     * @param modal
     * @param cp
     * @param inputS
     */
    public ServLookup(QFrame owner,  String inputS) {
        setModal(true);

        // parse the server-ip and port
        if (inputS!=null  && inputS.length()>0){
            inputS.trim();
            String[] ss= inputS.split(":");
            if (ss!=null && ss.length==2) {
                _inputServ= inputS.split(":")[0];
                _port= Integer.valueOf(inputS.split(":")[1].trim());
            }
            if (ss!=null && ss.length==1) {
                _inputServ= inputS;
            }
        }

        //create the UI-components
        createComp();
    }

    /**
     * get the Final data-feed	
     * @return
     */
    public UdpFeed getUdpFeed(){
        return _udpFeed;
    }

    /**
     * get project name from the data-server
     * @return
     */
    public String getProjectName() {
        if (_udpFeedInf!=null) return  _udpFeedInf.getProjectName();
        else return null;
    }

    private void toggleUI () {
        _bok.setText(" Ok   ");
        _mserv.setEnabled(false);
        _sserv.setEnabled(false);
        _jtPort.setEnabled(false);
        _jtmName.setEnabled(false);
        _jtsName.setEnabled(false);
        _jc.setEnabled(false);
        _jlPort.setEnabled(false);
        _jlServData.setVisible(true);
    }

    /**
     * Get the data-feeder's name from UI
     * @return
     */
    private String getServName() {
        _initServ=null;
        if (_sserv.isChecked()) {
            _inputServ=_jtsName.toPlainText().trim();
            _initServ= _inputServ;
        } else if (_mserv.isChecked()) {
            _multiServ=_jtmName.toPlainText().trim();
            _initServ= _multiServ;
        } 
        return _initServ;
    }

    /**
     *  Get the user preferred port
     * @return
     */
    private int getPort() {
        _port = Integer.valueOf(_jtPort.toPlainText().trim());
        return _port;
    }

    private int getTTL() {
        _ttl= _jc.currentIndex()+1;
        return _ttl;
    }

    /**
     * diag-ui creation
     */
    public void  createComp() {

        setWindowTitle("     Select a network connection type ");
        QVBoxLayout mlayout = new QVBoxLayout();

        //for row1 and row2
        QGroupBox qf = new QGroupBox();
        //qf.
        QVBoxLayout qv = new QVBoxLayout();
        //row1 multicast
        QHBoxLayout hlayout = new QHBoxLayout();
        _mserv = new QRadioButton("Multi-cast");
        _mserv.clicked.connect(this, "pressRMServ()");
        hlayout.addWidget(_mserv);
        _jtmName = new QTextEdit(_multiServ);
        _jtmName.setMaximumSize(200, 30);
        _jtmName.adjustSize();
        hlayout.addWidget(_jtmName);
        _jc = new QComboBox();
        for (int k=1; k<= 3; k++) {
            _jc.addItem(String.valueOf(k));
        }
        hlayout.addWidget(_jc);
        hlayout.addStretch();
        Alignment qal = new Alignment();
        qal.set(Qt.AlignmentFlag.AlignLeft);
        qv.addLayout(hlayout);

        //row 2 : radio= server textfield = servername
        hlayout = new QHBoxLayout();
        _sserv = new QRadioButton("Uni-cast  "); 
        _sserv.setChecked(true);
        _sserv.clicked.connect(this, "pressRSServ()");
        hlayout.addWidget(_sserv);
        _jtsName = new QTextEdit(_inputServ);
        _jtsName.setMaximumSize(200, 30);
        _jtsName.adjustSize();
        //_jtsName.setAlignment(Qt.AlignmentFlag.AlignRight);
        hlayout.addWidget(_jtsName);
        hlayout.addWidget(new QLabel());
        hlayout.addStretch();
        qv.addLayout(hlayout);
        qf.setLayout(qv);
        qf.setTitle("Network CpConnection Type");

        mlayout.addWidget(qf);

        //row-3  == port 
        hlayout = new QHBoxLayout();
        _jlPort= new QLabel("         Port#     ");
        hlayout.addWidget(_jlPort);
        _jtPort = new QTextEdit(""+_port);
        _jtPort.setMaximumSize(200, 30);
        _jtPort.adjustSize();
        //_jtPort.setAlignment(Qt.AlignmentFlag.AlignRight);
        hlayout.addWidget(_jtPort);
        hlayout.addWidget(new QLabel());
        hlayout.addStretch();
        //hlayout.setAlignment(qal);
        mlayout.addLayout(hlayout);


        //row 4  -servInf selection combobox
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel());
        mlayout.addLayout(hlayout);

        hlayout = new QHBoxLayout();
        _jlServData = new QLabel("Server Option:");
        _jlServData.setVisible(false);
        hlayout.addWidget(_jlServData);
        _cbData = new QComboBox();
        _cbData.setVisible(false);
        hlayout.addWidget(_cbData);
        mlayout.addItem(hlayout);

        //row-5 text-edit
        _cbDetail= new QCheckBox("Display CpConnection Details");
        _cbDetail.setChecked(false);
        _cbDetail.clicked.connect(this, "connDetail()");
        mlayout.addWidget(_cbDetail);
        _debugTe = new QTextEdit();
        //_debugTe.setGeometry(400,300,400,300);
        _debugTe.hide();
        mlayout.addWidget(_debugTe);
        _txClient = new   TextClient (_debugTe);

        //row-last   --ok-cancel buttons
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel());
        _bok= new QPushButton("Search", this);
        _bok.clicked.connect(this, "pressOk()");
        hlayout.addWidget(_bok);
        _bcancel= new QPushButton("Cancel", this);
        _bcancel.clicked.connect(this, "pressCancel()");
        hlayout.addWidget(_bcancel);
        hlayout.addWidget(new QLabel());
        mlayout.addItem(hlayout);

        setLayout(mlayout);
        setGeometry(400, 300, 400, 300);
        setVisible(true);
        exec();
    }

    void connDetail() {
        if (_cbDetail.isChecked()) {
            _debugTe.show();
            setGeometry(400,300,600,400);
        } else {
            _debugTe.hide();
            setGeometry(400,300,400,300);
        }
    }

    void pressRMServ(){
        if (_mserv.isChecked()) _sserv.setChecked(false);
        if (!_mserv.isChecked() && !_sserv.isChecked()) _mserv.setChecked(true);
    }

    void pressRSServ() {
        if (_sserv.isChecked()) _mserv.setChecked(false);
        if (!_mserv.isChecked() && !_sserv.isChecked()) _sserv.setChecked(true);
    }

    /**
     * This is a toggled button to search and finalize the data-feeder
     * if it shows "Search", it looks up the potential servers
     * else, it join the multicast group if needed, and close the dialog
     */
    void pressOk(){
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        Util._txClient=_txClient;
        if (_bok.text().trim().equals("Search")) {

            searchServ();
            if (_udpFeedInf!=null ) {  //ONLY ONE- find it
                Util.prtDbg("only one _servIp= "+_udpFeedInf.getServ());
                buildDataFeed();
                Util._txClient=null;
                close();
            }
        } else {
            if (_udpFeedInf==null && _cbData.isVisible() ) {
                _udpFeedInf=(UdpFeedInf)(_mUdpFeedInf.get(_cbData.currentIndex()));
                _prevServName=_udpFeedInf.getServ();
            }
            buildDataFeed();
            Util._txClient=null;
            close();
        }
        Util._txClient=null;
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));

    }

    void pressCancel() {
        close();
    }

    void pressRb() {
        if (!_rb.isChecked()) {
            Util.debugConnct=null;
        } else {
            Util.debugConnct +="Enable connection debug";
        }
    }



    /**
     * This method searches potential data-feeders, and let users select if there are multiple choices
     */
    private void searchServ() {

        int ttl = getTTL();
        String servname = getServName();
        int port = getPort();

        //server infor
        _udpFeedFinder = new UdpFeedFinder();
        _udpFeedInf = null ;

        Util.prtDbg(" connOpt ="+ servname + "  "+ port+ " "+ ttl);

        try {
            _mUdpFeedInf = _udpFeedFinder.findServers(servname, port);  //need to pass server ip and port=30000 || multiple cast server 239.0.0.10
        } catch (IOException e) {
            Util.prtException(e, "\n searchServ() exception - ");
            _udpFeedFinder.closeSocket();
            return;
        }

        if (_mUdpFeedInf==null || _mUdpFeedInf.size()==0 ) {
            Util.prtErr("No server found...");
            return;
        }

        if (_mUdpFeedInf.size()==1){
            _udpFeedInf= _mUdpFeedInf.get(0);
            return;
        }

        //choose server , project, and dsms for users
        List<String> servOpt = new ArrayList<String>();
        for (int i=0; i< _mUdpFeedInf.size(); i ++) {			
            _cbData.addItem(_mUdpFeedInf.get(i).getUserServInf());
        }
        _cbData.setVisible(true);
        toggleUI();
    }

    private void buildDataFeed(){
        Util.prtDbg(" connOpt-buildDataFeed  ="+ " udpFeedFinder="+_udpFeedFinder.getServAddr().getHostName() + "\n _udpFeedInf="+_udpFeedInf.getUDPPort());
        _udpFeed = new UdpFeed( _udpFeedFinder, _udpFeedInf );
    }
    
   

    /**
     * 
     * @param input user's input serv:port
     * 
     * @return
     */
    public UdpFeed reconnectUdpFeeder() {
        _udpFeedFinder.closeSocket();
        _udpFeed.closeSocket();
        _mUdpFeedInf.clear();
        _udpFeedFinder = new UdpFeedFinder();
        _udpFeedInf = null;
        _mUdpFeedInf = new ArrayList<UdpFeedInf>();
        Util.prtDbg(" connOpt-reconnection  ="+ _initServ + "  "+ _port+ " "+ _ttl);

        try {
            _mUdpFeedInf.addAll( _udpFeedFinder.findServers(_initServ, _port) );  //need to pass server ip and port=30000 || multiple cast server 239.0.0.10
        } catch (Exception e) {
            Util.prtException(e, "\n searchServ() exception ");
            _udpFeedFinder.closeSocket();
            _udpFeedFinder = null;
            return null;
        }

        if (_mUdpFeedInf==null || _mUdpFeedInf.size()==0 ) {
            Util.prtDbg(" reconnect--No server found...");
            return null;
        }

        if (_mUdpFeedInf.size()==1){
            _udpFeedInf = _mUdpFeedInf.get(0);
        }
        //mUdpFeedInf. multiple, get the previous selection
        if (_udpFeedInf==null) {
            for (int i=0; i< _mUdpFeedInf.size(); i++) {
                if (_mUdpFeedInf.get(i).getServ().equals(_prevServName)) {
                    _udpFeedInf = _mUdpFeedInf.get(i);
                    break;
                }
            }
        }

        if (_udpFeedInf==null) {
           return null;
        }
         
        //rebuild udpFeed
        Util.prtDbg(" connOpt-reconnect-buildDataFeed  ="+ " udpFeedFinder="+_udpFeedFinder.getServAddr().getHostName() + "\n udpFeedInf="+_udpFeedInf.getUDPPort());
        buildDataFeed();
        return _udpFeed;
    }
} //servDiag-class

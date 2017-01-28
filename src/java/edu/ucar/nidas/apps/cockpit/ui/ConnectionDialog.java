// -*- mode: java; indent-tabs-mode: nil; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
 ** ISFS: NCAR Integrated Surface Flux System software
 **
 ** 2016, Copyright University Corporation for Atmospheric Research
 **
 ** This program is free software; you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License as published by
 ** the Free Software Foundation; either version 2 of the License, or
 ** (at your option) any later version.
 **
 ** This program is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 ** GNU General Public License for more details.
 **
 ** The LICENSE.txt file accompanying this software contains
 ** a copy of the GNU General Public License. If it is not found,
 ** write to the Free Software Foundation, Inc.,
 ** 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 **
 ********************************************************************
*/

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
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QVBoxLayout;

import edu.ucar.nidas.core.UdpConnection;
import edu.ucar.nidas.core.UdpConnInfo;

/**
 * This class provides a UI interface for users to 
 * select an address and port for a data connection.
 */
public class ConnectionDialog extends QDialog
{
    /**
     * User preferred data connection, entered as program arguments
     */
    private String _unicastAddr = "localhost";
    /**
     * Default connection port.
     */
    private int _connPort = 30000;
    /**
     * Default multicast IP
     */
    private String _multicastAddr = "239.0.0.10";;	

    /**
     * Selected address.
     */
    private String _connAddr = null;

    private UdpConnection _udpConnection = null;

    private int _ttl;

    /**
     * Udp data connection information
     */
    private UdpConnInfo _selectedConnection = null;

    private List<UdpConnInfo> _connections = null;

    private QRadioButton _multicast, _unicast;

    private QPushButton _okButton;

    private QLineEdit _portInput, _multicastInput, _unicastInput;

    private QComboBox _ttlInput, _serverSelection;

    private QGroupBox _addressSelectionBox;

    private QGroupBox _serverSelectionBox;

    private QCheckBox _connDebug;

    private boolean _alwaysProvideServerSelection = false;

    private Cockpit _cockpit;

    private boolean _debug = false;

    /**
     * The dialog that provides users with UI interface to perform selection and search of the data connection.
     * 
     * @param cockpit
     * @param modal
     * @param cp
     * @param inputS
     */
    public ConnectionDialog(Cockpit cockpit,
            UdpConnection conn, String addr, int port)
    {
        super(cockpit);
        _cockpit = cockpit;
        _udpConnection = conn;
        _connAddr = addr;
        _unicastAddr = addr;
        _connPort = port;

        setModal(true);

        //create the UI-components
        createUI();
    }

    public boolean getDebug()
    {
        return _debug;
    }

    /**
     * Return the Connection.
     * @return
     */
    public UdpConnInfo getSelectedConnection()
    {
        return _selectedConnection;
    }

    /**
     * Get the connection address from UI
     * @return
     */
    private String getAddressInput()
    {
        if (_unicast.isChecked()) {
            _unicastAddr = _unicastInput.text().trim();
            _connAddr = _unicastAddr;
        } else if (_multicast.isChecked()) {
            _multicastAddr = _multicastInput.text().trim();
            _connAddr = _multicastAddr;
        } 
        return _connAddr;
    }

    public String getAddress()
    {
        return _connAddr;
    }

    /**
     *  Get the user preferred port
     * @return
     */
    private int getPortInput()
    {
        _connPort = Integer.valueOf(_portInput.text().trim());
        return _connPort;
    }

    public int getPort()
    {
        return _connPort;
    }

    private int getTTLInput()
    {
        _ttl = _ttlInput.currentIndex()+1;
        return _ttl;
    }

    public int getTTL()
    {
        return _ttl;
    }

    /**
     * diag-ui creation
     */
    public void createUI()
    {

        setWindowTitle(tr("Data connection"));
        QVBoxLayout mlayout = new QVBoxLayout();

        _addressSelectionBox = new QGroupBox();

        QVBoxLayout addrBoxLayout = new QVBoxLayout();

        // multicast
        QHBoxLayout hlayout = new QHBoxLayout();
        _multicast = new QRadioButton(tr("Multicast"));
        _multicast.clicked.connect(this, "multicastServerRadio()");
        hlayout.addWidget(_multicast);
        _multicastInput = new QLineEdit(_multicastAddr);
        // _multicastInput.setMaximumSize(200, 30);
        // _multicastInput.adjustSize();
        hlayout.addWidget(_multicastInput);
        hlayout.addWidget(new QLabel(tr("TTL")));
        _ttlInput = new QComboBox();
        for (int k = 1; k <= 3; k++) {
            _ttlInput.addItem(String.valueOf(k));
        }
        hlayout.addWidget(_ttlInput);
        // hlayout.addStretch();
        // Alignment qal = new Alignment();
        // qal.set(Qt.AlignmentFlag.AlignLeft);
        addrBoxLayout.addLayout(hlayout);

        // unicast
        hlayout = new QHBoxLayout();
        _unicast = new QRadioButton(tr("Unicast")); 
        _unicast.setChecked(true);
        _unicast.clicked.connect(this, "unicastServerRadio()");
        hlayout.addWidget(_unicast);
        _unicastInput = new QLineEdit(_unicastAddr);
        // _unicastInput.setMaximumSize(200, 30);
        // _unicastInput.adjustSize();
        //_unicastInput.setAlignment(Qt.AlignmentFlag.AlignRight);
        hlayout.addWidget(_unicastInput);
        // hlayout.addWidget(new QLabel());
        // hlayout.addStretch();
        addrBoxLayout.addLayout(hlayout);

        // port 
        hlayout = new QHBoxLayout();
        hlayout.addWidget(new QLabel(tr("Port")));
        _portInput = new QLineEdit(String.valueOf(_connPort));
        // _portInput.setMaximumSize(200, 30);
        // _portInput.adjustSize();
        //_portInput.setAlignment(Qt.AlignmentFlag.AlignRight);
        hlayout.addWidget(_portInput);
        // hlayout.addWidget(new QLabel());
        // hlayout.addStretch();
        //hlayout.setAlignment(qal);
        addrBoxLayout.addLayout(hlayout);

        _addressSelectionBox.setLayout(addrBoxLayout);
        mlayout.addWidget(_addressSelectionBox);

        // qf.setTitle("Data Connection");

        // if more than one server responds, a selection of servers
        _serverSelectionBox = new QGroupBox();
        QHBoxLayout slayout = new QHBoxLayout();
        slayout.addWidget(new QLabel(tr("Server")));
        _serverSelection = new QComboBox();
        slayout.addWidget(_serverSelection);
        _serverSelectionBox.setLayout(slayout);
        _serverSelectionBox.setVisible(false);

        hlayout = new QHBoxLayout();
        hlayout.addWidget(_serverSelectionBox);
        mlayout.addLayout(hlayout);

        _connDebug = new QCheckBox(tr("Log connection debug messages"));
        _connDebug.setChecked(false);
        _connDebug.clicked.connect(this, "connDebug()");
        mlayout.addWidget(_connDebug);

        // ok, cancel buttons
        hlayout = new QHBoxLayout();
        // hlayout.addWidget(new QLabel());
        _okButton = new QPushButton(tr("Search"), this);
        _okButton.clicked.connect(this, "pressOk()");
        hlayout.addWidget(_okButton);
        QPushButton cancel = new QPushButton(tr("Cancel"), this);
        cancel.clicked.connect(this, "pressCancel()");
        hlayout.addWidget(cancel);
        mlayout.addItem(hlayout);

        setLayout(mlayout);
        // setGeometry(400, 300, 400, 300);
        setVisible(true);
        exec();
    }

    void connDebug()
    {
        _debug = _connDebug.isChecked();
        if (_debug) {
            _cockpit.showLog();
            _alwaysProvideServerSelection = true;
        }
        else _alwaysProvideServerSelection = false;
    }

    void multicastServerRadio()
    {
        if (_multicast.isChecked()) _unicast.setChecked(false);
        if (!_multicast.isChecked() && !_unicast.isChecked()) _multicast.setChecked(true);
    }

    void unicastServerRadio()
    {
        if (_unicast.isChecked()) _multicast.setChecked(false);
        if (!_multicast.isChecked() && !_unicast.isChecked()) _unicast.setChecked(true);
    }

    /**
     * Dialog button to search or select a server.
     */
    void pressOk()
    {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        if (_okButton.text().trim().equals(tr("Search"))) {
            search();
            if (!_alwaysProvideServerSelection && _connections.size() == 1) {
                _selectedConnection = _connections.get(0);
                close();
            }
        } else {
            _selectedConnection = _connections.get(_serverSelection.currentIndex());
            close();
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    void pressCancel()
    {
        close();
    }

    /**
     * This method searches potential data connections, and let users select
     * if there are multiple choices
     */
    private void search()
    {

        String addr = getAddressInput();
        int port = getPortInput();
        int ttl = getTTLInput();

        try {
            _connections = _udpConnection.search(addr, port, ttl,
                    _cockpit.getLog(), _debug);
        }
        catch (IOException e) {
            _cockpit.getLog().error(tr("search: ") + e.toString());
            return;
        }

        if (_connections.isEmpty()) {
            _cockpit.getLog().error(tr("search: No server found"));
            _cockpit.status(tr("search: No server found"));
            return;
        }

        if (_connections.size() > 1 ||_alwaysProvideServerSelection) {
            // display connections
            for (UdpConnInfo conn : _connections) {
                _serverSelection.addItem(conn.toString());
            }
            _okButton.setText(tr("Select"));
            _addressSelectionBox.setEnabled(false);
            _serverSelectionBox.setVisible(true);
        }
    }
}

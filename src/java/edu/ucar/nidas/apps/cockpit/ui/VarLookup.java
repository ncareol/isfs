package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QAbstractItemView;
import com.trolltech.qt.gui.QBrush;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QDialog;
import com.trolltech.qt.gui.QFrame;
import com.trolltech.qt.gui.QHBoxLayout;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPalette;
import com.trolltech.qt.gui.QPushButton;
import com.trolltech.qt.gui.QTableWidget;
import com.trolltech.qt.gui.QTableWidgetItem;
import com.trolltech.qt.gui.QTextEdit;
import com.trolltech.qt.gui.QVBoxLayout;

import edu.ucar.nidas.model.Var;


public class VarLookup extends QDialog{

    /**
     * The dialog that provides users with UI interface to 
     * select variables for a new page
     * It provides users with a table to populate the variables, 
     * a search-engine to find desirable variables, 
     * a toggle button to select/deselect vars,
     * a check or uncheck a variable,
     * select whole/deselect whole vars in the current table 
     * @param owner
     */

    /**
     * all the variables from input
     */
    private List<Var> _vars = new ArrayList<Var>(); 

    /**
     * selected variables from users
     */
    private List<Var> _selvars = new ArrayList<Var>(); 

    //variables for class use only
    //private List<QTableWidgetItem> _nameItems = new ArrayList<QTableWidgetItem>();
    //private List<QTableWidgetItem> _statusItems = new ArrayList<QTableWidgetItem>();
    private List<Integer>         _selectedIdxs = new ArrayList<Integer>();
    private QTableWidget _tbl;
    private QTextEdit    _tedit;
    private QBrush _pgreen=new QBrush( QColor.green);
    private QBrush _pblack=new QBrush( QColor.black);
    private String yesstr= "Y";
    private String nostr="N";
    private CockPit _cpit;
    private UIUtil                 _uU          = new UIUtil();


    public VarLookup(QFrame owner, CockPit p, List<Var> vars) {
        setModal(true);
        _vars = _uU.getSortedVars(vars);
        _cpit = p;
        //create the UI-components
        createComp();
        if (_tbl == null) close();
        populateTbl();
        _tbl.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows);
        _tbl.setSelectionMode(QAbstractItemView.SelectionMode.MultiSelection);
        _tbl.setMinimumSize(390, 350);
        _tbl.setColumnWidth(0, 250);
        _tbl.setColumnWidth(1, 70);
        List<String> title = new ArrayList<String>();
        title.add(" Varaibles");
        title.add(" Status");        
        _tbl.setHorizontalHeaderLabels(title);
    }

    public List<Var> getSelVars() {
        return _selvars;
    }

    private void createComp(){
        setWindowTitle("Table to Select Variables");
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));

        //create buttons on right side
        QFrame rfrm = new QFrame();
        rfrm.setMinimumSize(150, 350);
        QVBoxLayout rlayout = new QVBoxLayout();

        // add search and toggle
        rlayout.addWidget(new QLabel());
        QFrame frm = new QFrame();
        QHBoxLayout hl = new QHBoxLayout();
        hl.setMargin(0);
        _tedit= new QTextEdit();
        _tedit.setMaximumSize(120, 30);
        QPalette pl=new QPalette( QColor.green);
        _tedit.setPalette(pl);
        _tedit.setLineWrapColumnOrWidth(200);
        _tedit.textChanged.connect(this, "searchBegin()");
        QLabel rlbl = new QLabel("Search:");
        rlbl.setTextFormat(Qt.TextFormat.RichText);
        hl.addWidget(rlbl);
        hl.addWidget(_tedit);
        frm.setLayout(hl);
        frm.setPalette(pl);
        rlayout.addWidget(frm);
        rlayout.addWidget(new QLabel());
        QPushButton pb =new QPushButton("toggle");
        pb.clicked.connect(this, "toggleSelection()");
        rlayout.addWidget(pb);

        // add all/deselectall
        frm = new QFrame();
        hl = new QHBoxLayout();
        pb= new QPushButton("SelectAll");
        pb.clicked.connect(this, "selectAll()");
        hl.addWidget(pb);
        pb= new QPushButton("DeselectAll");
        pb.clicked.connect(this, "deselectAll()");
        hl.addWidget(pb);
        frm.setLayout(hl);
        rlayout.addWidget(frm);

        // add empty label to save space
        rlayout.addWidget(new QLabel());
        rlayout.addWidget(new QLabel());
        rlayout.addWidget(new QLabel());
        rlayout.addWidget(new QLabel());
        rlayout.addWidget(new QLabel());
        rlayout.addWidget(new QLabel());
        rlayout.addWidget(new QLabel());

        //add ok /cancel
        frm = new QFrame();
        hl = new QHBoxLayout();
        pb= new QPushButton("    Ok    ");
        pb.clicked.connect(this, "pressOk()");
        hl.addWidget(pb);
        pb= new QPushButton("Cancel");
        pb.clicked.connect(this, "pressCancel()");
        hl.addWidget(pb);
        frm.setLayout(hl);
        rlayout.addWidget(frm);

        // create a table
        QHBoxLayout mainlout = new QHBoxLayout();
        _tbl = new QTableWidget(20,2,null);
        rfrm.setLayout(rlayout);
        mainlout.addWidget(_tbl);
        mainlout.addWidget(rfrm);

        setLayout(mainlout);
        setMinimumSize(600, 350);
        show();
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));

    }

    private void populateTbl(){
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        _tbl.clear();
        int len = _vars.size();
        _tbl.setRowCount(len);
        for (int i=0; i<len; i++) {
            Var var = _vars.get(i);
            String name= var.getName();
            QTableWidgetItem ti = new QTableWidgetItem(nostr);
            ti.setTextAlignment(0x0004|0x0008);
            _tbl.setItem(i,0,new QTableWidgetItem(name));
            _tbl.setItem(i,1,ti);
            _selectedIdxs.add(new Integer(i));
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void populateStatusTbl() {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        int len = _tbl.rowCount(); _selectedIdxs.clear();
        for (int i=1; i<=len; i++) {
            _tbl.setRowHidden(i,false);
            _selectedIdxs.add(new Integer(i));
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }


    private void pressCancel() {
        _tbl.clear();
        _selvars=null;
        close();
    }

    private void pressOk() {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        _selvars.clear();
        int len = _tbl.rowCount();
        for (int i=0; i<len; i++) {
            if (_tbl.item(i,1).text().equals(yesstr))  {
                _selvars.add(_vars.get(i));
            }
        }
        if (_selvars.size()> 0){
            _cpit._centWidget.addNewPage(_selvars, _selvars.get(0).getName());
        }
        close();
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void selectAll() {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        int len = _tbl.rowCount();
        for (int i=0; i<len; i++) {
            QTableWidgetItem item= _tbl.item(i,1);
            if (item==null) return;
            if (!_selectedIdxs.contains(new Integer(i))) continue;
            item.setText(yesstr);
            item.setForeground(_pgreen);
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void deselectAll() {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        int len = _tbl.rowCount();
        for (int i=0; i<len; i++) {
            QTableWidgetItem item= _tbl.item(i,1);
            if (item==null) return;
            if (!_selectedIdxs.contains(new Integer(i))) continue;
            item.setText(nostr);
            item.setForeground(_pblack);
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void searchBegin() {
        String str = _tedit.toPlainText().trim();
        int strlen = str.length();
        if (strlen <= 0) {
            populateStatusTbl();
            return;
        }
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        _selectedIdxs.clear();
        int len = _vars.size();
        for (int i =0; i<len; i++ ) {
            String vname = _vars.get(i).getName();
            if (vname.length()<str.length()) {
                _tbl.setRowHidden(i,true );
            } else  {
                String sn = vname.substring(0,strlen);
                if (sn.equals(str)){
                    _tbl.setRowHidden(i,false );
                    _selectedIdxs.add(new Integer(i));
                } else {
                    _tbl.setRowHidden(i,true );
                }
            }
        } //for
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

    private void toggleSelection() {
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        int len = _tbl.rowCount();
        for (int i =0; i<len; i++ ) {
            QTableWidgetItem item= _tbl.item(i,1);
            if (item==null) return;
            if (item.isSelected() && _selectedIdxs.contains(new Integer(i))) {
                if (item.text().equals(yesstr) ) {
                    item.setText(nostr);
                    item.setForeground(_pblack);
                }
                else  {
                    item.setText(yesstr);
                    item.setForeground(_pgreen);
                }
            } 
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }
} // eof-class

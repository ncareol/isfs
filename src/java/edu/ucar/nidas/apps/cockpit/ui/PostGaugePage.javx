package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.List;

import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QWidget;

import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;

public class PostGaugePage extends GaugePage {

    List<Var> _vars = new ArrayList<Var>();

    public  PostGaugePage( QWidget p, List<Var> vars , String name) {
        super.setParent(p);
        _vars = vars;
        _name = name;
        _primary = false;
        createUIs();
    }

    public void addPlots()
    {
        if (_vars==null || _vars.size()<1){
            Util.prtErr("Pwidget-addPlots  _vars ==null");
            return;
        }
        setCursor(new QCursor(Qt.CursorShape.WaitCursor));
        synchronized (_vars){
            Util.prtDbg("addPlots-begin");
            for (int j = 0; j < _vars.size(); j++) {
                Var var = _vars.get(j);
                if ( var.getLength()!=1) return;   //for nearly all the cases, the variable only has one data, except 1d-probe. Ignore it for now
                Gauge g = createGauge(var ); 
                if (g==null) continue;
                GaugeDataClient gdc = CentTabWidget._varToGdc.get(var);
                synchronized (gdc) {
                    gdc.addGauge(g);
                }
            }
        }
        setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
    }

}

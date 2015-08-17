package edu.ucar.nidas.apps.cockpit.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.w3c.dom.Document;

import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.QTimer;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.Qt.MouseButton;
import com.trolltech.qt.gui.QAction;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QColorDialog;
import com.trolltech.qt.gui.QCursor;
import com.trolltech.qt.gui.QInputDialog;
import com.trolltech.qt.gui.QLineEdit;
import com.trolltech.qt.gui.QMenu;
import com.trolltech.qt.gui.QMouseEvent;
import com.trolltech.qt.gui.QStackedLayout;
import com.trolltech.qt.gui.QTabWidget;
import com.trolltech.qt.gui.QSizePolicy.Policy;

import edu.ucar.nidas.apps.cockpit.model.DataDescriptor;
import edu.ucar.nidas.apps.cockpit.model.config.CockpitConfig;
import edu.ucar.nidas.apps.cockpit.model.config.UserConfig;
import edu.ucar.nidas.apps.cockpit.model.config.TabPageConfig;
import edu.ucar.nidas.apps.cockpit.model.config.PlotConfig;
import edu.ucar.nidas.model.Dsm;
import edu.ucar.nidas.model.Sample;
import edu.ucar.nidas.model.Var;
import edu.ucar.nidas.util.Util;
/**
 * This class is the center tab-widget which controls all the tab-gauge-pages
 * It tracks the current tabs and mediate between the cockpit main with the other gauge-pages
 * Encapsulate all cent-tab-widget code in the class to simplify the cockpit main program 
 * 
 * @author dongl
 *
 */
public class CentTabWidget extends QTabWidget {

    /**
     * copy of a parent
     */
    CockPit _parent=null;
    /**
     * user-config-keeper 
     */


    //data and clients
    /**
     * All the samples from the data-descriptor
     */
    List<Sample>    _samples = new ArrayList<Sample>(); //for all samples
    /**
     * All the dsms from the data-feed
     */
    List<Dsm> _dsms = new ArrayList<Dsm>();

    /**
     * All the plot-pages in the Cockpit
     */
    List<GaugePage> _gaugePages = new ArrayList<GaugePage>(0);
    /**
     * A list of var-to-gaugedataclient 
     *      one gaugedataclient-to-multiplots
     */
    public static HashMap<Var, GaugeDataClient> _varToGdc = new HashMap<Var,GaugeDataClient>();

    static HashMap<String, GaugePage> _nameToGaugepage = new HashMap<String,GaugePage>();

    /**
     * index of the previous tab-page in centWidget
     */
    int _pidx =-1;

    QStackedLayout _stacked= new QStackedLayout();; //stackedlayout for none-tab-widget

    UIUtil _uU = new UIUtil();

    private QTimer _tm; //used at beginning to cycle pages (one time only)
    private QTimer _ucftm; //used at beginning to check user config (one time only)
    
    String _name;

    /**
     * auto-cycle tab-pages, default is 10 seconds
     */
    QTimer _cycleTm;
    int _cycleInt = 10;

    /**
     * user-configuration
     */
    UserConfig _saveconfig = new UserConfig();;
    UserConfig _openconfig = new UserConfig();

    /***********************************************/
    public CentTabWidget(CockPit p) {
        _parent=p;
        connectSlotsByName();
        setLayout(_stacked);
        currentChanged.connect(this,   "pageChanged()");  
        _cycleTm = new QTimer();
        _cycleTm.timeout.connect(this, "cycleTimeout()");
    }

    public void createPrimaryGauges() {

        if (_dsms==null || _dsms.size()<1) return;
        // create gauge-pages
        GaugePage gp=null;
        for (int i=0; i<_dsms.size(); i++) {
            Dsm dsm= _dsms.get(i);
            if (dsm==null ) continue;
            if ( dsm.getSamples()==null ||dsm.getSamples().isEmpty()) {
                Util.prtErr("dsm "+dsm.getName() + " contains empty samples. \nSkip creating plots ");
                continue;
            }
            Util.prtDbg("create-"+dsm.getName()+"-page");
            gp = new GaugePage(this, dsm.getSamples(), dsm.getName());
            String color = "gdefBColor";
            if ((i%4)!=0)  color += (i%4+1); //skip 0
            
            gp.setBGColor(CockPit._orderToColor.get(color));
            if (gp==null) {
                Util.prtDbg("Errors occured in creating "+dsm.getName()+" page");
                continue;
            }
            gp.setGeometry(PageGeometry.x,  PageGeometry.y, PageGeometry.w, PageGeometry.h); 
            _gaugePages.add(gp);
            _nameToGaugepage.put(dsm.getName(), gp);
            setCursor(new QCursor(Qt.CursorShape.WaitCursor));
            gp.createDataClients();
            setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
        }
        //add g-pages to layout # add g-pages later to avoid empty dsm
        if (_gaugePages == null || _gaugePages.isEmpty()) return;
        GaugePage gpp=_gaugePages.get(0);
        if (_gaugePages.size()==1) {
            _stacked.addWidget(gpp);
        } else {
            for (int j=0; j< _gaugePages.size(); j++) {
                gpp=_gaugePages.get(j); 
                addTab(gpp, gpp.getName());
            }
        }
        setCurrentWidget(_gaugePages.get(0));
        _tm = new QTimer();
        _tm.timeout.connect(this, "timeout()");
        _tm.start(1000);
        if (_parent.getUserConfig()!=null){
            _ucftm = new QTimer();
            _ucftm.timeout.connect(this, "ucfTimeout()");
            _ucftm.start(100); 
        }
    }

    public GaugePage getCurrentGaugePage() {
        if (_gaugePages.size()<=0) return null;
        if (_gaugePages.size()==1) return (GaugePage)_gaugePages.get(0); //because the first page is in _stacked layout, not th _centWidget
        return (GaugePage)currentWidget();
    }

    public List<Sample> getSamps() {
        return _samples;
    }

    public void setName(String name) { _name = name; }

    public String getName() {return _name; }
    
    public CockPit getParent() {
        return _parent;
    }

    public List<GaugePage> getGaugePages(){
        return _gaugePages;
    }

    public HashMap<String, GaugePage> getNameToGaugePage() {
        return _nameToGaugepage;
    }

    public  void closeTab()  {
        if (_gaugePages.size()<=1) return;
        int idx= currentIndex();
        GaugePage gp=(GaugePage)currentWidget();
        if (gp==null) return;
        if (gp.getPrimary()) {
            _parent.statusBar().showMessage("Cannot delete a primary page, Cockpit preserved it", 10000); //10 sec
            return;
        }
        _gaugePages.remove(gp);
        _nameToGaugepage.remove(gp._name);
        removeTab(idx);
        gp.destroyWidget(true);
        setCurrentIndex(idx-1);
        setCurrentWidget(_gaugePages.get(idx-1));
        checkFirstPageTab(false);
    }

    /**
     * Change the plot-page policy to resize 
     */
    public void toggleFixedGaugeSize(Policy policy)
    {
        if (policy == Policy.Preferred) if (Util.confirmMsgBox("ToVarying will loss all the history data! ", "Toggle Resize")== Util.RetAbort) return;
        getCurrentGaugePage().toggleFixedGaugeSize(policy);
    }

    /**
     * Change all pages' policy to resize 
     */
    public void gtoggleFixedGaugeSize(Policy policy)
    {
        if (policy == Policy.Preferred) if (Util.confirmMsgBox("ToVarying will loss all the history data! ", "Toggle Resize")== Util.RetAbort) return;
        for (int i =0; i<_gaugePages.size(); i++) {
            _gaugePages.get(i).toggleFixedGaugeSize(policy);
        }
        _tm.start(15000);
    }

    /**
     * Clean up history for all plots in the current page
     */
    public void cleanupHistory() {
        if (Util.confirmMsgBox("Processing \"cleanupHistory\" will loss all the history data! ", "Clean History")== Util.RetAbort) return;
        getCurrentGaugePage().cleanupHistory();
    }
    /**
     * Clean up history for all plots in all pages
     */
    public void gcleanupHistory() {
        if (Util.confirmMsgBox("Processing \"cleanupHistory\" will loss all the history data! ", "Clean History")== Util.RetAbort) return;
        for (int i =0; i<_gaugePages.size(); i++) 
            _gaugePages.get(i).cleanupHistory();
    }

    /**
     * Scale each plot in the active page based on its max-min in the span 
     */
    public void gautoScalePlots() {
        getCurrentGaugePage().gautoScalePlots(true);
    }

    /**
     * Scale each plot in all page based on its max-min in the span 
     */
    public void ggautoScalePlots() {
        GaugePage gp = (GaugePage)currentWidget();
        for (int i =0; i<_gaugePages.size(); i++){ 
            _gaugePages.get(i).gautoScalePlots(true);
        }
        setCurrentWidget(gp);
    }

    /**
     * Color each plot in the active page with new color  
     */
    public void colorCurrent() {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getCColor());
        if (c.value()==0) return;
        getCurrentGaugePage().colorCurrent(c);
    }
    /**
     * Color each plot in all pages with new color  
     */
    public void gcolorCurrent() {
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getCColor());
        if (c.value()==0) return;
        for (int i =0; i<_gaugePages.size(); i++) 
            _gaugePages.get(i).colorCurrent(c);
    }

    /**
     * Color the history image of each plot in the active page with new color  
     */
    public void colorHistory() {
        if (Util.confirmMsgBox("Processing \"colorHistory\" will loss all the history data! ", "Color History")== Util.RetAbort ) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getHColor());
        if (c.value()==0) return;
        getCurrentGaugePage().colorHistory(c);
    }

    /**
     * Color the history image of each plot in the active page with new color  
     */
    public void gcolorHistory() {
        if (Util.confirmMsgBox("Processing \"colorHistory\" will loss all the history data! ", "Color History")== Util.RetAbort ) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getHColor());
        if (c.value()==0) return;
        for (int i =0; i<_gaugePages.size(); i++) 
            _gaugePages.get(i).colorHistory(c);
    }

    /**
     * Color the back-ground of each plot in the active page with new color  
     */
    public void colorBackGround() {
        if (Util.confirmMsgBox("Processing \"colorBackGround\" will loss all the history data! ", "Color Background")== Util.RetAbort) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getBGColor());
        if (c.value()==0) return;
        getCurrentGaugePage().colorBackGround(c); 
    }

    /**
     * Color the back-ground of each plot in all pages with new color  
     */
    public void gcolorBackGround() {
        if (Util.confirmMsgBox("Processing \"colorBackGround\" will loss all the history data! ", "Color Background")== Util.RetAbort) return;
        QColor c = QColorDialog.getColor();//((GaugePage)currentWidget()).getBGColor());
        if (c.value()==0) return;
        for (int i =0; i<_gaugePages.size(); i++) 
            _gaugePages.get(i).colorBackGround(c); 
    }


    /**
     * change the gauge-time-span-x_axis for every gauge page and its plots 
     * set the time-range in milli-second in x_axis
     */
    public void changePlotTimeMSec () {
        if (Util.confirmMsgBox("Changing-plot-time-range will loss all the history data! ", "Change time span")== Util.RetAbort) return;

        int oldtm= getCurrentGaugePage().getGaugeTimeMSec();
        NewTimeMSec tmDlg = new NewTimeMSec(null, oldtm);
        int newtm = tmDlg.getNewTimeMSec();
        if (newtm <= 0 || oldtm == newtm) return;

        for (int i=0; i<_gaugePages.size(); i++) {
            _gaugePages.get(i).setGaugeTimeMSec(newtm);
        }
    }

    /**
     * change the gauge-time-span-x_axis for the current gauge page and its plots 
     * set the time-range in milli-second in x_axis
     */
    public void changeSinglePlotTimeMSec () {
        if (Util.confirmMsgBox("Changing-plot-time-range will loss all the history data! ", "Change time span")== Util.RetAbort) return;

        int oldtm= getCurrentGaugePage().getGaugeTimeMSec();
        NewTimeMSec tmDlg = new NewTimeMSec(this, oldtm);
        int newtm = tmDlg.getNewTimeMSec();
        if (newtm <= 0 || oldtm == newtm) return;
        getCurrentGaugePage().setGaugeTimeMSec(newtm);
    }

    public void setNodataTimeout() {

        int oldtm= getCurrentGaugePage().getGaugeNoDataTmout();
        NoDataTimeout tmDlg = new NoDataTimeout(this, oldtm);
        int newtm = tmDlg.getNewTimeSec();
        if (newtm <= 0 || oldtm == newtm) return;

        for (int i=0; i<_gaugePages.size(); i++) {
            _gaugePages.get(i).setGaugeNoDataTimeout(newtm);
        }
    }

    public void setSingleNodataTimeout() {

        int oldtm= getCurrentGaugePage().getGaugeNoDataTmout();
        NoDataTimeout tmDlg = new NoDataTimeout(null, oldtm);
        int newtm = tmDlg.getNewTimeSec();
        if (newtm <= 0 || oldtm == newtm) return;
        getCurrentGaugePage().setGaugeNoDataTimeout(newtm);
    }

    public void pageChanged () {
        int index = currentIndex();
        if (index==_pidx) {
            Util.prtDbg("curidx=previdx == "+_pidx);
            return;
        }

        //set prev-page to be fixed-size 
        if (_pidx!=-1){
            if (_gaugePages.size()>= (_pidx+1)){
                GaugePage prevG = _gaugePages.get(_pidx);
                if (prevG.getPolicy()==Policy.Preferred) prevG.toggleFixedGaugeSize(Policy.Fixed);
            }
        }
        //sync current-page-policy with gfixedSize
        if (getCurrentGaugePage()!=null)  syncCurrentSizePolicy(getCurrentGaugePage().getPolicy());    
        //sign new id
        _pidx = index;   
    }

    /**
     * sync current page size policy with the menu
     * @param policy
     */
    public void syncCurrentSizePolicy(Policy policy) {

        boolean flag =false;
        if (policy==Policy.Fixed) flag=true;
        _parent.syncSizePolicy(flag);
    }

    private void checkFirstPageTab(boolean addTab) {
        //set first page
        if (_gaugePages.size()!=1) return;
        GaugePage p= _gaugePages.get(0);

        if (addTab ) {
            _stacked.removeWidget(p);
            addTab(p, p._name);
            return; 
        } 
        removeTab(0);
        _stacked.addWidget(p);
    }

    public GaugePage addNewPage(List<Var> vars, String pname) {
        if (vars.isEmpty()) return null;

        if (_nameToGaugepage.get(pname)!=null) pname += "+";
        PostGaugePage pgp = new PostGaugePage(this, vars, pname);
        //pgp.setPrimary(false); //should be in constructor
        pgp.addPlots();
        //pgp.setPrimary(false);        
        checkFirstPageTab(true);
        _gaugePages.add(pgp);
        _nameToGaugepage.put(pname, pgp);
        addTab(pgp, pgp._name);
        setCurrentWidget(pgp);
        setCurrentIndex(_gaugePages.size()-1);
        syncCurrentSizePolicy(pgp.getPolicy());
        update();      
        return pgp;
    }

    public void addVariablePage() {
        GaugePage gp = getCurrentGaugePage();
        if (gp==null) return;
        if (gp.getPolicy()==Policy.Preferred) gp.setAllPolicy(Policy.Fixed);
        ArrayList<Var> vars = _uU.getSortedVars(gp._gauges);
        if (vars.isEmpty()) return;
        String pname = gp._name+"Var";
        if (_nameToGaugepage.get(pname)!=null) pname += "+";

        PostGaugePage pgp = new PostGaugePage(this, vars, pname);
        pgp.addPlots();
        checkFirstPageTab(true);
        _gaugePages.add(pgp);
        _nameToGaugepage.put(pname, pgp);
        addTab(pgp, pgp._name);
        setCurrentWidget(pgp);
        setCurrentIndex(_gaugePages.size()-1);
        syncCurrentSizePolicy(pgp.getPolicy());
        update();        
    }

    public void sortVariable() {
        GaugePage gp = getCurrentGaugePage();
        if (gp==null) return;
        ArrayList<Gauge> gs=gp.getPlots();
        if (gs.size()<=1) return;
        if (gp.getPolicy()==Policy.Preferred) gp.toggleFixedGaugeSize(Policy.Fixed);
        ArrayList<Gauge> newgs=_uU.getSortedGauges(gs);
        gp.resetGaugeOrder(newgs);
        update();
    }

    public void gsortVariable() {
        for (int i=0; i< _gaugePages.size(); i++) {
            GaugePage gp = _gaugePages.get(i);
            if (gp==null) continue;
            ArrayList<Gauge> gs=gp.getPlots();
            if (gs.size()<=1) return;
            if (gp.getPolicy()==Policy.Preferred) gp.toggleFixedGaugeSize(Policy.Fixed);
            ArrayList<Gauge> newgs=_uU.getSortedGauges(gs);
            gp.resetGaugeOrder(newgs);
            update();
        }
    }

    public void  sortHeight() {
        GaugePage gp = getCurrentGaugePage();
        if (gp==null) return;
        ArrayList<Gauge> gs=gp.getPlots();
        if (gs.size()<=1) return;
        if (gp.getPolicy()==Policy.Preferred) gp.toggleFixedGaugeSize(Policy.Fixed);
        ArrayList<Gauge> newgs=_uU.getSortedGaugesByHeight(gs);
        if (newgs != null || newgs.size()>1) gp.resetGaugeOrder(newgs);
        update();
    }

    public void  gsortHeight() {
        for (int i=0; i< _gaugePages.size(); i++) {
            GaugePage gp = _gaugePages.get(i);
            if (gp==null) continue;
            ArrayList<Gauge> gs=gp.getPlots();
            if (gs.size()<=1) return;
            if (gp.getPolicy()==Policy.Preferred) gp.toggleFixedGaugeSize(Policy.Fixed);
            ArrayList<Gauge> newgs=_uU.getSortedGaugesByHeight(gs);
            //gp.resetGaugeOrder(newgs);
            if (newgs != null || newgs.size()>1) gp.resetGaugeOrder(newgs);
            update();
        }
    }

    public void addHeightPage(){
        GaugePage gp = getCurrentGaugePage();
        if (gp==null) return;
        if (gp.getPolicy()==Policy.Preferred) gp.setAllPolicy(Policy.Fixed);
        ArrayList<Var> vars = _uU.getSortedVarsByHeight(gp._gauges);
        if (vars.isEmpty()) return;

        String pname = gp._name+"Ht";
        if (_nameToGaugepage.get(pname)!=null) pname += "+";

        PostGaugePage pgp = new PostGaugePage(this, vars, pname);
        pgp.addPlots();
        // pgp.setPrimary(false);   //should be in constructor     
        checkFirstPageTab(true);
        _gaugePages.add(pgp);
        _nameToGaugepage.put(pname, pgp);
        setCurrentWidget(pgp);
        setCurrentIndex(_gaugePages.size()-1);
        syncCurrentSizePolicy(pgp.getPolicy());
        update();        
    }


    /**
     * This method gets a xml document, and parses it to get data-descriptors
     *  
     * @param doc
     */
    public void setSampsFromDataDescriptor(Document doc) {
        if (_dsms !=null && !_dsms.isEmpty()) { //this is the case of reconnection
            checkSamps(doc); 
            return;
        }
        synchronized (this){
            _dsms=getDsms(doc);
            if (_dsms==null || _dsms.isEmpty()) return;

            setCursor(new QCursor(Qt.CursorShape.WaitCursor));
            //parse the xml and walk through the elements
            ArrayList<Var> allVars = new ArrayList<Var>();
            for (int i = 0; i < _dsms.size(); i++) {
                Dsm dsm = _dsms.get(i);
                List<Sample> samples = dsm.getSamples();
                if (samples==null || samples.isEmpty()) continue;
                for (int j = 0; j < samples.size(); j++) {
                    Sample samp = samples.get(j);
                    List<Var> vars = samp.getVars();
                    if (vars==null || vars.isEmpty()) continue;
                    allVars.addAll(vars);
                }
                _samples.addAll(samples);
            }
            setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
        }       
    }

    private void checkSamps(Document doc) {
        synchronized (this){
            List<Sample>  tmpsamps = new ArrayList<Sample>(); //for all samples
            List<Dsm>   tmpdsms = getDsms(doc);
            if (tmpdsms==null || tmpdsms.isEmpty()) return;

            //parse the xml and walk through the elements
            setCursor(new QCursor(Qt.CursorShape.WaitCursor));
            ArrayList<Var> allVars = new ArrayList<Var>();
            for (int i = 0; i < tmpdsms.size(); i++) {
                Dsm dsm = tmpdsms.get(i);
                List<Sample> samples = dsm.getSamples();
                if (samples==null || samples.isEmpty()) continue;
                for (int j = 0; j < samples.size(); j++) {
                    Sample samp = samples.get(j);
                    List<Var> vars = samp.getVars();
                    if (vars==null || vars.isEmpty()) continue;
                    allVars.addAll(vars);
                }
                tmpsamps.addAll(samples);
            }
            setCursor(new QCursor(Qt.CursorShape.ArrowCursor));
        }

    }

    private List<Dsm> getDsms(Document doc) {
        DataDescriptor cpxml = new DataDescriptor(doc);  
        cpxml.walkSites();
        return cpxml.getAllDsms();
    }

    static class PageGeometry {
        static public int x= 350, y=250, w=1000, h=700;
    }

    public boolean isAnyPlot() {
        if ( _gaugePages==null || _gaugePages.get(0)==null || _gaugePages.get(0)._gauges.size()<1) return false;
        else return true;
    }

    public void mouseReleaseEvent(QMouseEvent pEvent)
    {
        if (pEvent.button()==MouseButton.RightButton)
        {
            QMenu pMenu = new QMenu("");
            QMenu option = pMenu.addMenu("RenamePage");
            option.addAction("&RenamePage", this, "renamePage()");
            int xmouse = pEvent.globalX();
            int ymouse = pEvent.globalY();
            option.popup(new QPoint(xmouse, ymouse) );    
        }
    }

    public  void saveUserConfig() {
        synchronized (this){
            _saveconfig.createCockpitConfig(this);
            _saveconfig.writeUserConfig();
        }
    }

    public void openUserConfig() {
        synchronized (this){
            _openconfig.readUserConfig(true);
            applyUserConfig(_openconfig.getCockpitConfig()); //or the method with fname
        }
    }


    public void openUserConfig(String ucf) {
        _openconfig.setConfigName(ucf);
        synchronized (this){
            _openconfig.readUserConfig(false);
            applyUserConfig(_openconfig.getCockpitConfig()); //or the method with fname
        }
    }

    public void rescaleGaugesInTab() {

        Gauge g = getCurrentGaugePage()._gauges.get(0);

        RescaleDialog rd = new RescaleDialog( g._ymax, g._ymin, geometry().width()/3, geometry().height()/3) ;
        if (!rd.getOk())  return;
        if (rd.getMax() <=rd.getMin()) {
            Util.prtErr("Y-axis-Max is smaller than Y-axis-Min");
            return;
        }

        List<Gauge> gs = getCurrentGaugePage()._gauges;
        for (int i = 0; i < gs.size(); i++) 
            gs.get(i).changeYMaxMin(rd.getMax(), rd.getMin()); 
    }


    /**
     * auto cycle tabs based on users' chosen time-interval 
     */
    private void autoCycleTabs(){
        synchronized(this) {
            QAction at = _parent._gsetup.actions().get(2);
            String tx = at.text();
            if (tx.equals("AutoCycleTabs")) {
                at.setText("StopCycleTabs");
                NoDataTimeout tmDlg = new NoDataTimeout(null, 10);
                tmDlg.setWindowTitle("Enter Prefered Cycle Time");
                _cycleInt = tmDlg.getNewTimeSec();
                if (_cycleInt <= 0 ) return;
                _cycleTm.start(_cycleInt*1000); 
                _parent._cpConn.getStatusBarClient().receive("Auto_cycle_tabs every "+ _cycleInt+ " seconds", -1);
            } else {
                at.setText("AutoCycleTabs");
                _cycleTm.stop();
                _parent._cpConn.getStatusBarClient().receive("Stop_cycle_tabs", 10000);
            }
        }
    }


    private void renamePage(){
        String text = QInputDialog.getText(this,
                "Get Page Name", "Enter a new name:", QLineEdit.EchoMode.Normal,"");
        if ( text!=null  ) {
            setTabText(currentIndex(),text);
        } 
    }

    /**
     * timer to set current page, and auto-scale. Only one time when the program starts. 
     */
    private void timeout() {
        _tm.stop();
        GaugePage gp=(GaugePage)currentWidget();
        for (int i=0; i<_gaugePages.size(); i++ ) {
            GaugePage p = _gaugePages.get(i);
            setCurrentWidget(p);
        }
        setCurrentWidget(gp);
       
    }

    /**
     * timer to set user config, and set current, only onetime when the porgram starts 
     */
    private void ucfTimeout() {
        _ucftm.stop();
        if (_parent.getUserConfig()!=null) {
            openUserConfig(_parent.getUserConfig());
        }    
        GaugePage gp=(GaugePage)currentWidget();
        for (int i=0; i<_gaugePages.size(); i++ ) {
            GaugePage p = _gaugePages.get(i);
            setCurrentWidget(p);
        }
        setCurrentWidget(gp);
    }
   
    private void cycleTimeout(){

        if (_cycleInt<=0) {
            _cycleTm.stop();
        } else {
            if (count() > 0) setCurrentIndex((currentIndex() + 1) % count());
        }
    }

    private void applyUserConfig(CockpitConfig cconf) {
        if (cconf==null ) {
           // Util.prtErr("Error: Userconfig-apply get cconf is null.");
            return;
        }
        synchronized (this){
            setWindowTitle(cconf.getName());
            // System.out.println("cconf-apply "+cconf.getTabPageConfig().size()+ "   "+cconf.getTabPageConfig().get(0).getName());
            List<TabPageConfig> tps = cconf.getTabPageConfig();
            for (int i=0; i<tps.size(); i++){
                TabPageConfig tp = tps.get(i);
                GaugePage gp = _nameToGaugepage.get(tp.getName());
                if (gp == null ){
                    List<Var> vars = abstractVars(tp);
                    if (vars!=null && vars.size()>0) {
                        gp = addNewPageFromConfig(tp, vars, tp.getName());
                    }
                } 
                if (gp==null) return;
                //else {    
                gp.setWindowTitle(tp.getName());
                gp.resize(tp.getSize()[0], tp.getSize()[1]);
                gp.setPrimary(tp.getPrm());

                List<PlotConfig> pcs = tp.getUIVars();
                for (int j=0; j<pcs.size(); j++) {
                    PlotConfig plotc = pcs.get(j);
                    Gauge g = gp.getNameToGauge().get(plotc.getName());
                    if (g!=null){
                        /* if (j==0) {
                                System.out.println(j+"plotc-apply= "+plotc.getName() + " g-name= "+g.getName() );
                                System.out.println( plotc.getCColor()+ " "+plotc.getHColor()+ " "+plotc.getBGColor() );
                            }*/
                        if (!g._label.equals(plotc.getName()) || (g.getYMax()!=plotc.getMax()) || (g.getYMin()!=plotc.getMin())){
                            g._label = plotc.getName();
                            g.changeYMaxMin(plotc.getMax(),plotc.getMin());
                        }
                        if (!g.getCColor().equals(new QColor(plotc.getCColor()))){ g._noDataPaint=false; g.setCColor(new QColor(plotc.getCColor()));}
                        if (!g.getHColor().equals(new QColor(plotc.getHColor()))) {g._noDataPaint=false; g.setHColor(new QColor(plotc.getHColor()));}
                        if (!g.getBGColor().equals(new QColor(plotc.getBGColor()))) {g._noDataPaint=false; g.setBGColor(new QColor(plotc.getBGColor()));}
                        if (plotc.getNoDataTm()!= g.getNoDataTmout())   g.setNoDataTmout(plotc.getNoDataTm());
                        if (plotc.getplotTmRange()!= g.getGaugeTimeMSec()) g.setNewTimeMSec(plotc.getplotTmRange());
                    } else { //TODO add new gauge or not
                        //createOneDataClient(gp.gets, plotc.getVar());
                    }
                }
                //}
            }
        }
    }

    private GaugePage addNewPageFromConfig(TabPageConfig tp, List<Var> vars, String name) {
        if (vars==null) {
            Util.prtErr("addNewPageFromConfig vars is null");
            return null;
        }
        List<Var> lvar = new ArrayList<Var>();
        for (int i=0; i< vars.size(); i++) {
            String vname = vars.get(i).getName();
            Iterator <Var> it = _varToGdc.keySet().iterator();

            while (it.hasNext()) {
                Var v = it.next();
                if (v.getName().equals(vname)) {
                    lvar.add(v);
                   // System.out.println(" lvar ="+v.getName());
                    break;
                }
            }
        }
        if (lvar.size()<1){
            Util.prtErr("addNewPageFromConfig Lvar-gauge is null");
            return null;
        }

        GaugePage gp = addNewPage(lvar, name);
        if (gp==null){
            Util.prtErr("addNewPage is null");
            return null;
        }

        return gp;
    }


    private List<Var> abstractVars(TabPageConfig tp) {
        List<PlotConfig> plotcs = tp.getUIVars();
        if (plotcs==null || plotcs.size()<=0) return null;
        List<Var> vars = new ArrayList<Var>(0);
        for (int i=0; i< plotcs.size(); i++){
            String name = plotcs.get(i).getName();
            vars.add(plotcs.get(i).getVar());
        }

        return vars;
    }
} //eof-cent-tab-widget class

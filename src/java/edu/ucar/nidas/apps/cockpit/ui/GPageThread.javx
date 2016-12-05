package edu.ucar.nidas.apps.cockpit.ui;




import com.trolltech.qt.gui.QApplication;

public class GPageThread implements Runnable{

	private 
	GaugePage _pw;
	Gauge _g;
		
	GPageThread (GaugePage g) {
		_pw = g;
		_g=null;
	}
	
	public void setGauge(Gauge g)
	{
		_g=g;
		QApplication.invokeLater(this);
	}
	
	public GaugePage getPWidget() {
		return _pw;
	}
	
	public void run() 
	{
		if (_g==null) return;
		_pw.removeGaugeFromWidget(_g);		
	}
	
}

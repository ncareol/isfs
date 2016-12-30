package edu.ucar.nidas.model;

public interface Log
{
    /**
     * Send a debug message to the log.
     */
    public void debug(String msg);

    public void info(String msg);

    public void error(String msg);

    /**
     * Clear the log, meant for log displays.
     */
    public void clear();
}

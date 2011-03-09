// -*- mode: C++; indent-tabs-mode: nil; c-basic-offset: 4; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
    Copyright 2005 UCAR, NCAR, All Rights Reserved

    $LastChangedDate$

    $LastChangedRevision$

    $LastChangedBy$

    $HeadURL$
 ********************************************************************

 Adjust timetags on nidas samples from sensors which have a constant sampling rate,
 by doing a least squares fit to the linear relationship between x=record number
 and y=timetag.

 If the input data has been sorted after it was sampled and time tagged,
 and the original timetags were bad enough such that the sequence of
 samples from a sensor were re-ordered, then this algorithm can
 reorder the data if it contains CSAT3 sonic anemometer samples,
 containing an internal sequence number, 0-63.
 Using the sequence number this program recognizes folds
 in the sequence of CSAT3 samples, and makes an attempt to unfold
 the time series of samples, and then generates corrected timetags
 from the least squares fit.  Timetags of other non-CSAT3 samples
 within the sample stream are then corrected using the timetag
 correction of neighboring CSAT3 samples.
*/

#include <list>
#include <map>

#include <nidas/core/Sample.h>
#include <nidas/core/SampleSorter.h>

/** forward declarations */
class TT_Adjust;

/**
 * Sums to save for gsl least squares.
 */
struct gsl_fit_sums
{
    double x;
    double y;
    double xx;
    double xy;
    double c1;
    double d2;
    size_t n;
    gsl_fit_sums(): x(0),y(0),xx(0),xy(0),c1(0),d2(0),n(0) {}
    void zero() { x = y = xx = xy = c1 = d2 = 0; n = 0; }
};

/** information to save with each data sample */
struct samp_save {

    const nidas::core::Sample* samp;

    /**
     * sample number from its DSM
     */
    long long dsmSampleNumber;

    samp_save() : samp(0),dsmSampleNumber(0) {}

};

class CSAT3Fold
{
public:
    CSAT3Fold(): _firstTime(0),_lastTime(0),_firstSeq(-1),_lastSeq(-1) {}

    void addSample(const nidas::core::Sample* samp,int cseq, long long dsmSampleNumber);

    bool empty() const { return _samples.empty(); }

    void append(CSAT3Fold& fold)
    {
        _samples.splice(_samples.end(),fold.getSamples());
        _lastTime = fold.getLastTime();
        _lastSeq = fold.getLastSeq();
    }

    std::list<samp_save>& getSamples() { return _samples; }

    nidas::core::dsm_time_t getFirstTime() const { return _firstTime; }
    nidas::core::dsm_time_t getLastTime() const { return _lastTime; }

    int getFirstSeq() const { return _firstSeq; }
    int getLastSeq() const { return _lastSeq; }

private:

    std::list<samp_save> _samples;

    /**
     * Timetag of first sample in fold.
     */
    nidas::core::dsm_time_t _firstTime;

    /**
     * Timetag of last sample in fold.
     */
    nidas::core::dsm_time_t _lastTime;

    int _firstSeq;

    int _lastSeq;
};

class CSAT3Sensor
{
public:
    CSAT3Sensor(TT_Adjust* adjuster, nidas::core::dsm_sample_id_t id,double rate);

    void addSample(const nidas::core::Sample* s, long long dsmSampleNumber);

    int spliceAbandonedFolds(nidas::core::dsm_time_t tt);

    int spliceAllFolds();

    size_t fitAndOutput();

    static int getSequenceNumber(const nidas::core::Sample* samp);

    const static int MAJOR_DATA_GAP = 30 * USECS_PER_SEC;

    // CSAT3 folds older than this are considered abandoned
    const static int OLD_FOLD_MAX_DT = 5 * USECS_PER_SEC;

    double getRate() const { return _rate; }

    void setRate(double val) { _rate = val; }

private:

    TT_Adjust* _adjuster;

    nidas::core::dsm_sample_id_t _id;

    /** sampling rate, from input runstring */
    double _rate;

    /** time tag of last sample received */
    nidas::core::dsm_time_t _lastTime;

    /**
     * time tag of last sample in last fit
     */
    nidas::core::dsm_time_t _lastFitTime;

    /**
     * Folded sequences of CSAT3 samples that are the result of a faulty
     * system clock at acquisition time that was jumping around,
     * and than a later sorting of data by the (faulty) timetags.
     */
    std::list<CSAT3Fold> _folds;

    /** current number of folds */
    int _nfolds;

    /**
     * How many sequential samples from this CSAT have not been on fold 0
     */
    int _unmatchedFold0Counter;

    /** maximum number of folds in a period */
    int _maxNumFolds;
};

/**
 * Information kept for non-CSAT fixed rate sensor.
 */
class FixedRateSensor {
public:

    FixedRateSensor(TT_Adjust* adjuster,nidas::core::dsm_sample_id_t id, double rate);

    void addSample(const nidas::core::Sample* s, long long dsmSampleNumber);

    size_t fitAndOutput();

private:

    TT_Adjust* _adjuster;

    nidas::core::dsm_sample_id_t _id;

    /** sampling rate, from input runstring */
    double _rate;

    int _sampleDt;

    /**
     * current sequence of samples being saved for the fit.
     */
    std::list<samp_save> _samples;

    /**
     * number of samples in _samples 
     */
    size_t _nsamp;

    /**
     * Timetag of first sample in _samples.
     */
    nidas::core::dsm_time_t _firstTime;

    /**
     * Timetag of last sample received.
     */
    nidas::core::dsm_time_t _lastTime;

    /**
     * Timetag of last sample in last fit.
     */
    nidas::core::dsm_time_t _lastFitTime;

    /** least squares sums */
    gsl_fit_sums _sums;

};

class TT_Adjust
{
public:

    TT_Adjust();

    int parseRunstring(int argc, char** argv);

    int run() throw();

    static void sigAction(int sig, siginfo_t* siginfo, void* vptr);

    static void setupSignals();

    static int main(int argc, char** argv) throw();

    static int usage(const char* argv0);

    /**
     * Do a linear least squares fit of record number to timetags of fixed
     * rate sensors, output the newly timetagged samples.
     */
    void fixed_fit_output();

    /**
     * Adjust the timetags on a list of samples, using the coefficients
     * of the least squares fit, and send them to a SampleClient,
     * Clears the list when done.
     */
    void writeSamples(std::list<samp_save>& samps,
            double a, double b,int* maxnegp, int* maxposp,
            nidas::core::dsm_time_t& tfirst,nidas::core::dsm_time_t& tlast);

    /**
     * Output the other sensors that haven't been designated as CSAT3 or fixed
     * rate, using the adjusted time tags of the CSAT and fixed rate sensors.
     */
    void output_other();

private:

    static bool _interrupted;

    std::list<std::string> _inputFileNames;

    std::string _outputFileName;

    long long _fitUsecs;

    int _outputFileLength;

    /**
     * Default rate of CSAT3 sonics.
     */
    double _csatRate;

    nidas::core::SampleSorter _sorter;

    /**
     * All the CSAT3 sensors by sample id.
     */
    std::map<nidas::core::dsm_sample_id_t,CSAT3Sensor> _csats;

    /**
     * Information on fixed rate sensors, by sample id.
     */
    std::map<nidas::core::dsm_sample_id_t,FixedRateSensor> _fixedRateSensors;

    /**
     * The list of other samples to be output after the fits
     * of CSAT3 and other fixed rate sensors.
     */
    std::list<samp_save> _other_samples;

    /**
     * counter of current sample number of each dsm.
     */
    std::map<int, long long> _dsmSampleNumbers;

    /**
     * Comparator class for a set of record number,time tag pairs, sorting
     * on the record number.
     */
    class SequenceComparator {
    public:
        /**
         * return true if x is less than y.
         */
        bool operator() (
                const std::pair<long long,int>& x,
                const std::pair<long long,int>& y) const {
            return x.first < y.first;
        }
    };

    /**
     * Sequence of record numbers and determined value of the
     * clock error for each DSM.
     */
    std::map<unsigned int, std::set<std::pair<long long,int>,SequenceComparator> > _clockOffsets;

};


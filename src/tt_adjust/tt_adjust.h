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
#include <vector>

#include <nidas/core/Sample.h>
#include <nidas/core/SampleSorter.h>
#include <nidas/core/SampleInputHeader.h>
#include <nidas/core/HeaderSource.h>
#include <nidas/dynld/RawSampleOutputStream.h>
#include <nidas/util/EndianConverter.h>
#include <nidas/util/UTime.h>

#include "gsl.h"

// #define USE_WEIGHTS

/** forward declarations */
class TT_Adjust;

/** information to save with each data sample */
struct samp_save {

    const nidas::core::Sample* samp;

    /**
     * sample number from its DSM
     */
    long long dsmSampleNumber;

#ifdef USE_WEIGHTS
    float weight;
#endif

    samp_save() : samp(0),dsmSampleNumber(0)
#ifdef USE_WEIGHTS
        ,weight(1.0)
#endif
    {}

};

class CSAT3Fold
{
public:
    CSAT3Fold(): _firstTime(0),_lastTime(0),_firstSeq(-99),_lastSeq(-99),_size(0),_unmatched(0) {}

    void addSample(const nidas::core::Sample* samp,int cseq, long long dsmSampleNumber);

    bool empty() const {
        if (_samples.empty()) assert(_size == 0);
        else assert(_size > 0);
        return _samples.empty();
    }

    void append(CSAT3Fold& fold);

    void clear();

    std::list<samp_save>& getSamples() { return _samples; }

    nidas::core::dsm_time_t getFirstTime() const { return _firstTime; }
    nidas::core::dsm_time_t getLastTime() const { return _lastTime; }

    const nidas::core::Sample* getLastSample() const;

    const nidas::core::Sample* getNextToLastSample() const;

    const nidas::core::Sample* popLast(long long& dsmSampleNumber);

    int getFirstSeq() const { return _firstSeq; }
    int getLastSeq() const { return _lastSeq; }

    /**
     * _samples is a list: don't use list::size(), it takes O(N) time,
     * so we keep our own size.
     */
    size_t getSize() const { return _size; }

    /**
     * A sample was read that didn't match this fold.
     */
    void sampleNotMatched() { _unmatched++; }

    /**
     * How many samples have been read that don't match this fold 
     */
    int getNotMatched() const { return _unmatched; }

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

    size_t _size;

    int _unmatched;
};

class CSAT3Sensor
{
public:
    CSAT3Sensor(TT_Adjust* adjuster, nidas::core::dsm_sample_id_t id,double rate);

    nidas::core::dsm_sample_id_t getId() const
    {
        return _id;
    }

    void addSample(const nidas::core::Sample* s, long long dsmSampleNumber);

    int getNfolds() const { return _nfolds; }

    int handleUnmatchedFolds(nidas::core::dsm_time_t tt);

    int spliceAllFolds();

    void matchFoldsToFold0();

    std::list<CSAT3Fold>::const_iterator getFoldsBegin()
    {
        return _folds.begin();
    }

    std::list<CSAT3Fold>::const_iterator getFoldsEnd()
    {
        return _folds.end();
    }

    /**
     * Do a least squares fit of the timetags in a fold,
     * output the fold, and clear it.
     */
    size_t fitAndOutput(CSAT3Fold& fold);

    /**
     * Call fitAndOutput on fold 0.
     */
    size_t fitAndOutput();
    
    /**
     * If there has been a data gap, don't use the last fit for
     * predicting outliers of the next period.
     */
    void discardLastFit();

    static int getSequenceNumber(const nidas::core::Sample* samp);

    const static int MAJOR_DATA_GAP = 5 * USECS_PER_SEC;

    // CSAT3 folds older than this are considered abandoned
    const static int OLD_FOLD_MAX_DT = 5 * USECS_PER_SEC;

    double getRate() const { return _rate; }

    void setRate(double val);

    bool processSample(const nidas::core::Sample* samp,std::vector<float>& res);

    /**
     *  2 CSAT3 samples, a and b, have the same sequence number.
     *  c has a sequence number which is 1 different than a and b.
     *  Use the length of the wind vector differences and the temperature differences
     *  to find the best sequence matchup.
     *     if |a-c| < |b-c|, then c matches best with a (return 0),
     *     else c matches with b and return 1.
     */
    int bestSampleMatch(const nidas::core::Sample* a, const nidas::core::Sample* b, const nidas::core::Sample* c);

    /**
     *  2 CSAT3 samples, a and b, have the same sequence number.
     *  c and d have the same sequence number which is 1 different than a and b.
     *  Use the length of the wind vector differences and the temperature differences
     *  to find the best sequence matchup:
     *     if |a-c| + |b-d| < |a-d| + |b-c|, then a matches with c and b with d (return 0)
     *     else a matches with d and b matches with c (return 1)
     */
    int bestSampleMatch(const nidas::core::Sample* a, const nidas::core::Sample* b,
            const nidas::core::Sample* c, const nidas::core::Sample* d);

    nidas::core::dsm_time_t getLastTime() const
    {
        return _lastTime;
    }

private:

    TT_Adjust* _adjuster;

    nidas::core::dsm_sample_id_t _id;

    /** sampling rate, from input runstring */
    double _rate;

    int _sampleDt;

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

    double _lasta,_lastb,_dev;  // results of last fit

    nidas::core::dsm_time_t _tlast0;

    size_t _lastn;

    static const nidas::util::EndianConverter* _endianConverter;

    std::vector<ssize_t> _foldLengths;
};

/**
 * Information kept for non-CSAT fixed rate sensor.
 */
class FixedRateSensor {
public:

    FixedRateSensor(TT_Adjust* adjuster,nidas::core::dsm_sample_id_t id, double rate);

    void addSample(const nidas::core::Sample* s, long long dsmSampleNumber);

    size_t fitAndOutput();

    /**
     * If there has been a data gap, don't use the last fit for
     * predicting outliers of the next period.
     */
    void discardLastFit();

    /**
     * _samples is a list. Don't use list::size(), it takes O(N) time!
     * Instead we keep our own size.
     */
    size_t getSize() const { return _size; }

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
    size_t _size;

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

    double _lasta,_lastb,_dev;  // results of last fit

    nidas::core::dsm_time_t _tlast0;

    size_t _lastn;
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
     * Write unmatched samples to their own output.
     */
    void writeUnmatchedSamples(std::list<samp_save>& samps);

    /**
     * Output the other sensors that haven't been designated as CSAT3 or fixed
     * rate, using the adjusted time tags of the CSAT and fixed rate sensors.
     */
    void output_other();

    /**
     * Sample id matched a CSAT, but it isn't a wind sample (likely a reset message)
     * so output it with the other samples.
     */
    void otherSample(const nidas::core::Sample* samp,long long dsmSampleNumber);

private:

    static bool _interrupted;

    nidas::util::UTime _startTime;

    nidas::util::UTime _endTime;

    std::list<std::string> _inputFileNames;

    std::string _outputFileName;

    long long _fitUsecs;

    int _outputFileLength;

    std::string _unmatchedFileName;

    int _unmatchedFileLength;

    /**
     * Default rate of CSAT3 sonics.
     */
    double _csatRate;

    nidas::core::SampleSorter _sorter;

    nidas::dynld::RawSampleOutputStream _unmatchedOutput;

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
     * of CSAT3 and other fixed rate sensors, saved by dsm id.
     */
    std::map<int,std::list<samp_save> > _other_samples;

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

    class HeaderSrc: public nidas::core::HeaderSource
    {
    public:
        HeaderSrc(const nidas::core::SampleInputHeader& header) : _header(header) {}
        void sendHeader(nidas::core::dsm_time_t thead,nidas::core::SampleOutput* out) throw(nidas::util::IOException)
        {
            _header.write(out);
        }
    private:
        nidas::core::SampleInputHeader _header;
    };

};

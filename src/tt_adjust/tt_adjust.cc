// -*- mode: C++; indent-tabs-mode: nil; c-basic-offset: 4; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
    Copyright 2005 UCAR, NCAR, All Rights Reserved

    $LastChangedDate: 2010-10-14 15:01:57 -0600 (Thu, 14 Oct 2010) $

    $LastChangedRevision: $

    $LastChangedBy: $

    $HeadURL: http://svn.eol.ucar.edu/svn/isff/trunk/src/tt_adjust
 ********************************************************************
*/

// #define DEBUG

#include "tt_adjust.h"

#define HAS_BZLIB_H
#include <nidas/core/FileSet.h>
#include <nidas/core/Bzip2FileSet.h>
#include <nidas/dynld/RawSampleInputStream.h>
#include <nidas/dynld/RawSampleOutputStream.h>
#include <nidas/util/EOFException.h>
#include <nidas/util/Process.h>

#include <iomanip>
#include <limits>
#include <math.h>
#include <sys/resource.h>

using namespace nidas::core;
using namespace nidas::dynld;
using namespace std;

namespace n_u = nidas::util;

string formatId(dsm_sample_id_t id)
{
    ostringstream ost;
    ost << setw(2) << GET_DSM_ID(id) << ' ' << setw(4) << GET_SPS_ID(id);
    return ost.str();
}

string formatTime(dsm_time_t tt,bool terse=false)
{
    if (terse) return n_u::UTime(tt).format(true,"%H:%M:%S.%3f");
    return n_u::UTime(tt).format(true,"%Y %m %d %H:%M:%S.%3f");
}

/*
 * Do a least squares fit of the time tags of a
 * list of samples to the sample numbers.
 *
 * tlast0: uncorrected time of first sample of last fit
 *
 * @param nexclude: number of points excluded from the fit
 *          based on their differrence from time tags computed 
 *          by extrapolating the last fit
 */
size_t do_fit(list<samp_save>& samps,gsl_fit_sums& sums,
        double* a, double* b, double* cov_00, double* cov_01, double* cov_11,
        double* sumsq,
        double lasta, double lastb, double dev, size_t lastn, dsm_time_t tlast0,
        size_t& nexclude)
{
    if (samps.empty()) return 0;

    list<samp_save>::iterator si;

    nexclude = 0;

    // sum the samples if it hasn't been done
    if (sums.n == 0) {
        si = samps.begin();

        // last fit time
        dsm_time_t ttestl = (long long)(lasta + lastn * lastb) + tlast0;

        dsm_time_t tt0 =  si->samp->getTimeTag();
        int dtfit = tt0 - tlast0;
        for (size_t n = 0; si != samps.end(); ++si,n++) {
            samp_save& save = *si;
            const Sample* samp = save.samp;
            double ttx = samp->getTimeTag() - tt0;

            // Guess at a weight for the y value from the deviation
            // from the last fit.
            // Using continuously varying weights, as is done if
            // USE_WEIGHTS is defined, seems to screw up the results,
            // apparently systematically giving lower weight to later
            // points in the fit - perhaps due to sonic oscillator drift
            // from the period of the last fit.

            // Using a binary 0 or 1 weighting based on an absolute
            // limit for the difference between an observed timetag
            // from a predicted timetag, as done in DISCARD_BAD, seems to work better.
            

#define DISCARD_BAD
#ifdef DISCARD_BAD
            if (lastn > 0) {
                // estimate timetag from last fit.
                if (n == 0) {
                    // If first point doesn't fit, don't use last fit.
                    // Try every quartile point? Not easy since samples are in lists.
                    if (llabs(samp->getTimeTag()-ttestl) > USECS_PER_SEC / 4) lastn = 0;
                    gsl_fit_linear_add_point((double)n,ttx,sums);
                }
                else {
                    double ttest = lasta + (lastn + n) * lastb - dtfit;
                    if (abs(ttx-ttest) < USECS_PER_SEC / 4)
                        gsl_fit_linear_add_point((double)n,ttx,sums);
                    else
                        nexclude++;
                }
            }
            else
                    gsl_fit_linear_add_point((double)n,ttx,sums);
#else
#ifdef USE_WEIGHTS
            save.weight = 1.0;
            if (lastn > 0 && dev > 0.0) {
                // estimate timetag from last fit.
                double dt = fabs(lasta + (lastn + n) * lastb - dtfit - ttx);
                // attempt at a gaussian with width 5*dev
                save.weight = 1.0 / exp(dt * dt/(2.0 * 25.0 * dev * dev));
#ifdef DEBUG
                if (samp->getDSMId() == 1 && samp->getSpSId()== 210) {
                    int ttest = lasta + (lastn + n) * lastb - dtfit;
                    cerr << "99 " << n << ' ' << ttest << ' ' <<
                        ttx << ' ' << (samp->getTimeTag()-tt0) << ' ' <<
                        dt << ' ' << save.weight << endl;
                }
#endif
            }
            gsl_fit_wlinear_add_point((double)n,save.weight,ttx,sums);
#else
            gsl_fit_linear_add_point((double)n,ttx,sums);
#endif
#endif
        }
    }

#ifdef USE_WEIGHTS
    gsl_fit_wlinear_compute(sums,a,b);
#else
    gsl_fit_linear_compute(sums,a,b);
#endif

    si = samps.begin();
    dsm_time_t tt0 =  si->samp->getTimeTag();
    for (unsigned int n = 0; si != samps.end(); ++si,n++) {
        const samp_save& save = *si;
        const Sample* samp = save.samp;
        double ttx = samp->getTimeTag() - tt0;
#ifdef USE_WEIGHTS
        gsl_fit_wlinear_add_resid((double)n,save.weight,ttx,sums);
#else
        gsl_fit_linear_add_resid((double)n,ttx,sums);
#endif
    }

#ifdef USE_WEIGHTS
    gsl_fit_wlinear_compute_resid(sums,cov_00, cov_01, cov_11, sumsq);
#else
    gsl_fit_linear_compute_resid(sums,cov_00, cov_01, cov_11, sumsq);
#endif
    size_t n = sums.n;
    sums.zero();      // we're done, zero the sums
    return n;
}

void CSAT3Fold::addSample(const Sample* samp, int cseq, long long dsmSampleNumber)
{
    struct samp_save save;
    save.samp = samp;
    save.dsmSampleNumber = dsmSampleNumber;

    if (_samples.empty()) {
        _firstTime = samp->getTimeTag();
        _firstSeq = cseq;
        _size = 0;
    }
    _samples.push_back(save);
    _lastTime = samp->getTimeTag();
    _lastSeq = cseq;
    _unmatched = 0;
    _size++;

    // cerr << "addSample, id=" << formatId(samp->getId()) << " time=" <<
      //       formatTime(_lastTime) << endl;
}

void CSAT3Fold::append(CSAT3Fold& fold)
{
    if (_samples.empty()) {
        _firstTime = fold.getFirstTime();
        _firstSeq = fold.getFirstSeq();
    }
    _samples.splice(_samples.end(),fold.getSamples());
    _size += fold.getSize();
    _lastTime = fold.getLastTime();
    _lastSeq = fold.getLastSeq();
    _unmatched = fold.getNotMatched();
    fold.clear();   // splice clears the list, but also clear the other info
}

void CSAT3Fold::clear()
{
    _samples.clear();
    _lastTime = _firstTime = 0;
    _lastSeq = _firstSeq = -99;
    _size = 0;
    _unmatched = 0;
}

const Sample* CSAT3Fold::getLastSample() const
{
    if (_samples.empty()) return 0;
    return _samples.back().samp;
}

const Sample* CSAT3Fold::getNextToLastSample() const
{
    list<samp_save>::const_iterator si = _samples.end();
    if (si == _samples.begin()) return 0;
    --si;
    if (si == _samples.begin()) return 0;
    --si;
    return si->samp;
}

const Sample* CSAT3Fold::popLast(long long& dsmSampleNumber)
{
    if (_samples.empty()) return 0;
    const Sample* samp = _samples.back().samp;
    dsmSampleNumber =  _samples.back().dsmSampleNumber;
    _samples.pop_back();
    _size--;

    const Sample* lsamp = getLastSample();
    if (lsamp) {
        _lastTime = lsamp->getTimeTag();
        _lastSeq = CSAT3Sensor::getSequenceNumber(lsamp);
    }
    else {
        _lastTime = 0;
        _lastSeq = -99;
    }
    return samp;
}

/* static */
const nidas::util::EndianConverter* CSAT3Sensor::_endianConverter = 
    n_u::EndianConverter::getConverter(n_u::EndianConverter::EC_LITTLE_ENDIAN);

CSAT3Sensor::CSAT3Sensor(TT_Adjust* adjuster, dsm_sample_id_t id,double rate):
    _adjuster(adjuster),_id(id),
    _lastTime(0),_lastFitTime(0), _nfolds(0),_maxNumFolds(0),_lastn(0)
{
    setRate(rate);
}

void CSAT3Sensor::setRate(double val)
{ 
    _rate = val;
    if (_rate != 0.0) _sampleDt = (int)rint(USECS_PER_SEC / _rate);
}

/* extract the CSAT3 sequence number from a sample
 * If it can't be determined, return -99, which can't
 * be sequenced with 0-63.
 */
int CSAT3Sensor::getSequenceNumber(const Sample* samp)
{
    size_t inlen = samp->getDataByteLength();
    const unsigned int csat3Length = 12;   // two bytes each for u,v,w,tc,diag, and 0x55aa

    // discard short and long records. CSAT3's will serializer have extra 2 bytes.
    // In CHATS some records looked like so, which should be junked:
    // 00 80 00 80 00 80 00 80 00 80 00 80 00 80 00 80 3f f0 55 aa
    // 2007 05 26 00:39:22.3604, sonic 2,150
    if (inlen < csat3Length || inlen > csat3Length+2) return -99;

    const char* dptr = (const char*) samp->getConstVoidDataPtr();

    // check for correct termination bytes
    if (dptr[inlen-2] != '\x55' || dptr[inlen-1] != '\xaa') return -99;

    // check for NaN diag
    const short* sptr = (const short*) dptr;
    unsigned short diag = _endianConverter->uint16Value(sptr+4);
    if (diag == 61503 || diag == 61440) return -99;
    return (dptr[8] & 0x3f);
}

bool CSAT3Sensor::processSample(const Sample* samp,vector<float>& res)
{
    size_t inlen = samp->getDataByteLength();

    const unsigned int csat3Length = 12;   // two bytes each for u,v,w,tc,diag, and 0x55aa
    const char* dinptr = (const char*) samp->getConstVoidDataPtr();

    res.resize(4);

    if (inlen < csat3Length || inlen > csat3Length+2 || dinptr[inlen-2] != '\x55' || dinptr[inlen-1] != '\xaa') {
        for (int i = 0; i < 4; i++) 
            res[i] = floatNAN;
        return false;
    }

    const short* sptr = (const short*) dinptr;

    unsigned short diag = _endianConverter->uint16Value(sptr+4);

    // special NaN encodings of diagnostic value
    // (F03F=61503 and F000=61440):
    // all diag bits (bits 12 to 15) set and counter
    // value of 0 or 63, range codes all 0.
    //
    // We'll also just NaN the data if any diag bits are set
    // (so checks for 61503,61440 are actually unnecessary)
    if ((diag & 0xf000) || diag == 61503 || diag == 61440) {
        for (int i = 0; i < 4; i++) 
            res[i] = floatNAN;
        diag = (diag & 0xf000) >> 12;
    }
    else {
        int range[3];
        range[0] = (diag & 0x0c00) >> 10;
        range[1] = (diag & 0x0300) >> 8;
        range[2] = (diag & 0x00c0) >> 6;
        const float scale[] = {0.002,0.001,0.0005,0.00025};
        diag = (diag & 0xf000) >> 12;

        int nmissing = 0;
        signed short w;
        for (int i = 0; i < 3; i++) {
            w =  _endianConverter->int16Value(sptr + i);
            /* Screen NaN encodings of wind components */
            if (w == -32768) {
                res[i] = floatNAN;
                nmissing++;
            }
            else
                res[i] = w * scale[range[i]];
        }
        /*
         * Documentation also says speed of sound should be a NaN if
         * ALL the wind components are NaNs.
         */
        w =  _endianConverter->int16Value(sptr + 3);
        if (nmissing == 3 || w == -32768)
            res[3] = floatNAN;
        else {
            /* convert to speed of sound */
            float c = (w * 0.001) + 340.0;
            /* Convert speed of sound to Tc */
            c /= 20.067;
            res[3] = c * c - 273.15;
        }
    }
    return true;
}

void CSAT3Sensor::matchFoldsToFold0()
{
    list<CSAT3Fold>::iterator matchingFold = _folds.begin();

    while (matchingFold != _folds.end()) {

        dsm_time_t toldest = std::numeric_limits<long long>::max();
        matchingFold = _folds.end();
        int mfold = -1;

        list<CSAT3Fold>::iterator fi = _folds.begin();
        CSAT3Fold& fold0 = *fi++;

        // find oldest matching fold
        for (int ifold = 1; fi != _folds.end(); ++fi,ifold++) {
            CSAT3Fold& fold = *fi;
            assert(!fold.empty());
            if (fold0.getLastSeq() < 0 || fold.getFirstSeq() == (fold0.getLastSeq() + 1) % 64) {
                if (fold.getFirstTime() < toldest) {
                    matchingFold = fi;
                    mfold = ifold;
                    toldest = fold.getFirstTime();
                }
            }
        }
        if (matchingFold != _folds.end()) {
#ifdef DEBUG
            cerr << "debug: " << formatTime(matchingFold->getFirstTime()) <<
                ", id=" << formatId(_id) <<
                ", #unmatched=" << fold0.getNotMatched() <<
                ", nfolds=" << _nfolds <<
                ", splicing matched fold " << mfold <<
                " to fold 0, f0 last seq=0x" << hex << fold0.getLastSeq() << dec <<
                ", fold " << mfold << " first seq=0x" << hex << matchingFold->getFirstSeq() << dec << endl;
#endif
            _foldLengths.push_back(matchingFold->getSize());
            fold0.append(*matchingFold);
            _folds.erase(matchingFold);
            _nfolds--;
        }
    }
}

/**
 *  2 CSAT3 samples, a and b, have the same sequence number.
 *  c has a sequence number which is 1 different than a and b.
 *  Use the length of the wind vector differences and the temperature differences
 *  to find the best sequence matchup.
 *     if |a-c| < |b-c|, then c matches best with a (return 0),
 *     else c matches with b and return 1.
 */
int CSAT3Sensor::bestSampleMatch(const Sample* sa, const Sample* sb,const Sample* sc)
{
    vector <float> da;
    processSample(sa,da);

    vector <float> db;
    processSample(sb,db);

    vector <float> dc;
    processSample(sc,dc);

    // wind difference between sample a and c
    float wdac = 0.0;
    for (int i = 0; i < 3; i++) {
        float dw = da[i] - dc[i];
        wdac += dw * dw;
    }
    wdac = sqrt(wdac);
    float tdac = fabsf(da[3] - dc[3]);

    // wind difference between sample b and c
    float wdbc = 0.0;
    for (int i = 0; i < 3; i++) {
        float dw = db[i] - dc[i];
        wdbc += dw * dw;
    }
    wdbc = sqrt(wdbc);
    float tdbc = fabsf(db[3] - dc[3]);

    if (!isnan(wdac) && !isnan(wdbc)) {
        float dw = fabsf(wdac - wdbc);
        if (dw < 0.01) {
            // better than 0.02 m/s agreement, check temperature
            // otherwise don't know how to compare m/s with degC
            // I guess it should depend on the current variances...
            if (tdac < tdbc) return 0;
            return 1;
        }
        else if (wdac < wdbc) return 0;
        else return 1;
    }
    else {
        bool wanan = false;
        for (int i = 0; i < 3; i++) if (isnan(da[i])) wanan = true;

        bool wbnan = false;
        for (int i = 0; i < 3; i++) if (isnan(db[i])) wbnan = true;

        bool wcnan = false;
        for (int i = 0; i < 3; i++) if (isnan(dc[i])) wcnan = true;

        if (wcnan) {
            if (wanan && !wbnan) return 0;
            if (wbnan && !wanan) return 1;
            if (isnan(dc[4])) {
                if (isnan(da[4]) && !isnan(db[4])) return 0;
                if (isnan(db[4]) && !isnan(da[4])) return 1;
            }
            // cannot determine best match, c is NaN, but not a or b
            // or both a and b are NaN
            return -1;
        }
        else if (!wanan) return 0;
        else if (!wbnan) return 1;
        // cannot determine best match, sample c is not NaN, but a and b both are
    }
    return -1;
}

/**
 *  2 CSAT3 samples, a and b, have the same sequence number.
 *  c and d have the same sequence number which is 1 different than a and b.
 *  Use the length of the wind vector differences and the temperature differences
 *  to find the best sequence matchup:
 *     if |a-c| + |b-d| < |a-d| + |b-c|, then a matches best with c and b with d (return 0)
 *     else a matches with d and b matches with c (return 1)
 */
int CSAT3Sensor::bestSampleMatch(const Sample* sa, const Sample* sb, const Sample* sc, const Sample *sd)
{
    vector <float> da;
    processSample(sa,da);

    vector <float> db;
    processSample(sb,db);

    vector <float> dc;
    processSample(sc,dc);

    vector <float> dd;
    processSample(sd,dd);

    // wind difference between sample a and c
    float wdac = 0.0;
    for (int i = 0; i < 3; i++) {
        float dw = da[i] - dc[i];
        wdac += dw * dw;
    }
    wdac = sqrt(wdac);
    float tdac = fabsf(da[3] - dc[3]);

    // wind difference between sample a and d
    float wdad = 0.0;
    for (int i = 0; i < 3; i++) {
        float dw = da[i] - dd[i];
        wdad += dw * dw;
    }
    wdad = sqrt(wdad);
    float tdad = fabsf(da[3] - dd[3]);

    // wind difference between sample b and c
    float wdbc = 0.0;
    for (int i = 0; i < 3; i++) {
        float dw = db[i] - dc[i];
        wdbc += dw * dw;
    }
    wdbc = sqrt(wdbc);
    float tdbc = fabsf(db[3] - dc[3]);

    // wind difference between sample b and d
    float wdbd = 0.0;
    for (int i = 0; i < 3; i++) {
        float dw = db[i] - dd[i];
        wdbd += dw * dw;
    }
    wdbd = sqrt(wdbd);
    float tdbd = fabsf(db[3] - dd[3]);

    if (!isnan(wdac) && !isnan(wdbd) && !isnan(tdac) && !isnan(tdbd)) {
        // no NaNs
        float dw = fabsf(wdac + wdbd - wdad - wdbc);
        if (dw < 0.01) {
            if (tdac + tdbd <= tdad + tdbc) return 0;
            return 1;
        }
        if (wdac + wdbd <= wdad + wdbc) return 0;
        return 1;
    }
    else {
        // this isn't a complete attempt to matchup the NaNs
        if (!isnan(wdac) && isnan(wdbd)) return 0;
        if (!isnan(wdad) && isnan(wdbc)) return 1;
    }
    return -1;
}

void CSAT3Sensor::addSample(const Sample* samp, long long dsmSampleNumber)
{
    int cseq = getSequenceNumber(samp);

    dsm_time_t tt = samp->getTimeTag();

    // check time difference from last sample received
    // on this csat.  If large positive, restart fit
    long long dt = tt - _lastTime;
    if (dt > MAJOR_DATA_GAP && _lastTime != 0) {

        // Deltat bigger than any expected clock jump for this CSAT
        // Assume a data system restart, or sensor unplugged/plugged
        // splice all folds for this CSAT onto fold[0], then output.
        // We're just splicing in numeric fold order, may want to look
        // at this further

        matchFoldsToFold0();

        if (!_folds.empty()) {
            list<CSAT3Fold>::iterator fi = _folds.begin();
            CSAT3Fold& fold0 = *fi++;
            fitAndOutput(fold0);
            discardLastFit();
            for (int ifold=1; fi != _folds.end(); ifold++) {
                CSAT3Fold& fold = *fi;
                cerr << "warning: " << formatTime(fold.getFirstTime()) <<
                    ", id=" << formatId(_id) << ", nfolds=" << _nfolds <<
                    ", major data gap, saving unmatched fold " << ifold << ' ' <<
                    formatTime(fold.getFirstTime(),true) << '-' << formatTime(fold.getLastTime(),true) <<
                    ",0x" << hex << fold.getFirstSeq() << dec << "-0x" <<
                    hex << fold.getLastSeq() << dec << ',' << fold.getSize() << endl;
                _foldLengths.push_back(-(signed)fold.getSize());
                list<samp_save>& samps = fold.getSamples();
                _adjuster->writeUnmatchedSamples(samps);
                fold.clear();
                fi = _folds.erase(fi);
                _nfolds--;
            }
            assert(_nfolds == 1);
        }
    }

    // doesn't have a sequence number, so not a wind sample, probably a
    // ?? output, or reset message, or a sample with missing data:
    //      00 80 00 80 00 80 00 80 3f f0 55 aa 
    // Add it to the "other" sensor output.
    // In a missing data sample, the wind and tc data will be NaN,
    // and the resampler ignores NaNs.  We don't save _lastTime
    if (cseq < 0) {
        list<CSAT3Fold>::iterator fi = _folds.begin();
        for (; fi != _folds.end(); ++fi) fi->sampleNotMatched();
        _adjuster->otherSample(samp,dsmSampleNumber);
        return;
    }


    _lastTime = tt;


    // Find a fold that this sample belongs to by checking
    // whether its csat3 sequence number is one greater than
    // the sequence number of the last sample in the fold.
    //

    list<CSAT3Fold>::iterator matchingFold = _folds.end();
    int mfold = -1;

    // only an empty fold 0
    if (_nfolds == 1 && _folds.begin()->empty()) {
        matchingFold = _folds.begin();
        mfold = 0;
    }
    else {
        // If this sample has the same sequence number as the last sample added to a fold,
        // perhaps it should replace that sample.
        list<CSAT3Fold>::iterator fi = _folds.begin();
        for (int ifold = 0; fi != _folds.end(); ++fi,ifold++) {
            CSAT3Fold& fold = *fi;
            if (fold.getLastSeq() == cseq) {
                if (fold.getSize() > 1) {
                    list<CSAT3Fold>::iterator fi2 = _folds.begin();
                    for (int ifold2 = 0; fi2 != _folds.end(); ++fi2,ifold2++) {
                        if (fi2 == fi) continue;
                        CSAT3Fold& fold2 = *fi2;
                        if (cseq == ((fold2.getLastSeq() + 1) % 64)) {
                            if (bestSampleMatch(fold.getNextToLastSample(),fold2.getLastSample(),
                                        fold.getLastSample(),samp) == 1) {
                                cerr << formatTime(tt) <<  ' ' << formatId(_id) <<
                                    ", sample with seq=0x" << hex << cseq << dec <<
                                    " matches better to ifold " << ifold << " than previous with time " << 
                                    formatTime(fold.getLastSample()->getTimeTag()) << endl;
                                long long dx;
                                const Sample* sx = fold.popLast(dx);
                                fold.addSample(samp,cseq,dsmSampleNumber);
                                samp = sx;
                                dsmSampleNumber = dx;
                                // by continuing here we can conceivably correct other mismatches.
                                // Haven't thought it through though (how's that for alliteration...)
                                // 
                            }
                        }
                    }
                }
            }
        }

        // match the sample to the fold by sequence number. If more than
        // one sequence number match, check for best match of the data
        fi = _folds.begin();
        for (int ifold = 0; fi != _folds.end(); ++fi,ifold++) {
            CSAT3Fold& fold = *fi;
            if (cseq == ((fold.getLastSeq() + 1) % 64)) {
                if (matchingFold != _folds.end()) {

                    // if more than one matching fold, compare the wind vectors
                    // and virtual temperature, tc. Look for best fit:
                    //  current sample to prev matching fold
                    //  current sample to current matching fold
                    int match = bestSampleMatch(matchingFold->getLastSample(),fold.getLastSample(),samp);
                    if (match == 1) {
                        matchingFold = fi;
                        mfold = ifold;
                    }
                }
                else {
                    matchingFold = fi;
                    mfold = ifold;
                }
            }
        }
    }
    
    // no match. 
    if (matchingFold == _folds.end()) {
        list<CSAT3Fold>::iterator fi = _folds.begin();
        _folds.push_back(CSAT3Fold());
        mfold = _nfolds++;
        matchingFold = _folds.end();
        matchingFold--;
#ifdef DEBUG
        cerr << formatTime(tt) <<
            ", id=" << formatId(_id) << " added a fold, nfolds=" <<
            _nfolds << endl;
#endif
    }
    else {
#ifdef DEBUG
        if (mfold != 0 || _nfolds > 1)
            cerr << formatTime(tt) <<
                ", id=" << formatId(_id) << " appending sample to fold " << mfold <<
                ", lastseq=0x" << hex << matchingFold->getLastSeq() << dec <<
                " cseq=0x" << hex << cseq << dec << endl;
#endif
    }

    matchingFold->addSample(samp,cseq,dsmSampleNumber);

    list<CSAT3Fold>::iterator fi = _folds.begin();
    if (fi != matchingFold) fi->sampleNotMatched();
    CSAT3Fold& fold0 = *fi++;

    for (; fi != _folds.end(); ++fi) {
        if (fi != matchingFold) fi->sampleNotMatched();
    }

    _maxNumFolds = std::max(_maxNumFolds,_nfolds);

    if (_nfolds != handleUnmatchedFolds(tt)) fold0 = _folds.front();

    if (fold0.getNotMatched() > _nfolds) {
        // we're no longer getting samples for fold 0. 
        // It may have ended.
        // There must be at least 2 folds
        // Check for a fold to splice to fold 0;
        assert(_nfolds > 1);
        assert(!_folds.empty());
        matchFoldsToFold0();
    }

    if (fold0.getNotMatched() > 10 + _nfolds) {
#ifdef DEBUG_FOLDS
        cerr << "warning: " << formatTime(tt) <<
            ", id=" << formatId(_id) <<
            ", nfolds=" << _nfolds <<
            ", fit and output unmatched fold 0 " << 
            formatTime(fold0.getFirstTime(),true) << '-' << formatTime(fold0.getLastTime(),true) <<
            ",0x" << hex << fold0.getFirstSeq() << dec << "-0x" <<
            hex << fold0.getLastSeq() << dec << ',' << fold0.getSize() <<
            ", #unmatched=" << fold0.getNotMatched() << endl;
#endif
        fitAndOutput(fold0);
        discardLastFit();

        // move oldest fold to fold0
        dsm_time_t toldest = std::numeric_limits<long long>::max();
        matchingFold = _folds.end();
        mfold = -1;

        list<CSAT3Fold>::iterator fi = _folds.begin();
        CSAT3Fold& fold0 = *fi++;
        for (int ifold = 1; fi != _folds.end(); ++fi,ifold++) {
            CSAT3Fold& fold = *fi;
            assert(!fold.empty());
            if (fold.getFirstTime() < toldest) {
                matchingFold = fi;
                mfold = ifold;
                toldest = fold.getFirstTime();
            }
        }
        if (matchingFold != _folds.end()) {
#ifdef DEBUG_FOLDS
            cerr << "warning: " << formatTime(matchingFold->getFirstTime()) <<
                ", id=" << formatId(_id) <<
                ", nfolds=" << _nfolds <<
                ", moving fold " << mfold << ' ' <<
                formatTime(matchingFold->getFirstTime(),true) << '-' << formatTime(matchingFold->getLastTime(),true) <<
                ",0x" << hex << matchingFold->getFirstSeq() << dec << "-0x" <<
                hex << matchingFold->getLastSeq() << dec << ',' << matchingFold->getSize() <<
                " to fold 0 " << endl;
#endif
            _foldLengths.push_back(matchingFold->getSize());
            fold0.append(*matchingFold);
            _folds.erase(matchingFold);
            _nfolds--;
        }
    }
}

size_t CSAT3Sensor::fitAndOutput()
{
    if (_folds.empty()) return 0;
    CSAT3Fold& fold0 = _folds.front();
    return fitAndOutput(fold0);
}
size_t CSAT3Sensor::fitAndOutput(CSAT3Fold& fold)
{
    // fit and output fold 0
    if (fold.empty()) return 0;

    double a,b; /* y = a + b * x */
    double cov_00, cov_01, cov_11, sumsq;

    struct gsl_fit_sums sums;

    size_t nexclude;
    size_t n = do_fit(fold.getSamples(),sums,&a,&b,
            &cov_00,&cov_01,&cov_11,&sumsq,_lasta,_lastb,_dev,_lastn,_tlast0,nexclude);

    assert(fold.getSize() == n + nexclude);
    assert(n + nexclude > 0);

    int maxneg,maxpos;
    dsm_time_t tlastfit = _lastFitTime;
    dsm_time_t tfirst,tlast;
    if (n > 2) {
        _adjuster->writeSamples(fold.getSamples(),a,b,&maxneg,&maxpos,tfirst,tlast);
        cout << 
            formatTime(fold.getFirstTime()) << ' ' <<
            formatId(_id) << ' ' <<
            setw(6) << n << ' ' <<
            setw(4) << nexclude << ' ' <<
            setw(10) << fixed << setprecision(2) << a << ' ' <<
            setw(10) << b << ' ' <<
            setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
            setw(10) << maxneg << ' ' << 
            setw(10) << maxpos <<  ' ' <<
            setw(10) << (tlastfit == 0 ? 0 : tfirst-tlastfit) << ' ' <<
            setw(3) << _maxNumFolds;
        for (unsigned int i = 0; i < _foldLengths.size(); i++)
            cout << ' ' << _foldLengths[i];
        cout << endl;
        _lastFitTime = tlast;
        _maxNumFolds = 0;

        // save results of fit
        _lasta = a;
        _lastb = b;
        _dev = sqrt(sumsq / (n - 2.0));

        // more than 10% discards is a problem
        if (n > 0 && (float)nexclude / n > 0.1) _lastn = 0;
        else _lastn = n + nexclude;

        _tlast0 = fold.getFirstTime();
    }
    else {
#ifdef DEBUG
        cerr << "warning: " << formatTime(fold.getFirstTime()) <<
            ", id=" << formatId(_id) << ", saving small fold, n=" << n << " nexclude=" << nexclude << ' ' <<
            formatTime(fold.getFirstTime(),true) << '-' << formatTime(fold.getLastTime(),true) <<
            ",0x" << hex << fold.getFirstSeq() << dec << "-0x" <<
            hex << fold.getLastSeq() << dec << ',' << fold.getSize() << endl;
#endif
        _adjuster->writeUnmatchedSamples(fold.getSamples());
        discardLastFit();
    }
    fold.clear();
    _foldLengths.clear();
    return n;
}

void CSAT3Sensor::discardLastFit()
{
    _dev = 0;
    _lastn = 0;
}


int CSAT3Sensor::handleUnmatchedFolds(dsm_time_t tt)
{

    if (_folds.empty()) {
        assert(_nfolds == 0);
        return _nfolds;
    }
    assert(_nfolds > 0);

    list<CSAT3Fold>::iterator matchfi = _folds.begin();

    while (_nfolds > 1 && matchfi != _folds.end()) {

        matchfi = _folds.end();

        list<CSAT3Fold>::iterator fi = _folds.begin();
        fi++;

        int ifold;
        for (ifold = 1; fi != _folds.end(); ifold++) {
            CSAT3Fold& fold = *fi;

            // folds other than 0 should not be empty
            assert(!fold.empty());

            long long dt = tt - fold.getLastTime();
            if (fold.getNotMatched() > 10 + _nfolds || dt > _sampleDt * 20)
            {
#ifdef DEBUG_FOLDS
                if (fold.getSize() > 10) 
                    cerr << "warning: " << formatTime(fold.getFirstTime()) <<
                        ", id=" << formatId(_id) << ", nfolds=" << _nfolds <<
                        ", saving unmatched fold " << ifold << ' ' <<
                        formatTime(fold.getFirstTime(),true) << '-' << formatTime(fold.getLastTime(),true) <<
                        ",0x" << hex << fold.getFirstSeq() << dec << "-0x" <<
                        hex << fold.getLastSeq() << dec << ',' << fold.getSize() <<
                        ", unmatched=" << fold.getNotMatched() << endl;
#endif
                _foldLengths.push_back(-(signed)fold.getSize());
                list<samp_save>& samps = fold.getSamples();
                _adjuster->writeUnmatchedSamples(samps);
                fold.clear();
                fi = _folds.erase(fi);
                _nfolds--;
            }
            else ++fi;
        }
    }
    return _nfolds;
}

int CSAT3Sensor::spliceAllFolds()
{

    if (_folds.empty()) {
        assert(_nfolds == 0);
        return 0;
    }
    assert(_nfolds > 0);

    // splice all folds to fold0, oldest first
    while (_nfolds > 1) {
        list<CSAT3Fold>::iterator fi = _folds.begin();
        CSAT3Fold& fold0 = *fi++;

        list<CSAT3Fold>::iterator matchfi = _folds.end();
        dsm_time_t oldestTime = numeric_limits<long long>::max();

        for ( ; fi != _folds.end(); ++fi) {
            CSAT3Fold& fold = *fi;
            if (fold.getFirstTime() < oldestTime) {
                matchfi = fi;
                oldestTime = fold.getFirstTime();
            }
        }
        assert(matchfi != _folds.end());
        _foldLengths.push_back(matchfi->getSize());
        fold0.append(*matchfi);
        _folds.erase(matchfi);
        _nfolds--;
    }
    return _nfolds;
}

FixedRateSensor::FixedRateSensor(TT_Adjust* adjuster,dsm_sample_id_t id, double rate):
    _adjuster(adjuster),_id(id),_rate(rate),_sampleDt((int)rint(USECS_PER_SEC / _rate)),
    _size(0),_firstTime(0),_lastTime(0),_lastFitTime(0)
{
}

void FixedRateSensor::addSample(const Sample* samp, long long dsmSampleNumber)
{

    dsm_time_t tt = samp->getTimeTag();

    // check for a data gap. If so, fit and output the samples we have.
    if (!_samples.empty()) {
        int dt = tt - _lastTime;
        if (abs(dt) > 64 * _sampleDt) {
            fitAndOutput();
            discardLastFit();
        }
    }
    if (_samples.empty()) {
        _firstTime = samp->getTimeTag();
        _size = 0;
    }
    _lastTime = tt;

    samp_save save;
    save.samp = samp;
    save.dsmSampleNumber = dsmSampleNumber;
    _samples.push_back(save);
    _size++;
}

size_t FixedRateSensor::fitAndOutput()
{
    size_t n;
    double a,b; /* y = a + b * x */
    double cov_00, cov_01, cov_11, sumsq;

    size_t nexclude;
    n = do_fit(_samples,_sums,&a,&b,
            &cov_00,&cov_01,&cov_11,&sumsq,_lasta,_lastb,_dev,_lastn,_tlast0,nexclude);
    if (n > 0) {
        int maxneg,maxpos;
        dsm_time_t tlastfit = _lastFitTime;
        dsm_time_t tfirst;
        _adjuster->writeSamples(_samples,a,b,&maxneg,&maxpos,tfirst,_lastFitTime);
        cout << 
            formatTime(_firstTime) << ' ' <<
            formatId(_id) << ' ' <<
            setw(6) << n << ' ' <<
            setw(4) << nexclude << ' ' <<
            setw(10) << fixed << setprecision(2) << a << ' ' <<
            setw(10) << b << ' ' <<
            setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
            setw(10) << maxneg << ' ' << 
            setw(10) << maxpos << ' ' <<
            setw(10) << (tlastfit == 0 ? 0 : tfirst-tlastfit) << endl;
    }
    // save results of fit
    _lasta = a;
    _lastb = b;
    if (n > 2)
        _dev = sqrt(sumsq / (n - 2.0));
    else _dev = _sampleDt / 4;
    _lastn = n + nexclude;
    _tlast0 = _firstTime;
    return n;
}

void FixedRateSensor::discardLastFit()
{
    _dev = 0;
    _lastn = 0;
}

int main(int argc, char** argv)
{
    return TT_Adjust::main(argc,argv);
}

/* static */
bool TT_Adjust::_interrupted = false;

TT_Adjust::TT_Adjust():
    _startTime((time_t)0),_endTime((time_t)0),_fitUsecs(30*USECS_PER_SEC),
    _outputFileLength(0),_csatRate(0.0),
    _sorter("output sorter",true)
{
}

int TT_Adjust::parseRunstring(int argc, char** argv)
{
    extern char *optarg;       /* set by getopt() */
    extern int optind;       /* "  "     "     */
    int opt_char;     /* option character */
    const char* cp;
    char* cp2;

    while ((opt_char = getopt(argc, argv, "B:c:E:f:l:i:o:r:s:u:")) != -1) {
	switch (opt_char) {
        case 'B':
            try {
                _startTime = n_u::UTime::parse(true,optarg);
            }
            catch (const n_u::ParseException& pe) {
                cerr << pe.what() << endl;
                return usage(argv[0]);
            }
            break;
        case 'c':
            {
                unsigned int dsmid;
                unsigned int sensorid;
                cp = optarg;

                dsmid = strtol(cp,&cp2,0);
                if (cp2 == cp || *cp2 == '\0') return usage(argv[0]);
                cp = ++cp2;
                sensorid = strtol(cp,&cp2,0);
                if (cp2 == cp) return usage(argv[0]);
                double rate = _csatRate;

                if (*cp2 != '\0') {
                    cp = ++cp2;
                    rate = strtod(cp,&cp2);
                    if (cp2 == cp) return usage(argv[0]);
                }

                dsm_sample_id_t id = 0;
                id = SET_DSM_ID(id,dsmid);
                id = SET_SHORT_ID(id,sensorid);
                _csats.insert(
                        make_pair<dsm_sample_id_t,CSAT3Sensor>(id,CSAT3Sensor(this,id,rate)));
            }
            break;
        case 'E':
            try {
                _endTime = n_u::UTime::parse(true,optarg);
            }
            catch (const n_u::ParseException& pe) {
                cerr << pe.what() << endl;
                return usage(argv[0]);
            }
            break;
	case 'f':
            cp = optarg;
	    _fitUsecs = (long long)(strtod(cp,&cp2) * USECS_PER_SEC);
            if (cp2 == cp) return usage(argv[0]);
	    break;
	case 'i':
	    _inputFileNames.push_back(optarg);
	    break;
	case 'l':
	    _outputFileLength = atoi(optarg);
	    _unmatchedFileLength = _outputFileLength;
	    break;
	case 'o':
	    _outputFileName = optarg;
	    break;
        case 'r':
            cp = optarg;
            _csatRate = strtod(cp,&cp2);
            if (cp2 == cp) return usage(argv[0]);
            break;
        case 's':
            {
                unsigned int dsmid;
                unsigned int sensorid;
                cp = optarg;

                dsmid = strtol(cp,&cp2,0);
                if (cp2 == cp || *cp2 == '\0') return usage(argv[0]);
                cp = ++cp2;
                sensorid = strtol(cp,&cp2,0);
                if (cp2 == cp || *cp2 == '\0') return usage(argv[0]);
                cp = ++cp2;
                double rate = strtod(cp,&cp2);

                dsm_sample_id_t id = 0;
                id = SET_DSM_ID(id,dsmid);
                id = SET_SHORT_ID(id,sensorid);

                _fixedRateSensors.insert(
                        make_pair<dsm_sample_id_t,FixedRateSensor>(id,FixedRateSensor(this,id,rate)));
            }
            break;
	case 'u':
	    _unmatchedFileName = optarg;
	    break;
	case '?':
	    return usage(argv[0]);
	}
    }
    if (_outputFileName.length() == 0) return usage(argv[0]);
    if (_unmatchedFileName.length() == 0) return usage(argv[0]);

    for ( ; optind < argc; optind++) _inputFileNames.push_back(argv[optind]);
    if (_inputFileNames.empty()) return usage(argv[0]);

    // set unspecified csat rates to default
    map<dsm_sample_id_t,CSAT3Sensor>::iterator ci = _csats.begin();
    for ( ; ci != _csats.end(); ++ci) {
        CSAT3Sensor& csat = ci->second;
        if (csat.getRate() == 0.0) csat.setRate(_csatRate);
    }
    return 0;
}

/* static */
int TT_Adjust::usage(const char* argv0)
{
    cerr << "\
Usage: " << argv0 << "[-c d,s[,rate] ...] [-r rate] [-s d,s[,rate] ...] [-f secs_per_fit]\n\
    -o output  -u unmatched_file_name [-l output_file_length] input ...\n\
\n\
    -c d,s,rate: dsm and sensor id of CSAT3 sonic\n\
    -r rate: default rate of all CSAT3 sonics\n\
    -s d,s,rate: dsm and sensor id of a sensor with a fixed rate output\n\
    -f secs_per_fit: time period of least squares fit of timetags to record number\n\
    -o output: output file name or file name format\n\
    -u output: output file name or file name format for unmatched samples\n\
    -l output_file_length: length of output files, in seconds, default=0, unlimited\n\
    input ...:  one or more input files\n\
" << endl;
    return 1;
}

/* static */
int TT_Adjust::main(int argc, char** argv) throw()
{
    setupSignals();

    TT_Adjust adjuster;

    int res;
    
    if ((res = adjuster.parseRunstring(argc,argv)) != 0) return res;

    nidas::util::Logger* logger = n_u::Logger::createInstance(&std::cerr);
    n_u::LogConfig lc;
    lc.level = 6;
    logger->setScheme(n_u::LogScheme("tt_adjust").addConfig (lc));

    return adjuster.run();
}

/* static */
void TT_Adjust::sigAction(int sig, siginfo_t* siginfo, void* vptr) {
    cerr <<
    	"received signal " << strsignal(sig) << '(' << sig << ')' <<
	", si_signo=" << (siginfo ? siginfo->si_signo : -1) <<
	", si_errno=" << (siginfo ? siginfo->si_errno : -1) <<
	", si_code=" << (siginfo ? siginfo->si_code : -1) << endl;
                                                                                
    switch(sig) {
    case SIGHUP:
    case SIGTERM:
    case SIGINT:
            TT_Adjust::_interrupted = true;
    break;
    }
}

/* static */
void TT_Adjust::setupSignals()
{
    sigset_t sigset;
    sigemptyset(&sigset);
    sigaddset(&sigset,SIGHUP);
    sigaddset(&sigset,SIGTERM);
    sigaddset(&sigset,SIGINT);
    sigprocmask(SIG_UNBLOCK,&sigset,(sigset_t*)0);
                                                                                
    struct sigaction act;
    sigemptyset(&sigset);
    act.sa_mask = sigset;
    act.sa_flags = SA_SIGINFO;
    act.sa_sigaction = TT_Adjust::sigAction;
    sigaction(SIGHUP,&act,(struct sigaction *)0);
    sigaction(SIGINT,&act,(struct sigaction *)0);
    sigaction(SIGTERM,&act,(struct sigaction *)0);
}

void TT_Adjust::otherSample(const Sample* samp,long long dsmSampleNumber)
{
    struct samp_save save;
    save.samp = samp;
    save.dsmSampleNumber = dsmSampleNumber;
    _other_samples[samp->getDSMId()].push_back(save);
}

void TT_Adjust::writeSamples(list<samp_save>& samps,
        double a, double b,int* maxnegp, int* maxposp,
        dsm_time_t& tfirst, dsm_time_t& tlast)
{
    int maxneg = 0;
    int maxpos = 0;
    size_t n;
    dsm_time_t newtt = 0;
    tfirst = 0;

    list<samp_save>::iterator si = samps.begin();
    dsm_time_t tt0 =  si->samp->getTimeTag();
    for (n = 0; si != samps.end(); ++si,n++) {
        samp_save& save = *si;
        
        // cast away const so we can change the time tag.
        Sample* samp = const_cast<Sample*>(save.samp);

        newtt = tt0 + (dsm_time_t) (a + n * b + 0.5);

        if (n == 0) tfirst = newtt;
        int dt = newtt - samp->getTimeTag();

        if (dt < 0) maxneg = std::min(dt,maxneg);
        else if (dt > 0) maxpos = std::max(dt,maxpos);

        samp->setTimeTag(newtt);
        _sorter.receive(samp);

        unsigned int dsmid = samp->getDSMId();
        _clockOffsets[dsmid].insert(make_pair<long long,int>(save.dsmSampleNumber,dt));

        samp->freeReference();
    }
    samps.clear();
    *maxnegp = maxneg;
    *maxposp = maxpos;
    tlast = newtt;
}

void TT_Adjust::writeUnmatchedSamples(list<samp_save>& samps)
{
    list<samp_save>::iterator si = samps.begin();
    for ( ; si != samps.end(); ++si) {
        samp_save& save = *si;
        const Sample* samp = save.samp;
        _unmatchedOutput.receive(samp);
        samp->freeReference();
    }
    samps.clear();
}

void TT_Adjust::output_other()
{
    map<int,list<samp_save> >::iterator di = _other_samples.begin();
    for ( ; di != _other_samples.end(); ++di) {
        int dsmid = di->first;
        list<samp_save>& samps = di->second;

        size_t ndiscards = 0;
        size_t n;
        int maxneg = 0;
        int maxpos = 0;

        set<pair<long long,int>,SequenceComparator>& offsets = _clockOffsets[dsmid];

        // save first time
        dsm_time_t tt0 = 0;

        list<samp_save>::iterator si = samps.begin();

        if (si != samps.end()) tt0 = si->samp->getTimeTag();

        for (n = 0; si != samps.end(); ++si,n++) {

            samp_save& save = *si;
            Sample* samp = const_cast<Sample*>(save.samp);

            dsm_time_t tt = samp->getTimeTag();
            if (!offsets.empty()) {
                // create a key containing the sample number
                pair<long long,int> p(save.dsmSampleNumber,0);

                // find iterator pointing to first element > key, giving us the
                // timetag of the sample with record number > save.dsmSampleNumber.
                set<pair<long long,int>,SequenceComparator>::const_iterator i1 = offsets.upper_bound(p);

                // extrapolate backwards if first element is > key
                if (i1 == offsets.begin()) ++i1;   

                set<pair<long long,int>,SequenceComparator>::const_iterator i0 = i1;
                // since offsets is not empty, i0 is a valid iterator after this decrement
                --i0;

                if (i1 != offsets.end()) {

                    // if the clock correction takes a big jump, then discard this sample,
                    // because we don't know which side of the jump it should be
                    // associated with.
                    if (abs((i1->second - i0->second)) > USECS_PER_SEC / 4) {
                        samp->freeReference();
                        ndiscards++;
                        continue;
                    }
                    int dt = i0->second +
                        (int)((i1->second - i0->second) / (float)(i1->first - i0->first) *
                        (save.dsmSampleNumber - i0->first));
                    tt += dt;
#ifdef DEBUG
                    cerr << samp->getDSMId() << ',' << samp->getSpSId() << ": " <<
                        save.dsmSampleNumber << ',' << samp->getTimeTag() << 
                        ", i0=" << i0->first << ',' << i0->second <<
                        ", i1=" << i1->first << ',' << i1->second <<
                        ", dt=" << dt <<
                        ", recdiff = " << i1->first - i0->first << 
                        ", timediff = " << i1->second - i0->second <<
                        ", recdiff = " << save.dsmSampleNumber - i0->first << endl;
#endif
                }
            }

            int dt = tt - samp->getTimeTag();

            if (dt < 0) maxneg = std::min(dt,maxneg);
            else if (dt > 0) maxpos = std::max(dt,maxpos);

            samp->setTimeTag(tt);
            _sorter.receive(samp);
            samp->freeReference();
        }
        if (!samps.empty()) {
            dsm_sample_id_t id = 0;
            id = SET_DSM_ID(id,dsmid);
            cout << formatTime(tt0) << ' ' <<
                formatId(id) << ' ' <<
                setw(6) << n << ' ' <<
                setw(4) << ndiscards << ' ' <<
                setw(10) << fixed << setprecision(2) << 0 << ' ' <<
                setw(10) << 0 << ' ' <<
                setw(10) << 0 << ' ' <<
                setw(10) << maxneg << ' ' << 
                setw(10) << maxpos <<  ' ' << endl;
        }
        samps.clear();
        offsets.clear();
    }
}

void TT_Adjust::fixed_fit_output()
{
    map<dsm_sample_id_t,FixedRateSensor>::iterator fi =  _fixedRateSensors.begin();
    for ( ; fi != _fixedRateSensors.end(); ++fi) {
        FixedRateSensor& fix = fi->second;
        fix.fitAndOutput();
    }
}

int TT_Adjust::run() throw()
{

    try {
	nidas::core::FileSet* outSet = 0;
        if (_outputFileName.find(".bz2") != string::npos)
            outSet = new nidas::core::Bzip2FileSet();
        else
            outSet = new nidas::core::FileSet();

	outSet->setFileName(_outputFileName);
	outSet->setFileLengthSecs(_outputFileLength);

        RawSampleOutputStream outStream(outSet);

        _sorter.setHeapMax(1000 * 1000 * 1000);
        _sorter.setHeapBlock(true);
        _sorter.addSampleClient(&outStream);
        _sorter.setLengthSecs(1.1 * _fitUsecs / USECS_PER_SEC);
        _sorter.start();

	nidas::core::FileSet* orphSet = 0;
        if (_unmatchedFileName.find(".bz2") != string::npos)
            orphSet = new nidas::core::Bzip2FileSet();
        else
            orphSet = new nidas::core::FileSet();
	orphSet->setFileName(_unmatchedFileName);
	orphSet->setFileLengthSecs(_unmatchedFileLength);
        _unmatchedOutput.connected(orphSet);

        nidas::core::FileSet* fset;
        if (_inputFileNames.size() > 1) {
            fset = nidas::core::FileSet::getFileSet(_inputFileNames);
        }
        else {
            if (_inputFileNames.front().find(".bz2") != string::npos) {
#ifdef HAS_BZLIB_H
                fset = new nidas::core::Bzip2FileSet();
#else
                cerr << "Sorry, no support for Bzip2 files on this system" << endl;
                exit(1);
#endif
            }
            else {
                fset = new nidas::core::FileSet();
            }
            fset->setFileName(_inputFileNames.front());
            if (_startTime.toUsecs() != 0) fset->setStartTime(_startTime);
            if (_endTime.toUsecs() != 0) fset->setEndTime(_endTime);
        }

        dsm_time_t fitEndTime = 0;

        // SampleInputStream owns the fset ptr.
        RawSampleInputStream input(fset);

        // save header for later writing to output
        try {
            input.readInputHeader();
        }
        catch(const n_u::EOFException& e) {
            cerr << e.what() << endl;
            outStream.close();
            _unmatchedOutput.close();
            return 1;
        }

        HeaderSrc hsrc(input.getInputHeader());
        outStream.setHeaderSource(&hsrc);
        _unmatchedOutput.setHeaderSource(&hsrc);

        unsigned long lastVsize = 0;

        for (;;) {
            Sample* samp;
            try {
                samp = input.readSample();
            }
            catch(const n_u::EOFException& e) {
                break;
            }
            if (_interrupted) break;

            dsm_time_t tt = samp->getTimeTag();
            dsm_sample_id_t inid = samp->getId();

            long long dsmSampleNumber = _dsmSampleNumbers[GET_DSM_ID(inid)]++;

#ifdef DEBUG
            if (!(dsmSampleNumber % 10000))
                cerr << "sample num for dsm " << GET_DSM_ID(inid) << "=" <<
                        _dsmSampleNumbers[GET_DSM_ID(inid)] << endl;
#endif
            if (tt > fitEndTime) {
                //  if all csats have only one fold, then fit and output
                //      them all
                //  one or more with multiple folds:
                //      detect unmatched folds, if last time of a fold
                //      is old, splice it to fold[0]
                //  if one or more csats still have multiple folds,
                //      then wait to fit and output
                bool allSingleFolds = true;
                map<dsm_sample_id_t,CSAT3Sensor>::iterator ci = _csats.begin();

                for ( ; ci != _csats.end(); ++ci) {
                    CSAT3Sensor& csat = ci->second;
                    int nfold = csat.getNfolds();
                    if (nfold > 1) nfold = csat.handleUnmatchedFolds(tt);
                    if (nfold > 1) {
#define DEBUG_FOLDS
#ifdef DEBUG_FOLDS
                        cerr << "warning: " << formatTime(tt) <<
                            " id=" << formatId(ci->first) <<
                            " tt > fitEndTime and nfold=" << nfold << endl;
                            list<CSAT3Fold>::const_iterator fi = csat.getFoldsBegin();
                        int ifold;
                        for (ifold = 0; fi != csat.getFoldsEnd(); ++fi,ifold++) {
                            const CSAT3Fold& fold = *fi;
                            assert(!fold.empty());
                            cerr << formatId(csat.getId()) << ", fold=" << ifold << ", not matched=" << fold.getNotMatched() << endl;
                        }
#undef DEBUG_FOLDS
#endif
                        allSingleFolds = false;
                    }
                }
                if (allSingleFolds) {
                    ci = _csats.begin();
                    for ( ; ci != _csats.end(); ++ci) {
                        CSAT3Sensor& csat = ci->second;
                        csat.fitAndOutput();
                    }
                    fixed_fit_output();
                    output_other();
                    // only increment fitEndTime if all single folds
                    fitEndTime = tt + _fitUsecs - (tt % _fitUsecs);

                    // output some memory info
                    unsigned long vsize = n_u::Process::getVMemSize();
                    cout << "virtual memory=" << vsize/1000/1000 << " Mbytes" <<
                        ", increase=" << (signed)(vsize - lastVsize)/1000 << " kbytes" <<
                        ", maxRSS=" << n_u::Process::getMaxRSS()/1000/1000 << " Mbytes" << endl;
                    std::list<SamplePoolInterface*> pools = SamplePools::getInstance()->getPools();
                    std::list<SamplePoolInterface*>::const_iterator pi = pools.begin();
                    for ( ; pi != pools.end(); ++pi) {
                        unsigned long nout = (*pi)->getNSamplesOut();
                        unsigned long ssize = _sorter.size();
                        cout << "samp alloc=" << (*pi)->getNSamplesAlloc()/1000 << "K" << 
                            ", out=" << nout << 
                            ", smallin=" << (*pi)->getNSmallSamplesIn() << 
                            ", mediumin=" << (*pi)->getNMediumSamplesIn() << 
                            ", largein=" << (*pi)->getNLargeSamplesIn() <<
                            ", sorter.size=" << ssize << ", out-sorter=" << (nout - ssize) << endl;
                    }
                    lastVsize = vsize;
                }
#ifdef DEBUG
                else {
                    ci = _csats.begin();
                    for ( ; ci != _csats.end(); ++ci) {
                        CSAT3Sensor& csat = ci->second;
                        if (csat.getNfolds() > 1)
                            cout << formatTime(fitEndTime) << formatId(ci->first) <<
                                ", nfolds=" << csat.getNfolds() << endl;
                    }
                }
#endif
            }

            map<dsm_sample_id_t,CSAT3Sensor>::iterator ci = _csats.find(inid);
            if (ci != _csats.end()) {
                CSAT3Sensor& csat = ci->second;
                csat.addSample(samp,dsmSampleNumber);
                continue;
            }

            map<dsm_sample_id_t,FixedRateSensor>::iterator fi = _fixedRateSensors.find(inid);
            if (fi != _fixedRateSensors.end()) {
                FixedRateSensor& fixed = fi->second;
                fixed.addSample(samp,dsmSampleNumber);
                continue;
            }
            struct samp_save save;
            save.samp = samp;
            save.dsmSampleNumber = dsmSampleNumber;
            _other_samples[samp->getDSMId()].push_back(save);
        }
        if (!_interrupted) {
            map<dsm_sample_id_t,CSAT3Sensor>::iterator ci = _csats.begin();
            for ( ; ci != _csats.end(); ++ci) {
                CSAT3Sensor& csat = ci->second;
                csat.matchFoldsToFold0();
                csat.fitAndOutput();
            }
            fixed_fit_output();
            output_other();
        }
        _sorter.finish();
        outStream.close();
        _unmatchedOutput.finish();
        _unmatchedOutput.close();
    }
    catch (n_u::IOException& ioe) {
        cerr << ioe.what() << endl;
	return 1;
    }
    return 0;
}

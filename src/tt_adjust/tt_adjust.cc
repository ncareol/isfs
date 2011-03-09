// -*- mode: C++; indent-tabs-mode: nil; c-basic-offset: 4; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
/*
 ********************************************************************
    Copyright 2005 UCAR, NCAR, All Rights Reserved

    $LastChangedDate: 2010-10-14 15:01:57 -0600 (Thu, 14 Oct 2010) $

    $LastChangedRevision: 5772 $

    $LastChangedBy: tbaltzer $

    $HeadURL: http://svn.eol.ucar.edu/svn/nidas/trunk/src/nidas/apps/nidsmerge.cc $
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
#include <nidas/util/UTime.h>

#include <iomanip>
#include <limits>

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

string formatTime(dsm_time_t tt)
{
    return n_u::UTime(tt).format(true,"%Y %m %d %H:%M:%S.%3f");
}

/*
 * Least squares fitting code taken from GNU Scientific Library (gsl),
 * function gsl_fit_linear.  That function computes the fit in one call,
 * if passed x and y input arguments of double arrays. In order
 * to avoid creating separate x and y arrays, and instead add x,y values
 * to the sums as they are determined, we split the function into several
 * passes, using a struct to hold the results of the sums:
 *
 * initialize the gsl_fit_sums structure.
 * gsl_fit_init(struct gsl_fit_sums*)
 *
 * add an x,y point to the sums
 * gsl_fit_linear_add_point(double x,double y,struct gsl_fit_sums*)
 *
 * compute the fit: y = c0 + c1 * x
 * gsl_fit_linear_compute(struct gsl_fit_sums*,  double *c0, double *c1)
 *
 * To compute the sum of the residuals, one must pass over the data
 * again, after computing the above results:
 * gsl_fit_linear_add_resid(double x, double y,struct gsl_fit_sums* sums)
 *
 * Compute the covariance matrix and residuals
 * gsl_fit_linear_compute_resid(struct gsl_fit_sums* sums,
 *   double *cov_00, double *cov_01, double *cov_11, double *sumsq)
 *
 */

void gsl_fit_init(struct gsl_fit_sums* sums)
{
    memset(sums,0,sizeof(*sums));
}

void gsl_fit_linear_add_point(double x, double y,struct gsl_fit_sums* sums)
{
    sums->x += x;
    sums->y += y;
    sums->xx += x * x;
    sums->xy += x * y;
    sums->n++;
}

void gsl_fit_linear_compute(struct gsl_fit_sums* sums, double *c0, double *c1)
{
    sums->x /= sums->n;
    sums->y /= sums->n;
    sums->xx = sums->xx / sums->n - sums->x * sums->x;
    sums->xy = sums->xy / sums->n - sums->x * sums->y;

    double b = sums->xy / sums->xx;
    double a = sums->y - sums->x * b;
    *c0 = a;
    sums->c1 = *c1 = b;
}

void gsl_fit_linear_add_resid(double x, double y,struct gsl_fit_sums* sums)
{
    double dx = x - sums->x;
    double dy = y - sums->y;
    double d = dy - sums->c1 * dx;
    sums->d2 += d * d;
}

void gsl_fit_linear_compute_resid(struct gsl_fit_sums* sums,
     double *cov_00, double *cov_01, double *cov_11, double *sumsq)
{
    double s2;
    s2 = sums->d2 / (sums->n - 2.0);        /* chisq per degree of freedom */

    *cov_00 = s2 * (1.0 / sums->n) * (1 + sums->x * sums->x / sums->xx);
    *cov_11 = s2 * 1.0 / (sums->n * sums->xx);
    *cov_01 = s2 * (-sums->x) / (sums->n * sums->xx);
    *sumsq = sums->d2;
}

/*
 * Function to do a least squares fit of the time tags of a
 * list of samples to the sample number.
 */
size_t do_fit(list<samp_save>& samps,gsl_fit_sums& sums,
        double* a, double* b, double* cov_00, double* cov_01, double* cov_11,
        double* sumsq)
{
    if (samps.empty()) return 0;

    list<samp_save>::const_iterator si;

    // sum the samples if it hasn't been done
    if (sums.n == 0) {
        si = samps.begin();
        dsm_time_t tt0 =  si->samp->getTimeTag();
        for (size_t n = 0; si != samps.end(); ++si,n++) {
            const samp_save& save = *si;
            const Sample* samp = save.samp;
            double ttx =  samp->getTimeTag() - tt0;
            gsl_fit_linear_add_point((double)n,ttx, &sums);
        }
    }

    gsl_fit_linear_compute(&sums,a,b);

    si = samps.begin();
    dsm_time_t tt0 =  si->samp->getTimeTag();
    for (unsigned int n = 0; si != samps.end(); ++si,n++) {
        const samp_save& save = *si;
        const Sample* samp = save.samp;
        double ttx =  samp->getTimeTag() - tt0;
        gsl_fit_linear_add_resid((double)n,ttx,&sums);
    }

    gsl_fit_linear_compute_resid(&sums,cov_00, cov_01, cov_11, sumsq);
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
    }
    _samples.push_back(save);
    _lastTime = samp->getTimeTag();
    _lastSeq = cseq;

    // cerr << "addSample, id=" << formatId(samp->getId()) << " time=" <<
      //       formatTime(_lastTime) << endl;
}

CSAT3Sensor::CSAT3Sensor(TT_Adjust* adjuster, dsm_sample_id_t id,double rate):
    _adjuster(adjuster),_id(id),_rate(rate),_lastTime(0),_lastFitTime(0),
    _nfolds(0),_unmatchedFold0Counter(0),_maxNumFolds(0)
{
}

/* extract the CSAT3 sequence number from a sample */
int CSAT3Sensor::getSequenceNumber(const Sample* samp)
{
    size_t inlen = samp->getDataByteLength();
    if (inlen < 12) return -1;       // not enough data

    const char* dptr = (const char*) samp->getConstVoidDataPtr();

    // check for correct termination bytes
    if (dptr[inlen-2] != '\x55' || dptr[inlen-1] != '\xaa') return -1;

    return (dptr[8] & 0x3f);
}

void CSAT3Sensor::addSample(const Sample* samp, long long dsmSampleNumber)
{
    int cseq = getSequenceNumber(samp);

    if (cseq < 0) return;

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
        if (!_folds.empty()) {
            list<CSAT3Fold>::iterator fi = _folds.begin();
            CSAT3Fold& fold0 = *fi++;
            for ( ; fi != _folds.end(); ) {
                CSAT3Fold& fold = *fi;
                cerr << "id=" << formatId(_id) << "MAJOR_DATA_GAP, nfolds=" << _nfolds << ", splicing fold " << 1 <<
                    " to fold 0, dt=" << dt << endl;
                fold0.append(fold);
                fi = _folds.erase(fi);
                _nfolds--;
                _unmatchedFold0Counter = 0;
            }
            assert(_nfolds == 1);
            fitAndOutput();
        }
    }

    _lastTime = tt;

    spliceAbandonedFolds(tt);

    // Find a fold that this sample belongs to by checking
    // whether its csat3 sequence number is one greater than
    // the sequence number of the last sample in the fold.

    list<CSAT3Fold>::iterator matchingFold = _folds.end();
    int mfold = -1;

    long long closestDt = OLD_FOLD_MAX_DT;

    list<CSAT3Fold>::iterator fi = _folds.begin();
    for (int ifold = 0; fi != _folds.end(); ++fi,ifold++) {
        CSAT3Fold& fold = *fi;
        if (cseq == ((fold.getLastSeq() + 1) % 64) && dt < closestDt) {
            matchingFold = fi;
            mfold = ifold;
            closestDt = dt;
        }
    }
    
    // no match, add a fold
    if (matchingFold == _folds.end()) {
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

    _maxNumFolds = std::max(_maxNumFolds,_nfolds);

    if (mfold == 0) _unmatchedFold0Counter = 0;
    else {
        if (++_unmatchedFold0Counter > _nfolds) {
            // we're no longer getting samples for fold 0. 
            // It may have ended.
            // There must be at least 2 folds
            // Look for a fold to splice to fold 0;

            assert(_nfolds > 1);
            assert(!_folds.empty());

            dsm_time_t toldest = tt;
            matchingFold = _folds.end();
            mfold = -1;

            list<CSAT3Fold>::iterator fi = _folds.begin();
            CSAT3Fold& fold0 = *fi++;
            assert(!fold0.empty());

            // find oldest matching fold
            for (int ifold = 1; fi != _folds.end(); ++fi,ifold++) {
                CSAT3Fold& fold = *fi;
                assert(!fold.empty());
                if (fold.getFirstSeq() == (fold0.getLastSeq() + 1) % 64) {
                    if (fold.getFirstTime() < toldest) {
                        matchingFold = fi;
                        mfold = ifold;
                        toldest = fold.getFirstTime();
                    }
                }
            }
            if (matchingFold != _folds.end()) {
#ifdef DEBUG
                cerr << formatTime(matchingFold->getFirstTime()) <<
                    ", id=" << formatId(_id) <<
                    ", #unmatched=" << _unmatchedFold0Counter <<
                    ", nfolds=" << _nfolds <<
                    ", splicing matched fold " << mfold <<
                    " to fold 0, f0 seq=0x" << hex << fold0.getLastSeq() << dec <<
                    ", fold " << mfold << " seq=0x" << hex << matchingFold->getFirstSeq() << dec << endl;
#endif
                fold0.append(*matchingFold);
                _folds.erase(matchingFold);
                _nfolds--;
                _unmatchedFold0Counter = 0;
            } else if (_unmatchedFold0Counter > 20) {
                // only force an unmatched fold splice to fold 0
                // there has been a long period of no fold0 samples
                // find oldest fold
                dsm_time_t toldest = tt;
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
                    cerr << formatTime(matchingFold->getFirstTime()) <<
                        ", id=" << formatId(_id) <<
                        ", #unmatched=" << _unmatchedFold0Counter <<
                        ", nfolds=" << _nfolds <<
                        ", splicing unmatched fold " << mfold <<
                        " to fold 0, f0 seq=0x" << hex << fold0.getLastSeq() << dec <<
                        ", fold " << mfold << " seq=0x" << hex << matchingFold->getFirstSeq() << dec << endl;
                    fold0.append(*matchingFold);
                    _folds.erase(matchingFold);
                    _nfolds--;
                    _unmatchedFold0Counter = 0;
                }
            }
        }
    }
}

size_t CSAT3Sensor::fitAndOutput()
{
    // may be called without any data.
    if (_nfolds == 0) return 0;

    assert(_nfolds == 1);

    list<CSAT3Fold>::iterator fi = _folds.begin();
    CSAT3Fold& fold0 = *fi++;
    assert(fi == _folds.end());

    if (fold0.empty()) return 0;

    double a,b; /* y = a + b * x */
    double cov_00, cov_01, cov_11, sumsq;

    struct gsl_fit_sums sums;

    size_t n = do_fit(fold0.getSamples(),sums,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
    if (n == 0) return n;

    int maxneg,maxpos;
    dsm_time_t tout = fold0.getFirstTime();
    dsm_time_t tlastfit = _lastFitTime;
    dsm_time_t tfirst,tlast;
    _adjuster->writeSamples(fold0.getSamples(),a,b,&maxneg,&maxpos,tfirst,tlast);
    cout << 
        formatTime(fold0.getFirstTime()) << ' ' <<
        formatId(_id) << ' ' <<
        setw(6) << n << ' ' <<
        setw(10) << fixed << setprecision(2) << a << ' ' <<
        setw(10) << b << ' ' <<
        setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
        setw(10) << maxneg << ' ' << 
        setw(10) << maxpos <<  ' ' <<
        setw(10) << (tlastfit == 0 ? 0 : tfirst-tlastfit) << ' ' <<
        setw(3) << _maxNumFolds << endl;
    _lastFitTime = tlast;
    _maxNumFolds = 0;
    _unmatchedFold0Counter = 0;
    return n;
}

int CSAT3Sensor::spliceAbandonedFolds(dsm_time_t tt)
{

    if (_folds.empty()) {
        assert(_nfolds == 0);
        return _nfolds;
    }
    assert(_nfolds > 0);

    // splice abandoned folds to fold0, oldest first
    list<CSAT3Fold>::iterator matchfi = _folds.begin();

    while (_nfolds > 1 && matchfi != _folds.end()) {

        dsm_time_t oldestTime = std::numeric_limits<long long>::max();
        matchfi = _folds.end();

        list<CSAT3Fold>::iterator fi = _folds.begin();
        CSAT3Fold& fold0 = *fi++;

        int ifold;
        for (ifold = 1; fi != _folds.end(); ++fi,ifold++) {
            CSAT3Fold& fold = *fi;

            // folds other than 0 should not be empty
            assert(!fold.empty());

            long long dt = tt - fold.getLastTime();
            if (dt > OLD_FOLD_MAX_DT) {
                if (fold.getFirstTime() < oldestTime) {
                    cerr << "dt=" << dt << " ifold=" << ifold << endl;
                    matchfi = fi;
                    oldestTime = fold.getFirstTime();
                }
            }
        }
        if (matchfi != _folds.end()) {
            cerr << formatTime(matchfi->getLastTime()) << ", id=" << formatId(_id) <<
                "splicing abandoned fold " << ifold <<
            " to fold 0, dt=" << (tt - matchfi->getLastTime()) << endl;

            fold0.append(*matchfi);
            _folds.erase(matchfi);
            _nfolds--;
            _unmatchedFold0Counter = 0;
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
        fold0.append(*matchfi);
        _folds.erase(matchfi);
        _nfolds--;
        _unmatchedFold0Counter = 0;
    }
    return _nfolds;
}

FixedRateSensor::FixedRateSensor(TT_Adjust* adjuster,dsm_sample_id_t id, double rate):
    _adjuster(adjuster),_id(id),_rate(rate),_sampleDt((int)rint(USECS_PER_SEC / _rate)),
    _nsamp(0),_firstTime(0),_lastTime(0),_lastFitTime(0)
{
}

void FixedRateSensor::addSample(const Sample* samp, long long dsmSampleNumber)
{

    dsm_time_t tt = samp->getTimeTag();
    dsm_time_t ttx = 0;

    // check for a data gap. If so, fit and output the samples we have.
    if (!_samples.empty()) {
        int dt = tt - _lastTime;
        if (abs(dt) > 64 * _sampleDt) fitAndOutput();
    }
    if (_samples.empty()) {
        _firstTime = samp->getTimeTag();
        _nsamp = 0;
    }
    ttx =  tt - _firstTime;
    _lastTime = tt;

    samp_save save;
    save.samp = samp;
    save.dsmSampleNumber = dsmSampleNumber;
    _samples.push_back(save);

    // cerr << "ttx=" << ttx << " sampleNumber=" << sampleNumber << " cseq=" << cseq << endl;
    gsl_fit_linear_add_point(_nsamp++,(double)ttx, &_sums);
}

size_t FixedRateSensor::fitAndOutput()
{
    size_t n;
    double a,b; /* y = a + b * x */
    double cov_00, cov_01, cov_11, sumsq;

    n = do_fit(_samples,_sums,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
    if (n > 0) {
        int maxneg,maxpos;
        dsm_time_t tlastfit = _lastFitTime;
        dsm_time_t tfirst,tlast;
        _adjuster->writeSamples(_samples,a,b,&maxneg,&maxpos,tfirst,_lastFitTime);
        cout << 
            formatTime(_firstTime) << ' ' <<
            formatId(_id) << ' ' <<
            setw(6) << n << ' ' <<
            setw(10) << fixed << setprecision(2) << a << ' ' <<
            setw(10) << b << ' ' <<
            setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
            setw(10) << maxneg << ' ' << 
            setw(10) << maxpos << ' ' <<
            setw(10) << (tlastfit == 0 ? 0 : tfirst-tlastfit) << endl;
    }
    return n;
}

int main(int argc, char** argv)
{
    return TT_Adjust::main(argc,argv);
}

/* static */
bool TT_Adjust::_interrupted = false;

TT_Adjust::TT_Adjust():
    _fitUsecs(30*USECS_PER_SEC),_outputFileLength(0),_csatRate(0.0),
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

    while ((opt_char = getopt(argc, argv, "c:f:l:o:r:s:")) != -1) {
	switch (opt_char) {
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
	case 'f':
            cp = optarg;
	    _fitUsecs = (long long)(strtod(cp,&cp2) * USECS_PER_SEC);
            if (cp2 == cp) return usage(argv[0]);
	    break;
	case 'l':
	    _outputFileLength = atoi(optarg);
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
	case '?':
	    return usage(argv[0]);
	}
    }
    if (_outputFileName.length() == 0) return usage(argv[0]);

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
Usage: " << argv0 << "-o output [-l output_file_length] [-r secs_per_fit]\n\n\
        input ...\n\
    -o output: output file name or file name format\n\
    -l output_file_length: length of output files, in seconds\n\
    -r secs_per_fit: time period of least squares fit of timetags to record number\n\
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

void TT_Adjust::output_other()
{
    int maxneg = 0;
    int maxpos = 0;

    list<samp_save>::iterator si = _other_samples.begin();
    size_t ndiscards = 0;
    size_t n;
    for (n = 0; si != _other_samples.end(); ++si,n++) {
        samp_save& save = *si;
        Sample* samp = const_cast<Sample*>(save.samp);
        unsigned int dsmid = samp->getDSMId();

        set<pair<long long,int>,SequenceComparator>& offsets = _clockOffsets[dsmid];

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
    if (!_other_samples.empty())
            cout << "output other, n=" << n << ", ndiscards=" << ndiscards << ", maxneg=" << maxneg << ", maxpos=" << maxpos << endl;
    _other_samples.clear();
    _clockOffsets.clear();
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

        nidas::core::FileSet* fset = nidas::core::FileSet::getFileSet(_inputFileNames);

        dsm_time_t endTime = 0;

        // SampleInputStream owns the fset ptr.
        RawSampleInputStream input(fset);

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
            if (tt > endTime) {
                //  if all csats have only one fold, then fit and output
                //      them all
                //  one or more with multiple folds:
                //      detect abandoned folds, if last time of a fold
                //      is old, splice it to fold[0]
                //  if one or more csats still have multiple folds,
                //      then wait to fit and output
                bool allSingleFolds = true;
                map<dsm_sample_id_t,CSAT3Sensor>::iterator ci = _csats.begin();

                for ( ; ci != _csats.end(); ++ci) {
                    CSAT3Sensor& csat = ci->second;
                    int nfold = csat.spliceAbandonedFolds(tt);
                    if (nfold > 1) {
                        cerr << "after spliceAbandonedFolds(), id=" <<
                            formatId(ci->first) << " nfold=" << nfold << endl;
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
                    // only increment endTime if all single folds
                    endTime = tt + _fitUsecs - (tt % _fitUsecs);
                }
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
            _other_samples.push_back(save);
        }
        if (!_interrupted) {
            map<dsm_sample_id_t,CSAT3Sensor>::iterator ci = _csats.begin();
            for ( ; ci != _csats.end(); ++ci) {
                CSAT3Sensor& csat = ci->second;
                csat.spliceAllFolds();
                csat.fitAndOutput();
            }
            fixed_fit_output();
            output_other();
            _sorter.finish();
        }
        outStream.close();
    }
    catch (n_u::IOException& ioe) {
        cerr << ioe.what() << endl;
	return 1;
    }
    return 0;
}

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

 Adjust timetags on nidas samples from sensors which have a constant sampling rate,
 by doing a least squares fit to the linear relationship between x=record number
 and y=timetag.

 If the input data has been sorted after it was sampled and time tagged,
 and the original timetags were bad enough such that the sequence of
 samples from a sensor were re-ordered, then this algorithm does not
 fix that issue, except in the case of samples from a CSAT3 sonic,
 which contain an internal sequence number, 0-63. If the
 CSAT3 samples were not re-ordered by more than 32 samples, then it is 
 straightforward to use the sequence number to determine the original
 sample sequence and generate the timetags from the least squares fit.
*/

#define HAS_BZLIB_H
#include <nidas/core/FileSet.h>
#include <nidas/core/Bzip2FileSet.h>
#include <nidas/dynld/RawSampleInputStream.h>
#include <nidas/dynld/RawSampleOutputStream.h>
#include <nidas/util/EOFException.h>
#include <nidas/util/UTime.h>

#include <iomanip>

/* Test with different containers:
 * list:
 * real    0m33.680s
 * user    0m34.784s
 * sys     0m7.931s
 * deque:
 * real    0m31.659s
 * user    0m32.446s
 * sys     0m7.814s
 */
#define sampcontainer list
#include <deque>
#include <list>

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

/**
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

/** extract the CSAT3 sequence number from a sample */
int get_csat_seq(const Sample* samp)
{
    size_t inlen = samp->getDataByteLength();
    if (inlen < 12) return -1;       // not enough data

    const char* dptr = (const char*) samp->getConstVoidDataPtr();

    // check for correct termination bytes
    if (dptr[inlen-2] != '\x55' || dptr[inlen-1] != '\xaa') return -1;

    return (dptr[8] & 0x3f);
}

class TT_Adjust
{
public:

    /** information to save with each data sample */
    struct samp_save {
        const Sample* samp;

        /** sequence number 0-63 found in CSAT3 sample */
        int seq;

        /**
         * Sample number for a given sensor, used to
         * as the x value in the fit of sample timetags.
         * Reset to 0 at the beginning of each fit period.
         */
        size_t sampleNumber;

        /**
         * sample number from its DSM
         */
        long long dsmSampleNumber;

        samp_save() : samp(0),seq(0),sampleNumber(0),dsmSampleNumber(0) {}

    };

    TT_Adjust();

    int parseRunstring(int argc, char** argv);

    int run() throw();

    static void sigAction(int sig, siginfo_t* siginfo, void* vptr);

    static void setupSignals();

    static int main(int argc, char** argv) throw();

    static int usage(const char* argv0);

    size_t do_fit(sampcontainer<samp_save>& samps,gsl_fit_sums& sums,
            double* a, double* b,
            double* cov_00, double* cov_01, double* cov_11, double* sumsq);

    /**
     * Do a linear least squares fit of record number to CSAT3 timetags,
     * output the newly timetagged samples.
     */
    size_t csat_fit_output(dsm_sample_id_t id, sampcontainer<samp_save>& samps,SampleClient&);

    /**
     * Do a linear least squares fit of record number to timetags of a fixed
     * rate sensor, output the newly timetagged samples.
     */
    void fixed_fit_output(SampleClient&);

    /**
     * Adjust the timetags on a sampcontainer of samples, using the coefficients
     * of the least squares fit, and send them to a SampleClient,
     * Clears the sampcontainer when done.
     */
    size_t write_samps(sampcontainer<samp_save>& samps,SampleClient& out,
            double a, double b,int* maxnegp, int* maxposp);

    /**
     * Output the other sensors that haven't been designated as CSAT3 or fixed
     * rate, using the adjusted time tags of the CSAT and fixed rate sensors.
     */
    void output_other(SampleClient& out);

private:

    static bool _interrupted;

    list<string> _inputFileNames;

    string _outputFileName;

    long long _fitUsecs;

    int _outputFileLength;

    /**
     * Rate of CSAT3 sonics.
     */
    double _csatRate;

    /**
     * Parsed from the runstring, the ids of the CSAT3s and their rates.
     */
    map<dsm_sample_id_t,double> _csatRates;

    /**
     * Parsed from the runstring, the ids and rates of the fixed rate sensors.
     */
    map<dsm_sample_id_t,double> _fixedRates;

    /**
     * information to save with each CSAT3 data sample
     * */
    map<dsm_sample_id_t, sampcontainer<samp_save> > _csat_samples;

    /** 
     * least squares sums for each CSAT3
     */
    map<dsm_sample_id_t, gsl_fit_sums> _csat_sums;

    /**
     * information to save with each non-CSAT3 data sample with a fixed rate
     */
    map<dsm_sample_id_t, sampcontainer<samp_save> > _fixed_rate_samples;

    /**
     * least squares sums for each non-CSAT3 with a fixed rate
     */
    map<dsm_sample_id_t, gsl_fit_sums> _fixed_sums;

    /**
     * The sampcontainer of other samples to be output after the fits of CSAT3 and other fixed
     * rate sensors.
     */
    sampcontainer<samp_save> _other_samples;

    /**
     * counter of current sample number of each dsm.
     */
    map<unsigned int, long long> _dsmSampleNumbers;

    /**
     * Comparator class for a set of record number,time tag pairs, sorting
     * on the record number.
     */
    class SequenceComparator {
    public:
        /**
         * return true if x is less than y.
         */
        bool operator() (const pair<size_t,dsm_time_t>& x,
                const pair<size_t,dsm_time_t>& y) const {
            return x.first < y.first;
        }
    };

    /**
     * Sequence of record numbers and determined value of the clock error
     */
    map<unsigned int, set<pair<long long,int>,SequenceComparator> > _clockOffsets;

};

int main(int argc, char** argv)
{
    return TT_Adjust::main(argc,argv);
}

/* static */
bool TT_Adjust::_interrupted = false;

TT_Adjust::TT_Adjust():
	_fitUsecs(30*USECS_PER_SEC),_outputFileLength(0),_csatRate(0.0)
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
                _csatRates[id] = rate;
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

                _fixedRates[id] = rate;
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
    map<dsm_sample_id_t,double>::iterator ci = _csatRates.begin();
    for ( ; ci != _csatRates.end(); ++ci) {
        pair<const dsm_sample_id_t,double>& p = *ci;
        if (p.second == 0.0) p.second = _csatRate;
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

size_t TT_Adjust::do_fit(sampcontainer<samp_save>& samps,gsl_fit_sums& sums,
        double* a, double* b, double* cov_00, double* cov_01, double* cov_11,
        double* sumsq)
{
    if (samps.empty()) return 0;

    sampcontainer<samp_save>::const_iterator si = samps.begin();
    dsm_time_t tt0 =  si->samp->getTimeTag();
    for (size_t n = 0; si != samps.end(); ++si,n++) {
        const samp_save& save = *si;
        const Sample* samp = save.samp;
        double ttx =  samp->getTimeTag() - tt0;
        gsl_fit_linear_add_point((double)n,ttx, &sums);
    }


    gsl_fit_linear_compute(&sums,a,b);

    si = samps.begin();
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

size_t TT_Adjust::write_samps(sampcontainer<samp_save>& samps, SampleClient& out,
        double a, double b,int* maxnegp, int* maxposp)
{
    int maxneg = 0;
    int maxpos = 0;
    size_t n;

    sampcontainer<samp_save>::iterator si = samps.begin();
    dsm_time_t tt0 =  si->samp->getTimeTag();
    for (n = 0; si != samps.end(); ++si,n++) {
        samp_save& save = *si;
        
        // cast away const so we can change the time tag.
        Sample* samp = const_cast<Sample*>(save.samp);

        dsm_time_t newtt = tt0 + (dsm_time_t) (a + n * b + 0.5);
        int dt = newtt - samp->getTimeTag();

        if (dt < 0) maxneg = std::min(dt,maxneg);
        else if (dt > 0) maxpos = std::max(dt,maxpos);

        samp->setTimeTag(newtt);
        out.receive(samp);

        unsigned int dsmid = samp->getDSMId();
        _clockOffsets[dsmid].insert(make_pair<long long,int>(save.dsmSampleNumber,dt));

        samp->freeReference();
    }
    samps.clear();
    *maxnegp = maxneg;
    *maxposp = maxpos;
    return n;
}

void TT_Adjust::output_other(SampleClient& out)
{
    int maxneg = 0;
    int maxpos = 0;

    sampcontainer<samp_save>::iterator si = _other_samples.begin();
    for ( ; si != _other_samples.end(); ++si) {
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
                int dt = i0->second +
                    (i1->second - i0->second) / (signed)(i1->first - i0->first) *
                    (signed)(save.dsmSampleNumber - i0->first);
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
        out.receive(samp);
        samp->freeReference();
    }
    if (!_other_samples.empty())
            cout << "output other, maxneg=" << maxneg << " maxpos=" << maxpos << endl;
    _other_samples.clear();
    _clockOffsets.clear();
}

size_t TT_Adjust::csat_fit_output(dsm_sample_id_t id, sampcontainer<samp_save>& samps,SampleClient& sorter)
{
    double a,b; /* y = a + b * x */
    double cov_00, cov_01, cov_11, sumsq;

    struct gsl_fit_sums sums;

    size_t n = do_fit(samps,sums,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
    if (n == 0) return n;

    int maxneg,maxpos;
    dsm_time_t tout = samps.front().samp->getTimeTag();
    write_samps(samps,sorter,a,b,&maxneg,&maxpos);
    cout << 
        formatTime(tout) << ' ' <<
        formatId(id) << ' ' <<
        setw(6) << n << ' ' <<
        setw(10) << fixed << setprecision(2) << a << ' ' <<
        setw(10) << b << ' ' <<
        setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
        setw(10) << maxneg << ' ' << 
        setw(10) << maxpos << endl;
    return n;
}

void TT_Adjust::fixed_fit_output(SampleClient& sorter)
{
    map<dsm_sample_id_t,double>::const_iterator ci = _fixedRates.begin();
    for ( ; ci != _fixedRates.end(); ++ci) {
        const pair<dsm_sample_id_t,double>& p = *ci;
        dsm_sample_id_t id = p.first;

        size_t n;
        double a,b; /* y = a + b * x */
        double cov_00, cov_01, cov_11, sumsq;

        sampcontainer<samp_save>& samps = _fixed_rate_samples[id];
        gsl_fit_sums& sums = _fixed_sums[id];

        n = do_fit(samps,sums,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
        if (n > 0) {
            int maxneg,maxpos;
            dsm_time_t tout = samps.front().samp->getTimeTag();
            write_samps(samps,sorter,a,b,&maxneg,&maxpos);
            cout << 
                formatTime(tout) << ' ' <<
                formatId(id) << ' ' <<
                setw(6) << n << ' ' <<
                setw(10) << fixed << setprecision(2) << a << ' ' <<
                setw(10) << b << ' ' <<
                setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
                setw(10) << maxneg << ' ' << 
                setw(10) << maxpos << endl;
        }
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

        SampleSorter sorter("output sorter",true);
        sorter.setHeapMax(1000 * 1000 * 1000);
        sorter.setHeapBlock(true);
        sorter.addSampleClient(&outStream);
        sorter.setLengthSecs(1.1 * _fitUsecs / USECS_PER_SEC);
        sorter.start();

        nidas::core::FileSet* fset = nidas::core::FileSet::getFileSet(_inputFileNames);

        dsm_time_t endTime = 0;

        // SampleInputStream owns the fset ptr.
        RawSampleInputStream input(fset);

        map<dsm_sample_id_t,int> lastfolds;
        // yeow, what a tangled web we weave...
        map<dsm_sample_id_t, vector<sampcontainer<samp_save> > > folds_by_csat;

        map<dsm_sample_id_t, int> unmatchedFold0Counters;

        map<dsm_sample_id_t,dsm_time_t> lastCSAT3Times;

        map<dsm_sample_id_t, dsm_time_t> last_CSAT_Times;

        // folds older than this are considered abandoned
        const int OLD_FOLD_MAX_DT = 5 * USECS_PER_SEC;
        const int MAJOR_DATA_GAP = 30 * USECS_PER_SEC;

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

            if (!(dsmSampleNumber % 10000))
                cerr << "sample num for dsm " << GET_DSM_ID(inid) << "=" <<
                        _dsmSampleNumbers[GET_DSM_ID(inid)] << endl;
#ifdef DEBUG
#endif

            if (_csatRates.find(inid) != _csatRates.end()) {

                int cseq = get_csat_seq(samp);
                if (cseq < 0) continue;

                vector<sampcontainer<samp_save> >& folds = folds_by_csat[inid];

                if (tt > endTime) {
                    // one csat sample time > endTime
                    //      we don't wait until all are > endTime.
                    //  if all csats have only one fold, then do a fit and output
                    //  one or more with multiple folds:
                    //      detect abandoned folds, if last time of a fold
                    //      is old, splice it to fold[0]
                    bool allSingleFolds = true;
                    map<dsm_sample_id_t, vector<sampcontainer<samp_save> > >::iterator fi = folds_by_csat.begin();
                    for ( ; fi != folds_by_csat.end(); ++fi) {
                        vector<sampcontainer<samp_save> >& flds = fi->second;
                        if (flds.size() > 1) {
                            for (unsigned int ifold = 1; ifold < flds.size(); ifold++) {
                                // folds other than 0 should not be empty
                                assert(!flds[ifold].empty());
                                struct samp_save& last_save = flds[ifold].back();
                                const Sample* lastsamp = last_save.samp;
                                // splice abandoned folds to fold[0]
                                long long dt = tt - lastsamp->getTimeTag();
                                if (dt > OLD_FOLD_MAX_DT) {
                                    cerr << "endTime exceeded, splicing abandoned fold " << ifold <<
                                        " to fold 0, dt=" << dt << endl;
                                    flds[0].splice(flds[0].end(),flds[ifold]);
                                    flds.erase(flds.begin()+ifold);
                                    if (lastfolds[inid] >= ifold) 
                                        lastfolds[inid]--;
                                    ifold--;
                                }
                            }
                        }
                        if (flds.size() > 1) allSingleFolds = false;
                    }
                    if (allSingleFolds) {
                        map<dsm_sample_id_t, vector<sampcontainer<samp_save> > >::iterator fi = folds_by_csat.begin();
                        for ( ; fi != folds_by_csat.end(); ++fi) {
                            dsm_sample_id_t id = fi->first;
                            vector<sampcontainer<samp_save> >& flds = fi->second;
                            if (flds.size() > 0) {
                                assert(flds.size() == 1);
                                csat_fit_output(id,flds[0],sorter);
                            }
                        }
                        fixed_fit_output(sorter);
                        output_other(sorter);
                        // don't increment endTime of not all single folds
                        endTime = tt + _fitUsecs - (tt % _fitUsecs);
                    }
                }
                else {
                    // check time difference from last sample received
                    // on this csat.  If large positive, restart fit
                    dsm_time_t ct = lastCSAT3Times[GET_DSM_ID(inid)];
                    long long dt = tt - ct;
                    if (dt > MAJOR_DATA_GAP && ct != 0) {
                        // Deltat bigger than any expected clock jump for this CSAT
                        // Assume a data system restart, or sensor unplugged/plugged
                        // splice all folds for this CSAT onto fold[0], then output.
                        // We're just splicing in numeric fold order, may want to look
                        // at this further
                        while (folds.size() > 1) {
                            cerr << "splicing fold " << 1 <<
                                " to fold 0, dt=" << dt << endl;
                            folds[0].splice(folds[0].end(),folds[1]);
                            folds.erase(folds.begin()+1);
                        }
                        lastfolds[inid] = 0;
                        unmatchedFold0Counters[inid] = 0;
                        if (folds.size() > 0 && !folds[0].empty()) 
                            csat_fit_output(inid,folds[0],sorter);
                    }
                }
                lastCSAT3Times[GET_DSM_ID(inid)] = tt;

                int sampleDt = USECS_PER_SEC / _csatRates[inid];

                // Find a fold that this sample belongs to by checking
                // whether its csat3 sequence number is one greater than
                // the sequence number of the last sample in the fold.
                // There is a 1 out of 64 chance that it could match the
                // wrong fold if there are multiple folds
                unsigned int ifold;
                int lastfold = lastfolds[inid];
                int lastseq = -1;
                for (ifold = 0; ifold < folds.size(); ifold++) {
                    if (folds[0].empty()) {
                        lastfold = 0;
                        break;
                    }
                    lastfold = (lastfold + 1) % folds.size();

                    struct samp_save& last_save = folds[lastfold].back();
                    const Sample* lastsamp = last_save.samp;
                    // check for abandoned folds, and splice them onto fold[0]
                    long long dt = tt - lastsamp->getTimeTag();
                    if (lastfold != 0 && dt > OLD_FOLD_MAX_DT) {
                        cerr << "splicing abandoned fold " << lastfold <<
                            " to fold 0, dt=" << dt << endl;
                        folds[0].splice(folds[0].end(),folds[lastfold]);
                        folds.erase(folds.begin()+lastfold);
                        lastfold--;
                        ifold--;
                    }
                    else {
                        struct samp_save& last_save = folds[lastfold].back();
                        lastseq = last_save.seq; 
                        if (cseq == (lastseq + 1) % 64) break;
                    }
                }
                struct samp_save csat;
                csat.samp = samp;
                csat.seq = cseq;
                csat.dsmSampleNumber = dsmSampleNumber;
                assert(ifold <= folds.size());

                if (ifold == folds.size()) {
                    folds.push_back(sampcontainer<samp_save>());
                    lastfold = ifold;
                    cerr << formatTime(tt) <<
                        ", id=" << formatId(inid) << " adding a fold, folds.size()=" <<
                        folds.size() << endl;
                }
                if (lastfold != 0 || folds.size() > 1)
                    cerr << formatTime(tt) <<
                        ", id=" << formatId(inid) << " appending sample to fold " <<
                        lastfold <<
                        ", lastseq=" << lastseq << "(0x" << hex << lastseq << dec << ')' <<
                        " cseq=" << cseq << "(0x" << hex << cseq << dec << ')' << endl;

                // either found a fold to append this sample, or are
                // starting a new fold
                // sample sequence number is 1 more than seq at end of
                // fold with index lastfold

                // for how many samples have we not found a sample
                // on fold[0]
                if (lastfold != 0) unmatchedFold0Counters[inid]++;
                folds[lastfold].push_back(csat);
                lastfolds[inid] = lastfold;

                if (unmatchedFold0Counters[inid] > 4) {
                    // we're no longer getting samples for fold 0, it must have
                    // ended. See if another fold matches the end of fold 0
                    // and if so, splice it.
                    assert(!folds[0].empty());

                    struct samp_save& lastf0 = folds[0].back();
                    int lastseqf0 = lastf0.seq; 

                    int jfold = 1;
                    for (jfold = 1; jfold < folds.size(); jfold++) {
                        // if a fold other than 0 exists it must contain something
                        struct samp_save& first = folds[jfold].front();
                        if (first.seq == (lastseqf0 + 1) % 64) break;
                    }
                    // fold jfold matches end of fold[0], append it
                    if (jfold < folds.size()) {
                        // splice empties folds[jfold]
                        cerr << "splicing matched fold " << jfold <<
                            " to fold 0, seq=" << lastseqf0 << "(0x" << hex << lastseqf0 << dec << ')' << endl;
                        folds[0].splice(folds[0].end(),folds[jfold]);
                        folds.erase(folds.begin()+jfold);
                        lastfold--;
                        unmatchedFold0Counters[inid] = 0;
                    }
                }
            }
            else if (_fixedRates.find(inid) != _fixedRates.end()) {

                sampcontainer<samp_save>& samps = _fixed_rate_samples[inid];
                gsl_fit_sums& sums = _fixed_sums[inid];

                dsm_time_t ttx = 0;
                size_t sampleNumber = 0;

                if (!samps.empty()) {

                    struct samp_save& last_samp = samps.back();
                    const Sample* last = last_samp.samp;

                    int sampleDt = USECS_PER_SEC / _fixedRates[inid];
                    int dt = tt - last->getTimeTag();
                    bool newfit = true;

                    if (abs(dt) < 64 * sampleDt) newfit = false;
                    if (newfit) {
                        size_t n;
                        double a,b; /* y = a + b * x */
                        double cov_00, cov_01, cov_11, sumsq;

                        n = do_fit(samps,sums,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
                        if (n > 0) {
                            int maxneg,maxpos;
                            dsm_time_t tout = samps.front().samp->getTimeTag();
                            write_samps(samps,sorter,a,b,&maxneg,&maxpos);
                            cout << 
                                n_u::UTime(tout).format(true,"%Y %m %d %H:%M:%S ") <<
                                formatId(inid) << ' ' <<
                                setw(6) << n << ' ' <<
                                setw(10) << fixed << setprecision(2) << a << ' ' <<
                                setw(10) << b << ' ' <<
                                setw(10) << sqrt(sumsq / (n-2.0)) << ' ' <<
                                setw(10) << maxneg << ' ' << 
                                setw(10) << maxpos << endl;
                        }
                    }
                    else {
                        sampleNumber = last_samp.sampleNumber + 1;
                        ttx =  tt - samps.front().samp->getTimeTag();
                    }
                }

                struct samp_save save;
                save.samp = samp;
                save.sampleNumber = sampleNumber;
                save.dsmSampleNumber = dsmSampleNumber;
                samps.push_back(save);

                // cerr << "ttx=" << ttx << " sampleNumber=" << sampleNumber << " cseq=" << cseq << endl;
                gsl_fit_linear_add_point((double)sampleNumber,(double)ttx, &sums);
            }
            else {
                struct samp_save save;
                save.samp = samp;
                save.dsmSampleNumber = dsmSampleNumber;
                _other_samples.push_back(save);
            }
        }
        if (!_interrupted) {
            map<dsm_sample_id_t, vector<sampcontainer<samp_save> > >::iterator fi = folds_by_csat.begin();
            for ( ; fi != folds_by_csat.end(); ++fi) {
                dsm_sample_id_t id = fi->first;
                vector<sampcontainer<samp_save> >& folds = fi->second;
                while (folds.size() > 1) {
                    cerr << "end of processing, id=" << formatId(id) << ", splicing fold " << 1 <<
                        " to fold 0" << endl;
                    folds[0].splice(folds[0].end(),folds[1]);
                    folds.erase(folds.begin()+1);
                }
                if (folds.size() > 0 && !folds[0].empty()) {
                    size_t n = csat_fit_output(id,folds[0],sorter);
                }
            }
            fixed_fit_output(sorter);
            output_other(sorter);
            sorter.finish();
        }
        outStream.close();
    }
    catch (n_u::IOException& ioe) {
        cerr << ioe.what() << endl;
	return 1;
    }
    return 0;
}


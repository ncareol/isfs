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

using namespace nidas::core;
using namespace nidas::dynld;
using namespace std;

namespace n_u = nidas::util;

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
        size_t dsmSampleNumber;

        samp_save() : samp(0),seq(0),sampleNumber(0),dsmSampleNumber(0) {}
    };

    TT_Adjust();

    int parseRunstring(int argc, char** argv);

    int run() throw();

    static void sigAction(int sig, siginfo_t* siginfo, void* vptr);

    static void setupSignals();

    static int main(int argc, char** argv) throw();

    static int usage(const char* argv0);

    void do_fit(list<samp_save>& samps,gsl_fit_sums& sums,
            size_t* n,double* a, double* b,
            double* cov_00, double* cov_01, double* cov_11, double* sumsq);

    /**
     * Do a linear least squares fit of record number to CSAT3 timetags,
     * output the newly timetagged samples.
     */
    void csat_fit_output(SampleClient&);

    /**
     * Do a linear least squares fit of record number to timetags of a fixed
     * rate sensor, output the newly timetagged samples.
     */
    void fixed_fit_output(SampleClient&);

    /**
     * Adjust the timetags on a list of samples, using the coefficients
     * of the least squares fit, and send them to a SampleClient,
     */
    void write_samps(list<samp_save>& samps,SampleClient& out,
            double a, double b,int* maxnegp, int* maxposp);

    /**
     * Output the other sensors that haven't been designated as CSAT3 or fixed
     * rate, using the adjusted time tags of the CSAT and fixed rate sensors.
     */
    void output_other(SampleClient& out, int* maxnegp, int* maxposp);

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
    map<dsm_sample_id_t, list<samp_save> > _csat_samples;

    /** 
     * least squares sums for each CSAT3
     */
    map<dsm_sample_id_t, gsl_fit_sums> _csat_sums;

    /**
     * information to save with each non-CSAT3 data sample with a fixed rate
     */
    map<dsm_sample_id_t, list<samp_save> > _fixed_rate_samples;

    /**
     * least squares sums for each non-CSAT3 with a fixed rate
     */
    map<dsm_sample_id_t, gsl_fit_sums> _fixed_sums;

    /**
     * The list of other samples to be output after the fits of CSAT3 and other fixed
     * rate sensors.
     */
    list<samp_save> _other_samples;

    /**
     * counter of current sample number of each dsm.
     */
    map<unsigned int, size_t> _dsmSampleNumbers;

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
     * Sequence of record number and time tags for each dsm.
     */
    map<unsigned int, set<pair<size_t,dsm_time_t>,SequenceComparator> > _dsmSequences;

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
	    _fitUsecs = atoi(optarg) * (long long)USECS_PER_SEC;
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
    if (_inputFileNames.size() == 0) return usage(argv[0]);

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

void TT_Adjust::do_fit(list<samp_save>& samps,gsl_fit_sums& sums,
        size_t* np, double* a, double* b,
        double* cov_00, double* cov_01, double* cov_11, double* sumsq)
{
    size_t n = sums.n;

    if (n > 0) {
        gsl_fit_linear_compute(&sums,a,b);

        list<samp_save>::const_iterator si = samps.begin();
        dsm_time_t tt0 =  si->samp->getTimeTag();

        for ( ; si != samps.end(); ++si) {
            const samp_save& save = *si;
            const Sample* samp = save.samp;
            gsl_fit_linear_add_resid((double)save.sampleNumber,
                    (double)samp->getTimeTag() - tt0,&sums);
        }

        gsl_fit_linear_compute_resid(&sums,cov_00, cov_01, cov_11, sumsq);
    }
    sums.zero();      // we're done, zero the sums
    *np = n;
}

void TT_Adjust::write_samps(list<samp_save>& samps, SampleClient& out,
        double a, double b,int* maxnegp, int* maxposp)
{
    int maxneg = 0;
    int maxpos = 0;

    list<samp_save>::iterator si = samps.begin();
    dsm_time_t tt0 =  si->samp->getTimeTag();
    for ( ; si != samps.end(); ++si) {
        samp_save& save = *si;
        Sample* samp = const_cast<Sample*>(save.samp);

        dsm_time_t newtt = tt0 + (dsm_time_t) (a + save.sampleNumber * b + 0.5);
        int dt = newtt - samp->getTimeTag();

        if (dt < 0) maxneg = std::min(dt,maxneg);
        else if (dt > 0) maxpos = std::max(dt,maxpos);

        samp->setTimeTag(newtt);
        out.receive(samp);

        unsigned int dsmid = samp->getDSMId();
        _dsmSequences[dsmid].insert(make_pair<size_t,dsm_time_t>(save.dsmSampleNumber,newtt));

        samp->freeReference();
    }
    samps.clear();
    *maxnegp = maxneg;
    *maxposp = maxpos;
}

void TT_Adjust::output_other(SampleClient& out, int* maxnegp, int* maxposp)
{
    int maxneg = 0;
    int maxpos = 0;

    list<samp_save>::iterator si = _other_samples.begin();
    for ( ; si != _other_samples.end(); ++si) {
        samp_save& save = *si;
        Sample* samp = const_cast<Sample*>(save.samp);
        unsigned int dsmid = samp->getDSMId();

        set<pair<size_t,dsm_time_t>,SequenceComparator>& seq = _dsmSequences[dsmid];

        dsm_time_t tt = samp->getTimeTag();
        if (seq.size() > 0) {
            // create a key containing the sample number
            pair<size_t,dsm_time_t> p(save.dsmSampleNumber,0);

            // find iterator pointing to first element > key, giving us the
            // timetag of the sample with record number > save.dsmSampleNumber.
            set<pair<size_t,dsm_time_t>,SequenceComparator>::const_iterator i1 = seq.upper_bound(p);

            // extrapolate backwards if first element is > key
            if (i1 == seq.begin()) ++i1;   

            set<pair<size_t,dsm_time_t>,SequenceComparator>::const_iterator i0 = i1;
            // since size() > 0, i0 is a valid iterator after this decrement
            --i0;

            if (i1 != seq.end()) {
                // leave timetag alone if it is found between times of the neighboring records
                if (tt < i0->second || tt > i1->second) {
                    tt = i0->second +
                        (i1->second - i0->second) / (signed)(i1->first - i0->first) *
                        (signed)(save.dsmSampleNumber - i0->first);
#ifdef DEBUG
                    cerr << samp->getDSMId() << ',' << samp->getSpSId() << ": " <<
                        save.dsmSampleNumber << ',' << samp->getTimeTag() << 
                        ", i0=" << i0->first << ',' << i0->second <<
                        ", i1=" << i1->first << ',' << i1->second <<
                        ", newtt=" << tt <<
                        ", recdiff = " << i1->first - i0->first << 
                        ", timediff = " << i1->second - i0->second <<
                        ", recdiff = " << save.dsmSampleNumber - i0->first << endl;
#endif
                }
            }
        }

        int dt = tt - samp->getTimeTag();

        if (dt < 0) maxneg = std::min(dt,maxneg);
        else if (dt > 0) maxpos = std::max(dt,maxpos);

        samp->setTimeTag(tt);
        out.receive(samp);
        samp->freeReference();
    }
    _other_samples.clear();
    _dsmSequences.clear();
    *maxnegp = maxneg;
    *maxposp = maxpos;
}

void TT_Adjust::csat_fit_output(SampleClient& sorter)
{
    map<dsm_sample_id_t,double>::const_iterator ci = _csatRates.begin();
    for ( ; ci != _csatRates.end(); ++ci) {
        const pair<dsm_sample_id_t,double>& p = *ci;
        dsm_sample_id_t id = p.first;

        size_t n;
        double a,b; /* y = a + b * x */
        double cov_00, cov_01, cov_11, sumsq;

        list<samp_save>& samps = _csat_samples[id];
        gsl_fit_sums& sums = _csat_sums[id];

        do_fit(samps,sums,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
        if (n > 0) {
            int maxneg,maxpos;
            write_samps(samps,sorter,a,b,&maxneg,&maxpos);
            cerr << GET_DSM_ID(id) << ',' << GET_SPS_ID(id) << ": " <<
                "n=" << n << ", a=" << a << " b=" << b <<
                " sumsq=" << sumsq <<
                " sd=" << sqrt(sumsq / (n-2.0)) <<
                " maxneg=" << maxneg << " maxpos=" << maxpos << endl;
        }
    }
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

        list<samp_save>& samps = _fixed_rate_samples[id];
        gsl_fit_sums& sums = _fixed_sums[id];

        do_fit(samps,sums,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
        if (n > 0) {
            int maxneg,maxpos;
            write_samps(samps,sorter,a,b,&maxneg,&maxpos);
            cerr << GET_DSM_ID(id) << ',' << GET_SPS_ID(id) << ": " <<
                "n=" << n << ", a=" << a << " b=" << b <<
                " sumsq=" << sumsq <<
                " sd=" << sqrt(sumsq / (n-2.0)) <<
                " maxneg=" << maxneg << " maxpos=" << maxpos << endl;
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
        sorter.addSampleClient(&outStream);
        sorter.setLengthSecs(1.5 * _fitUsecs / USECS_PER_SEC);
        sorter.start();

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
            if (tt > endTime) {
                csat_fit_output(sorter);
                fixed_fit_output(sorter);
                int maxneg,maxpos;
                output_other(sorter,&maxneg,&maxpos);
                cerr << "output other, maxneg=" << maxneg << " maxpos=" << maxpos << endl;
                endTime = tt + _fitUsecs - (tt % _fitUsecs);
                // cerr << "tt=" << tt << " endTime=" << endTime << endl;
            }

            dsm_sample_id_t inid = samp->getId();
            _dsmSampleNumbers[GET_DSM_ID(inid)]++;

            if (_csatRates.find(inid) != _csatRates.end()) {

                int cseq = get_csat_seq(samp);
                if (cseq >= 0) {

                    list<samp_save>& samps = _csat_samples[inid];
                    gsl_fit_sums& sums = _csat_sums[inid];

                    size_t sampleNumber = 0;        // x value in fit
                    dsm_time_t ttx = 0;             // y value in fit

                    if (samps.size() > 0) {

                        struct samp_save& last_csat = samps.back();
                        const Sample* last = last_csat.samp;

                        int sampleDt = USECS_PER_SEC / _csatRates[inid];

                        int dseq = cseq - last_csat.seq;
                        if (dseq < -32) dseq += 64;
                        else if (dseq >  32) dseq -= 64;

                        int dt = tt - last->getTimeTag();

                        bool newfit = true;

                        // Try to screen ridiculous pairs of dseq and dt here.
                        // dseq=0 is a wierd value, same as 64
                        // It is hard to screen values without looking ahead.
                        // Need to screen values after the fit.
                        // Any way to estimate a weight for a clock value?

                        if (dseq != 0 && abs(dseq) < 32 && abs(dt) < 64 * sampleDt)
                                newfit = false;
                        // cerr << "dseq=" << dseq << " dt=" << dt << " sampleDt=" << sampleDt << endl;
                        if (newfit) {
                            size_t n;
                            double a,b; /* y = a + b * x */
                            double cov_00, cov_01, cov_11, sumsq;

                            do_fit(samps,sums,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
                            if (n > 0) {
                                int maxneg,maxpos;
                                write_samps(samps,sorter,a,b,&maxneg,&maxpos);
                                cerr << GET_DSM_ID(inid) << ',' << GET_SPS_ID(inid) << ": " <<
                                        "n=" << n << ", a=" << a << " b=" << b <<
                                        " sumsq=" << sumsq <<
                                        " sd=" << sqrt(sumsq / (n-2.0)) <<
                                        " maxneg=" << maxneg << " maxpos=" << maxpos << endl;
                            }
                        }
                        else {
                            sampleNumber = last_csat.sampleNumber + dseq;
                            ttx =  tt - samps.front().samp->getTimeTag();
                        }
                    }

                    struct samp_save csat;
                    csat.samp = samp;
                    csat.seq = cseq;
                    csat.sampleNumber = sampleNumber;
                    csat.dsmSampleNumber = _dsmSampleNumbers[GET_DSM_ID(inid)];
                    samps.push_back(csat);

                    // cerr << "ttx=" << ttx << " sampleNumber=" << sampleNumber << " cseq=" << cseq << endl;
                    gsl_fit_linear_add_point((double)sampleNumber,(double)ttx, &sums);
                }
            }
            else if (_fixedRates.find(inid) != _fixedRates.end()) {

                list<samp_save>& samps = _fixed_rate_samples[inid];
                gsl_fit_sums& sums = _fixed_sums[inid];

                dsm_time_t ttx = 0;
                size_t sampleNumber = 0;

                if (samps.size() > 0) {

                    struct samp_save& last_samp = samps.back();
                    const Sample* last = last_samp.samp;

                    int dt = tt - last->getTimeTag();

                    int DELTAT_MAX = 100 * USECS_PER_SEC / _fixedRates[inid];
                    if (dt >= DELTAT_MAX) {
                        size_t n;
                        double a,b; /* y = a + b * x */
                        double cov_00, cov_01, cov_11, sumsq;

                        do_fit(samps,sums,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
                        if (n > 0) {
                            int maxneg,maxpos;
                            write_samps(samps,sorter,a,b,&maxneg,&maxpos);
                            cerr << GET_DSM_ID(inid) << ',' << GET_SPS_ID(inid) << ": " <<
                                    "n=" << n << ", a=" << a << " b=" << b <<
                                    " sumsq=" << sumsq <<
                                    " sd=" << sqrt(sumsq / (n-2.0)) <<
                                    " maxneg=" << maxneg << " maxpos=" << maxpos << endl;
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
                save.dsmSampleNumber = _dsmSampleNumbers[GET_DSM_ID(inid)];
                samps.push_back(save);

                // cerr << "ttx=" << ttx << " sampleNumber=" << sampleNumber << " cseq=" << cseq << endl;
                gsl_fit_linear_add_point((double)sampleNumber,(double)ttx, &sums);
            }
            else {
                struct samp_save save;
                save.samp = samp;
                save.dsmSampleNumber = _dsmSampleNumbers[GET_DSM_ID(inid)];
                _other_samples.push_back(save);
            }
        }
        csat_fit_output(sorter);
        fixed_fit_output(sorter);
        int maxneg,maxpos;
        output_other(sorter,&maxneg,&maxpos);
        cerr << "output other, maxneg=" << maxneg << " maxpos=" << maxpos << endl;
        sorter.finish();
        outStream.close();
    }
    catch (n_u::IOException& ioe) {
        cerr << ioe.what() << endl;
	return 1;
    }
    return 0;
}


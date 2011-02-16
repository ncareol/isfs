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

 If the input data has been sorted, and the original timetags were bad enough
 such that the sequence of samples from a sensor were re-ordered, then this
 algorithm does not fix that issue, except in the case of samples from a
 CSAT3 sonic, which contain an internal sequence number, 0-63. If the
 CSAT3 samples were not re-ordered by more than 32 samples, then it is 
 straightforward to use the sequence number to determine the original
 sample sequence, generate the timetags from the least squares fit.
*/

#include <nidas/core/FileSet.h>
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
 * if passed x and y input arguments of continuous double arrays. In order
 * to avoid creating the x and y arrays, we split the function into several
 * passes, using a struct to pass the results of the sums:
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

    unsigned short diag = ((const unsigned short*) dptr)[4];
    return (diag & 0x003f);
}

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

    void do_fit(dsm_sample_id_t id,size_t* n,double* a, double* b,
        double* cov_00, double* cov_01, double* cov_11, double* sumsq);

    // void free_samps(dsm_sample_id_t id);

    void write_samps(dsm_sample_id_t id,
        SampleClient& out,double a, double b);

private:

    static bool _interrupted;

    list<string> _inputFileNames;

    string _outputFileName;

    long long _fitUsecs;

    int _outputFileLength;

    double _csatRate;

    map<dsm_sample_id_t,double> _csatRates;

    map<dsm_sample_id_t,double> _otherRates;

    /** information to save with each CSAT3 data sample */
    struct csat_save {
        const Sample* samp;
        /** sequence number 0-63 found in CSAT3 sample */
        int seq;
        /** sample number for a given CSAT3 in the input */
        size_t snum;
        csat_save() : samp(0),seq(0),snum(0) {}
    };

    map<dsm_sample_id_t, list<csat_save> > _csat_samples;

    /** least squares sums for each CSAT3 */
    map<dsm_sample_id_t, gsl_fit_sums> _csat_sums;

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
                cerr << "dsmid=" << dsmid << " sensorid=" << sensorid <<
                    " rate=" << rate << endl;
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

                _otherRates[id] = rate;
            }
            break;
	case '?':
	    return usage(argv[0]);
	}
    }
    if (_outputFileName.length() == 0) return usage(argv[0]);

    for ( ; optind < argc; optind++) _inputFileNames.push_back(argv[optind]);
    if (_inputFileNames.size() == 0) return usage(argv[0]);

    // set unspecified rates to default
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

void TT_Adjust::do_fit(dsm_sample_id_t id,size_t* np, double* a, double* b,
        double* cov_00, double* cov_01, double* cov_11, double* sumsq)
{

    size_t n = _csat_sums[id].n;

    if (n > 0) {
        gsl_fit_linear_compute(&_csat_sums[id],a,b);

        list<csat_save>& samps = _csat_samples[id];
        list<csat_save>::const_iterator si = samps.begin();

        dsm_time_t tt0 =  samps.front().samp->getTimeTag();

        for ( ; si != samps.end(); ++si) {
            const csat_save& save = *si;
            const Sample* samp = save.samp;
            gsl_fit_linear_add_resid((double)save.snum,
                    (double)samp->getTimeTag() - tt0,&_csat_sums[id]);
        }

        gsl_fit_linear_compute_resid(&_csat_sums[id],
                cov_00, cov_01, cov_11, sumsq);
    }
    *np = n;
}

void TT_Adjust::write_samps(dsm_sample_id_t id,
        SampleClient& out,double a, double b)
{
    list<csat_save>& samps = _csat_samples[id];
    list<csat_save>::iterator si = samps.begin();

    dsm_time_t tt0 =  samps.front().samp->getTimeTag();
    int maxneg = 0;
    int maxpos = 0;

    for ( ; si != samps.end(); ++si) {
        csat_save& save = *si;
        Sample* samp = const_cast<Sample*>(save.samp);

        dsm_time_t newtt = tt0 + (dsm_time_t) (a + save.snum * b + 0.5);
        int dt = newtt - samp->getTimeTag();

        if (dt < 0) maxneg = std::min(dt,maxneg);
        else if (dt > 0) maxpos = std::max(dt,maxpos);

        samp->setTimeTag(newtt);
        out.receive(samp);
        samp->freeReference();
    }
    samps.clear();
    _csat_sums[id].zero();      // zero the sums

    cerr << GET_DSM_ID(id) << ',' << GET_SPS_ID(id) << ": " <<
            "maxneg=" << maxneg << " maxpos=" << maxpos << endl;
}

int TT_Adjust::run() throw()
{

    try {
	nidas::core::FileSet* outSet = 0;
#ifdef HAS_BZLIB_H
        if (outputFileName.find(".bz2") != string::npos)
            outSet = new nidas::core::Bzip2FileSet();
        else
#endif
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
                map<dsm_sample_id_t,double>::const_iterator ci = _csatRates.begin();
                for ( ; ci != _csatRates.end(); ++ci) {
                    const pair<dsm_sample_id_t,double>& p = *ci;
                    dsm_sample_id_t id = p.first;

                    size_t n;
                    double a,b; /* y = a + b * x */
                    double cov_00, cov_01, cov_11, sumsq;

                    do_fit(id,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
                    write_samps(id,sorter,a,b);
                }
                sorter.finish();
                outStream.close();
                break;
            }
            if (_interrupted) break;

            dsm_time_t tt = samp->getTimeTag();

            if (tt > endTime) {
                map<dsm_sample_id_t,double>::const_iterator ci = _csatRates.begin();
                for ( ; ci != _csatRates.end(); ++ci) {
                    const pair<dsm_sample_id_t,double>& p = *ci;
                    dsm_sample_id_t id = p.first;

                    size_t n;
                    double a,b; /* y = a + b * x */
                    double cov_00, cov_01, cov_11, sumsq;

                    do_fit(id,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
                    if (n > 0) {
                        cerr << GET_DSM_ID(id) << ',' << GET_SPS_ID(id) << ": " <<
                                "n=" << n << ", a=" << a << " b=" << b <<
                                " sumsq=" << sumsq <<
                                " sd=" << sqrt(sumsq / (n-2.0)) << endl;
                        write_samps(id,sorter,a,b);
                    }
                }
                endTime = tt + _fitUsecs - (tt % _fitUsecs);
                // cerr << "tt=" << tt << " endTime=" << endTime << endl;
            }

            dsm_sample_id_t id = samp->getId();

            if (_csatRates.find(id) != _csatRates.end()) {

                int cseq = get_csat_seq(samp);
                if (cseq >= 0) {

                    list<csat_save>& samps = _csat_samples[id];
                    dsm_time_t ttx = 0;
                    size_t snum = 0;

                    if (samps.size() > 0) {

                        struct csat_save& last_csat = samps.back();
                        const Sample* last = last_csat.samp;

                        int dt = tt - last->getTimeTag();

                        int DELTAT_MAX = 5 * USECS_PER_SEC;
                        if (dt >= DELTAT_MAX) {
                            size_t n;
                            double a,b; /* y = a + b * x */
                            double cov_00, cov_01, cov_11, sumsq;
                            do_fit(id,&n,&a,&b,&cov_00,&cov_01,&cov_11,&sumsq);
                            if (n > 0) {
                                cerr << GET_DSM_ID(id) << ',' << GET_SPS_ID(id) << ": " <<
                                        "n=" << n << ", a=" << a << " b=" << b <<
                                        " sumsq=" << sumsq <<
                                        " sd=" << sqrt(sumsq / (n-2.0)) << endl;
                                write_samps(id,sorter,a,b);
                            }
                        }
                        else {
                            int dseq = cseq - last_csat.seq;
                            if (dseq < -32) dseq += 64;
                            snum = last_csat.snum + dseq;
                            ttx =  tt - samps.front().samp->getTimeTag();
                        }
                    }

                    struct csat_save csat;
                    csat.samp = samp;
                    csat.seq = cseq;
                    csat.snum = snum;
                    samps.push_back(csat);

                    // cerr << "ttx=" << ttx << " snum=" << snum << " cseq=" << cseq << endl;
                    gsl_fit_linear_add_point((double)snum,(double)ttx, &_csat_sums[id]);
                }
            }
        }
    }
    catch (n_u::IOException& ioe) {
        cerr << ioe.what() << endl;
	return 1;
    }
    return 0;
}


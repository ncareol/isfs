// -*- mode: C++; indent-tabs-mode: nil; c-basic-offset: 4; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:

#include <iostream>
#include <time.h>
#include <unistd.h>
#include <stdlib.h>
#include <ctype.h>
#include <stdio.h>
#include <math.h>
#include <cstring>

#include <netcdf.hh>
#include <vector>

#include <nidas/util/UTime.h>

namespace n_u = nidas::util;

using std::cout;
using std::cerr;
using std::endl;
using std::vector;

char *localTZ;

void Usage(char *argv0)
{
    cerr << "Usage: " << argv0 << " [-s stations] [-t time_format | -H | -S] [-l] filename varnames ..." << endl;
    cerr << "  -s stations: station numbers (0-n) separted by commas or dashes," << endl;
    cerr << "     where a dash indicates a range of station numbers. Use station 0," << endl;
    cerr << "     which is the default value, for variables without a station dimension" << endl;
    cerr << "  -t time_format: format to use when printing time" << endl;
    cerr << "     See strftime man page. The default, " << endl;
    cerr << "     -t \"%Y %m %d %H%M%S\" results in YYYY MM DD HHMMSS." << endl;
    cerr << "  -l: print time in local time zone, else GMT." << endl;
    cerr << "     -l does not effect -H or -S options" << endl;
    cerr << "  -H: print time as fractional hours since midnight GMT" << endl;
    cerr << "  -S: print time as seconds since midnight GMT" << endl;
    cerr << "  -h: print header in first two lines, containing variable names and units" << endl;
    cerr << "  varnames is a list of 1 or more variable names, separated by blanks" << endl;
    cerr << "    Variables are matched by their NetCDF name, or the short_name or long_name attributes" << endl;
    cerr << endl << "Examples:" << endl;
    cerr << "Print variables u.9m and v.9m from stations 2,3 and 4" << endl;
    cerr << '\t' << argv0 << " -s 2-4 xxx.990113.nc u.9m v.9m" << endl;
    cerr << endl;
    cerr << "Print Tsoil.xxx which has no station dimension" << endl;
    cerr << '\t' << argv0 << " xxx.990113.nc Tsoil.xxx" << endl;
    cerr << endl;
    cerr << "Print variable U from any station" << endl;
    cerr << '\t' << argv0 << " -s 0-99 xxx.990113.nc U" << endl;
    cerr << endl;
    cerr << "Use ncdump to view NetCDF header contents and data" << endl; 
    cerr << "\tncdump xxx.990113.nc | more" << endl;
    exit(1);
}

n_u::UTime readTime(NcVar *var,long n) 
{
    nclong lt;
    double dt;
    float ft;
    int getres;

    var->set_cur(n);
    switch(var->type()) {
    case ncLong:
        getres = var->get(&lt,1);
        dt = lt;
        break;
    case ncDouble:
        getres = var->get(&dt,1);
        break;
    case ncFloat:
        getres = var->get(&ft,1);
        dt = ft;
        break;
    default:
        cerr << "Error in get of " << var->name() << " type not supported" << endl;
        exit(1);
    }
    if (!getres) {
        cerr << "Error in get of " << var->name() << " rec: " << n << endl;
        return n_u::UTime((time_t)0);
    }
    return n_u::UTime(dt);
}

int main(int argc, char **argv)
{
    int i,j,k,l;
    long n;

    char *p1,*p2,sep;
    int c1=0,c2;
    extern char    *optarg;       /* set by getopt() */
    extern int      optind;       /* "  "     "     */
    int             opt_char;     /* option character */

    enum {FHOUR,SECONDS,CFTIME} printopt=CFTIME;
    vector<int> stations;
    vector<char *> varnames;
    char *fname;
    const char *timefmt;
    timefmt = "%Y %m %d %H%M%S %Z";

    bool printheader = false;

    localTZ = getenv("TZ");
    // if (localTZ == 0) localTZ = "GMT";

    const char *useTZ = "GMT";
    char TZenv[32];

    while ((opt_char = getopt(argc, argv, "hHlSs:t:")) != -1) {
        switch (opt_char) {
        case 'H':
            printopt = FHOUR;
            break;
        case 'h':
            printheader = true;
            break;
        case 'l':
            useTZ = localTZ;
            break;
        case 'S':
            printopt = SECONDS;
            break;
        case 's':
            p1 = optarg;
            sep= ',';

            while (sep != '\0') {
                c2 = atoi(p1);

                switch (sep) {
                case ',':
                    stations.push_back(c2);
                    break;
                case '-':
                    for (i = c1+1; i <= c2; i++) stations.push_back(i);
                    break;
                }
                c1 = c2;
                for (p2=p1; *p2 && isdigit(*p2); p2++);
                sep = *p2;
                p1 = ++p2;
            }
            break;
        case 't':
            printopt = CFTIME;
            timefmt = optarg;
            break;
        case '?':
            Usage(argv[0]);
        }
    }
    if (optind == argc) Usage(argv[0]);
    fname = argv[optind++];
    if (optind == argc) Usage(argv[0]);
    for (; optind < argc; optind++) varnames.push_back(argv[optind]);

    if (stations.size() == 0) stations.push_back(0);

    if (useTZ) {
        strcpy(TZenv,"TZ=");
        strcat(TZenv,useTZ);
        putenv(TZenv);
    }

    // NcError ncerror(NcError::silent_nonfatal);
    NcError ncerror(NcError::verbose_fatal);

    NcFile ncf(fname);
    if (!ncf.is_valid()) {
        cerr << fname << ": NetCDF open failed" << endl;
        exit(1);
    }

    NcAtt *att;
    NcDim *dim;
    NcVar *var;
    char *attString;
    NcVar *btvar, *tvar;

    std::ostringstream headerout;
    std::ostringstream unitsout;

    n_u::UTime bt,ut,ut0;

    bt = 0;
    {
        NcError ncerror(NcError::silent_nonfatal);
        if ((btvar = ncf.get_var("base_time"))) bt = readTime(btvar,0);
    }
    {
        NcError ncerror(NcError::silent_nonfatal);
        if (!(tvar = ncf.get_var("time"))) tvar = ncf.get_var("time_offset"); 
        if (!tvar) {
            cerr << "Time variable not found" << endl;
            exit(1);
        }
    }

    // arrays of information for each requested variable
    vector<NcVar *> vars;
    vector<int> nstations;	// how many stations
    vector<int> nsamples;		// how many samples
    vector<long*> start;
    vector<long*> count;
    vector<int> samplei;		// which dimension is the sample dimension
    vector<int> stationi;		// which dimension is the station dimension
    vector<long> otherdims;	// product of non-time, non-sample, non-station dims
    int maxnsamples = 1;
    long maxotherdims = 1;
    int nvars = 0;
    int iv,jv;

    for (iv = 0; iv < (signed) varnames.size(); iv++) {
        NcError ncerror(NcError::silent_nonfatal);
        var=0;
        // cerr << varnames[iv] << endl;
        if (!(var = ncf.get_var(varnames[iv]))) {
            for (jv = 0; jv < ncf.num_vars(); jv++) {
                var = ncf.get_var(jv);
                // Check its short_name attribute
                if ((att = var->get_att("short_name"))) {
                    attString = 0;
                    if (att->type() == ncChar && att->num_vals() > 0 &&
                            (attString = att->as_string(0)) &&
                            !strcmp(attString,varnames[iv])) {
                        delete att;
                        delete [] attString;
                        break;  // match
                    }
                    delete att;
                    delete [] attString;
                }
                // Check its long_name attribute
                if ((att = var->get_att("long_name"))) {
                    attString = 0;
                    if (att->type() == ncChar && att->num_vals() > 0 &&
                            (attString = att->as_string(0)) &&
                            !strcmp(attString,varnames[iv])) {
                        delete att;
                        delete [] attString;
                        break;  // match
                    }
                    delete att;
                    delete [] attString;
                }
            }
            if (jv == ncf.num_vars()) {
                cerr << "Variable " << varnames[iv] << " not found in " << fname << endl;
                exit(1);
            }
        }
        if (var->id() == tvar->id()) continue;	// don't repeat time variable

        if (printheader) {
            headerout << varnames[iv] << ' ';
            if ((att = var->get_att("units"))) {
                attString = 0;
                if (att->type() == ncChar && att->num_vals() > 0 &&
                        (attString = att->as_string(0)))
                    unitsout << '"' << attString << '"' << ' ';
                else unitsout << '"' << "unknown" << '"' << ' ';

                delete att;
                delete [] attString;
            }
            else unitsout << '"' << "unknown" << '"' << ' ';
        }


        vars.push_back(var);
        int nd = var->num_dims();
        nsamples.push_back(1);
        nstations.push_back(0);
        start.push_back(new long[nd]);
        count.push_back(var->edges());

        samplei.push_back(-1);
        stationi.push_back(-1);

        long od = 1;
        for (jv = 0; jv < nd; jv++) {
            start[nvars][jv] = 0;
            dim = var->get_dim(jv);
            if (!dim->is_unlimited()) {
                if (!strncmp(dim->name(),"sample",6)) {
                    nsamples[nvars] = dim->size();
                    samplei[nvars] = jv;
                    if (nsamples[nvars] > maxnsamples) maxnsamples = nsamples[nvars];
                }
                else if (!strcmp(dim->name(),"station")) {
                    nstations[nvars] = dim->size();
                    stationi[nvars] = jv;
                }
                else od *= dim->size();
            }
        }
        otherdims.push_back(od);
        if (od > maxotherdims) maxotherdims = od;
        // cerr << var->name() << ' ' << od << ' ' << nstations[nvars] << ' ' << nsamples[nvars] << endl;
        nvars++;
    }

    if (printheader) {
        cout << "# " << headerout.str() << endl;
        cout << "# " << unitsout.str() << endl;
    }

    long nrecs = ncf.rec_dim()->size();
    int doread;

    double *dval = new double[maxotherdims];
    float *fval = new float[maxotherdims];
    nclong *lval = new nclong[maxotherdims];

    int dt;
    if (nrecs > 1) dt = (int)(readTime(tvar,1) - readTime(tvar,0));
    else dt = 300 * USECS_PER_SEC;

    // cerr << "dt=" << dt << endl;
    //


    for (n = 0; n < nrecs; n++) {
        ut0 = bt + readTime(tvar,n).toUsecs();
        for (l = 0; l < maxnsamples; l++ ) {
            ut = ut0 - dt / 2 + (dt / maxnsamples / 2) + l * (dt / maxnsamples);
            switch(printopt) {
            case FHOUR:
                cout << (double)(ut - bt) / USECS_PER_SEC / SECS_PER_HOUR << ' ';
                break;
            case SECONDS:
                cout << (double)(ut - bt) / USECS_PER_SEC;
                break;
            case CFTIME:
                cout << ut.format(false,timefmt) << ' ';
                break;
            }
            for (iv = 0; iv < (signed) vars.size(); iv++) {
                var = vars[iv];
                start[iv][0] = n;
                count[iv][0] = 1;
                doread = 1;

                if (l >= nsamples[iv]) doread = 0;
                if (doread && (k = samplei[iv]) >= 0) {
                    start[iv][k] = l;
                    count[iv][k] = 1;
                }

                // loop over requested stations
                for (j = 0; j < (signed) stations.size(); j++) {

                    // if the length of the station dimension for this variable is
                    // greater than the current requested station, then read this variable

                    // station 0 is station # of variables without a station dimension

                    if (nstations[iv] >= stations[j] && !(stations[j] == 0 && nstations[iv] > 0)) {
                        if ((k = stationi[iv]) >= 0) {
                            start[iv][k] = stations[j]-1;
                            count[iv][k] = 1;
                        }
                        if (!var->set_cur(start[iv])) {
                            cerr << "Error in set_cur of " << var->name() << " rec: " <<
                                n << " station: " << stations[j] << endl;
                            continue;
                        }

                        NcBool getres;
                        switch(var->type()) {
                        case ncDouble:
                            getres = var->get(dval,count[iv]);
                            break;
                        case ncFloat:
                            getres = var->get(fval,count[iv]);
                            break;
                        case ncLong:
                            getres = var->get(lval,count[iv]);
                            break;
                        default:
                            cerr << "Error in get of " << var->name() << " type not supported" << endl;
                            exit(1);

                        }
                        if (!getres) {
                            cerr << "Error in get of " << var->name() << " rec: " <<
                                n << " station: " << stations[j] << endl;
                            continue;
                        }
                        for (k = 0; k < otherdims[iv]; k++)
                            switch(var->type()) {
                            case ncDouble:
                                cout << dval[k] << ' ';
                                break;
                            case ncFloat:
                                cout << fval[k] << ' ';
                                break;
                            case ncLong:
                                cout << lval[k] << ' ';
                                break;
                            default:
                                cout << '?' << ' ';
                                break;
                            }
                    }
                }
            }
            cout << endl;
        }
    }
}


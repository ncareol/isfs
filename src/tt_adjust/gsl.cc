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
*/

#include "gsl.h"
#include <string.h> // memset

void gsl_fit_init(struct gsl_fit_sums& sums)
{
    memset(&sums,0,sizeof(sums));
}

void gsl_fit_linear_add_point(double x, double y,struct gsl_fit_sums& sums)
{
    sums.x += x;
    sums.y += y;
    sums.xx += x * x;
    sums.xy += x * y;
    sums.n++;
}

void gsl_fit_linear_compute(struct gsl_fit_sums& sums, double *c0, double *c1)
{
    sums.x /= sums.n;
    sums.y /= sums.n;
    sums.xx = sums.xx / sums.n - sums.x * sums.x;
    sums.xy = sums.xy / sums.n - sums.x * sums.y;

    double b = sums.xy / sums.xx;
    double a = sums.y - sums.x * b;
    *c0 = a;
    sums.c1 = *c1 = b;
}

void gsl_fit_linear_add_resid(double x, double y,struct gsl_fit_sums& sums)
{
    double dx = x - sums.x;
    double dy = y - sums.y;
    double d = dy - sums.c1 * dx;
    sums.d2 += d * d;
}

void gsl_fit_linear_compute_resid(struct gsl_fit_sums& sums,
     double *cov_00, double *cov_01, double *cov_11, double *sumsq)
{
    double s2;
    s2 = sums.d2 / (sums.n - 2.0);        /* chisq per degree of freedom */

    *cov_00 = s2 * (1.0 / sums.n) * (1 + sums.x * sums.x / sums.xx);
    *cov_11 = s2 * 1.0 / (sums.n * sums.xx);
    *cov_01 = s2 * (-sums.x) / (sums.n * sums.xx);
    *sumsq = sums.d2;
}

void gsl_fit_wlinear_add_point(double x, double w, double y,struct gsl_fit_sums& sums)
{
    if (w > 0.0) {
        x *= w;
        y *= w;
        sums.x += x;
        sums.y += y;
        sums.xx += x * x;
        sums.xy += x * y;
        sums.W += w;
    }
    sums.n++;
}

void gsl_fit_wlinear_compute(struct gsl_fit_sums& sums, double *c0, double *c1)
{
    sums.x /= sums.W;
    sums.y /= sums.W;
    sums.xx = sums.xx / sums.W - sums.x * sums.x;
    sums.xy = sums.xy / sums.W - sums.x * sums.y;

    double b = sums.xy / sums.xx;
    double a = sums.y - sums.x * b;
    *c0 = a;
    sums.c1 = *c1 = b;
}

void gsl_fit_wlinear_add_resid(double x, double w, double y,struct gsl_fit_sums& sums)
{
    double dx = x - sums.x;
    double dy = y - sums.y;
    double d = dy - sums.c1 * dx;
    sums.d2 += w * d * d;
}

void gsl_fit_wlinear_compute_resid(struct gsl_fit_sums& sums,
     double *cov_00, double *cov_01, double *cov_11, double *sumsq)
{
    *cov_00 = (1.0 / sums.W) * (1 + sums.x * sums.x / sums.xx);
    *cov_11 = 1.0 / (sums.W * sums.xx);
    *cov_01 = -sums.x / (sums.W * sums.xx);
    *sumsq = sums.d2;
}


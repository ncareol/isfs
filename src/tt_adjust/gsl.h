// -*- mode: C++; indent-tabs-mode: nil; c-basic-offset: 4; tab-width: 4; -*-
// vim: set shiftwidth=4 softtabstop=4 expandtab:
//
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

#include <sys/types.h>

/**
 * Sums to be saved between the calls of gsl_fit_linear_add_point and
 * the computation of the results.
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
    double W;
    gsl_fit_sums(): x(0),y(0),xx(0),xy(0),c1(0),d2(0),n(0),W(0) {}
    void zero() { x = y = xx = xy = c1 = d2 = W = 0; n = 0; }
};

extern void gsl_fit_init(struct gsl_fit_sums& sums);

extern void gsl_fit_linear_add_point(double x, double y,struct gsl_fit_sums& sums);

extern void gsl_fit_linear_compute(struct gsl_fit_sums& sums, double *c0, double *c1);

extern void gsl_fit_linear_add_resid(double x, double y,struct gsl_fit_sums& sums);

extern void gsl_fit_linear_compute_resid(struct gsl_fit_sums& sums,
     double *cov_00, double *cov_01, double *cov_11, double *sumsq);

extern void gsl_fit_wlinear_add_point(double x, double w, double y,struct gsl_fit_sums& sums);

extern void gsl_fit_wlinear_compute(struct gsl_fit_sums& sums, double *c0, double *c1);

extern void gsl_fit_wlinear_add_resid(double x, double w, double y,struct gsl_fit_sums& sums);

extern void gsl_fit_wlinear_compute_resid(struct gsl_fit_sums& sums,
     double *cov_00, double *cov_01, double *cov_11, double *sumsq);

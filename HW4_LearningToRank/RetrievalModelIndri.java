/**
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

    public static double lambda = 0.4;
    public static double mu = 2500;

    //empty constructor
    public RetrievalModelIndri(){}

    //constructor with certain values
    public RetrievalModelIndri(double lambda, double mu) {
        RetrievalModelIndri.lambda = lambda;
        RetrievalModelIndri.mu = mu;
    }

    public String defaultQrySopName() {
        return new String("#and");
    }

}

/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

    public static double k1 = 1.2;
    public static double b = 0.75;
    public static double k3 = 0;

    //empty constructor
    public RetrievalModelBM25(){}

    //constructor with certain values
    public RetrievalModelBM25(double k1, double b, double k3) {
        RetrievalModelBM25.k1 = k1;
        RetrievalModelBM25.b = b;
        RetrievalModelBM25.k3 = k3;
    }

    public String defaultQrySopName() {
        return new String("#sum");
    }

}

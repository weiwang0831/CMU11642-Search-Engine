/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;

/**
 * The AND operator for all retrieval models.
 */
public class QrySopWAnd extends QrySop {

    public ArrayList<Double> weights;
    public double ttlWeights = 0;

    public double sumWeights(ArrayList<Double> weights) {
        double ttl = 0;
        for (double w : weights) {
            ttl += w;
        }
        return ttl;
    }

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    /**
     * Get a score for the document that docIteratorHasMatch matched.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            return this.getscoreIndri(r);
        } else {
            throw new IllegalArgumentException(r.getClass().getName() + " doesn't support the WSUM operator.");
        }
    }

    /**
     * getScore for the BM25 retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getscoreIndri(RetrievalModel r) throws IOException {

        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double score = 1.0;

            //loop through args and get multiplication on scores
            for (int i = 0; i < this.args.size(); i++) {
                QrySop q_i = (QrySop) this.args.get(i);
                double weight_rate = weights.get(i) / ttlWeights;

                //calculation
                if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == this.docIteratorGetMatch()) {
                    score *= Math.pow(q_i.getScore(r), weight_rate);
                } else {
                    score *= Math.pow(q_i.getDefaultScore(r, this.docIteratorGetMatch()), weight_rate);
                }
            }
            return score;
        }

    }

    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {

        double score = 1.0;                      // Initialize the score

        /* Return the multiplication of scores of all query arguments.
         * Note that AND in Indri is different from AND in Boolean, it uses
         * docIteratorHasMatchMin, so we need to check whether docid matches.
         */
        for (int i = 0; i < this.args.size(); i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            double weight_rate = weights.get(i) / ttlWeights;

            score *= Math.pow(q_i.getDefaultScore(r, docid), weight_rate);
        }
        return score;
    }
}

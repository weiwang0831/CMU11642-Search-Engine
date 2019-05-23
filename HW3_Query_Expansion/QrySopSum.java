/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 * The AND operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

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

        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else {
            throw new IllegalArgumentException(r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    /**
     * getScore for the BM25 retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreBM25(RetrievalModel r) throws IOException {

        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double sum_result = 0.0;
            int docid = this.docIteratorGetMatch();
            for (Qry q : this.args) {
                QrySop q_i = (QrySop) q;
                if (q_i.docIteratorHasMatch(r) && docid == q_i.docIteratorGetMatch()) {
                    sum_result += q_i.getScore(r);
                }
            }
            return sum_result;
        }

    }

    public double getDefaultScore(RetrievalModel r, int docid) {
        return 0;
    }
}

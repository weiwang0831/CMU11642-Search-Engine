/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 * The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if (r instanceof RetrievalModelIndri){
            return this.docIteratorHasMatchMin(r);
        }
        return this.docIteratorHasMatchAll(r);
    }

    /**
     * Get a score for the document that docIteratorHasMatch matched.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
        }
    }

    /**
     * getScore for the UnrankedBoolean retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     * getScore for the UnrankedBoolean retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return this.getScoreAnd(r);
        }
    }

    private double getScoreAnd(RetrievalModel r) throws IOException {

        double result = Double.MAX_VALUE;
        for (Qry arg : this.args) {
            result = Math.min(result, ((QrySop) arg).getScore(r));
        }
        return result;
    }

    /**
     * getScore for the Indri retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri(RetrievalModel r) throws IOException {
        double score = 1.0;

        //loop through args and get multiplication on scores
        for (Qry q : this.args) {
            QrySop q_i = (QrySop) q;

            //the double here for 1/size is very imporant!!! if missing, the score calculation will be different
            if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == this.docIteratorGetMatch()) {
                score *= Math.pow(q_i.getScore(r), 1.0 / (double)this.args.size());
            } else {
                score *= Math.pow(q_i.getDefaultScore(r, this.docIteratorGetMatch()), (double)1 / this.args.size());
            }
        }
        return score;
    }


    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        double score = 1.0;

        for (Qry q : this.args) {
            QrySop q_i = (QrySop) q;
            score *= Math.pow(q_i.getDefaultScore(r, docid), 1.0 / this.args.size());
        }

        return score;
    }

}

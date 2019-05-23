/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 * The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

    /**
     *  Document-independent values that should be determined just once.
     *  Some retrieval models have these, some don't.
     */

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchFirst(r);
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
        } else if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SCORE operator.");
        }
    }

    /**
     * getScore for the Unranked retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     * getScore for the Ranked retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return ((QryIop) this.args.get(0)).docIteratorGetMatchPosting().tf;
        }
    }

    /**
     * getScore for the Ranked retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreBM25(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {

            double k1 = RetrievalModelBM25.k1;
            double b = RetrievalModelBM25.b;
            double k3 = RetrievalModelBM25.k3;

            QryIop q = (QryIop) this.args.get(0);

            double N = (double) Idx.getNumDocs();
            double df = (double) q.getDf();
            double tf = (double) q.docIteratorGetMatchPosting().tf;
            double qtf = (double) 1;
            double doclen = (double) Idx.getFieldLength(q.getField(), this.docIteratorGetMatch());

            double tmp_sum = (double) Idx.getSumOfFieldLengths(q.getField());
            double tmp_length = (double) Idx.getDocCount(q.getField());
            double avg_doclen = tmp_sum / tmp_length;

            double idf = Math.max(Math.log((N - df + 0.5) / (df + 0.5)), 0.0);
            double tf_weight = (tf) / (tf + k1 * (1 - b + b * (doclen / avg_doclen)));
            double user_weight = ((k3 + 1) * qtf) / (k3 + qtf);

            double score = idf * tf_weight * user_weight;
            return score;
        }
    }

    /**
     * getScore for the Indri retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {

        double mu = RetrievalModelIndri.mu;
        double lambda = RetrievalModelIndri.lambda;

        QryIop q = (QryIop) this.args.get(0);

        double tf = (double) q.docIteratorGetMatchPosting().tf;
        double ctf = (double) q.getCtf();
        double tmp_sum = (double) Idx.getSumOfFieldLengths(q.getField());
        double tmp_length = (double) Idx.getFieldLength(q.getField(), q.docIteratorGetMatch());
        double pMLE = ctf / tmp_sum;

        double score = (1 - lambda) * (tf + mu * pMLE) / (tmp_length + mu) + lambda * pMLE;

        return score;
    }

    /**
     * Get default score when docIteratehasMatch not match
     *
     * @param r
     * @param docid
     * @return
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            double mu = RetrievalModelIndri.mu;
            double lambda = RetrievalModelIndri.lambda;

            QryIop q = (QryIop) this.args.get(0);

            double ctf = (double) q.getCtf();
            //extra smoothing when ctf=0. let it equals to 0.5 for calculation on pMLE
            if (ctf == 0) {
                ctf = 0.5;
            }
            double tmp_sum = (double) Idx.getSumOfFieldLengths(q.getField());
            double tmp_length = (double) Idx.getFieldLength(q.getField(), docid);
            double pMLE = ctf / tmp_sum;

            double score = (1 - lambda) * mu * pMLE / (tmp_length + mu) + lambda * pMLE;
            return score;
        } else {
            return 0.0;
        }
    }

    /**
     * Initialize the query operator (and its arguments), including any
     * internal iterators.  If the query operator is of type QryIop, it
     * is fully evaluated, and the results are stored in an internal
     * inverted list that may be accessed via the internal iterator.
     *
     * @param r A retrieval model that guides initialization
     * @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException {

        Qry q = this.args.get(0);
        q.initialize(r);
    }

}

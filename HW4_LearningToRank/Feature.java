import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Feature {

    public String[] featureArrayIdx = new String[18];
    public Map<String, String> parameters = new HashMap<>();
    public double k1;
    public double b;
    public double k3;
    public double lambda;
    public double mu;

    public Feature(String[] featureArrayIdx, Map<String, String> parameters) {
        this.featureArrayIdx = featureArrayIdx;
        this.parameters = parameters;
        k1 = Double.valueOf(parameters.get("BM25:k_1"));
        b = Double.valueOf(parameters.get("BM25:b"));
        k3 = Double.valueOf(parameters.get("BM25:k_3"));
        mu = Double.valueOf(parameters.get("Indri:mu"));
        lambda = Double.valueOf(parameters.get("Indri:lambda"));
    }

    public HashMap<Integer, Double> constructFeatureMap(int docId, String[] terms) throws IOException {
        HashMap<Integer, Double> featureScore = new HashMap<>();

        //start by indicating different features
        //f1: Spam score for d (read from index)
        if (!featureArrayIdx[0].equals("")) {
            int spamScore = Integer.parseInt(Idx.getAttribute("spamScore", docId));
            featureScore.put(1, (double) spamScore);
        }
        //f2: Url depth for d(number of '/' in the rawUrl field)
        if (!featureArrayIdx[1].equals("")) {
            String rawUrl = Idx.getAttribute("rawUrl", docId);
            int depth = rawUrl.length() - rawUrl.replace("/", "").length();
            featureScore.put(2, (double) depth);
        }

        //f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
        if (!featureArrayIdx[2].equals("")) {
            String rawUrl = Idx.getAttribute("rawUrl", docId);
            int contains = 0;
            if (rawUrl.contains("wikipedia.org")) {
                contains = 1;
            }
            featureScore.put(3, (double) contains);
        }

        //f4: PageRank score for d (read from index)
        if (!featureArrayIdx[3].equals("")) {
            float prScore = Float.parseFloat(Idx.getAttribute("PageRank", docId));
            featureScore.put(4, (double) prScore);
        }

        //f5: BM25 score for <q, dbody>
        if (!featureArrayIdx[4].equals("")) {
            double score = getBM25Score(docId, "body", terms);
            featureScore.put(5, score);
        }

        //f6: Indri score for <q, dbody>.
        if (!featureArrayIdx[5].equals("")) {
            double score = getIndriScore(docId, "body", terms);
            featureScore.put(6, score);
        }

        //f7: Term overlap score (also called Coordination Match) for <q, dbody>.
        //Hint: Term overlap is defined as the percentage of query terms that match the document field.
        if (!featureArrayIdx[6].equals("")) {
            double score = getTermOverlap(docId, "body", terms);
            featureScore.put(7, score);
        }
        //f8: BM25 score for <q, dtitle>
        if (!featureArrayIdx[7].equals("")) {
            double score = getBM25Score(docId, "title", terms);
            featureScore.put(8, score);
        }
        //f9: Indri score for <q, dtitle>
        if (!featureArrayIdx[8].equals("")) {
            double score = getIndriScore(docId, "title", terms);
            featureScore.put(9, score);
        }
        //f10: Term overlap score (also called Coordination Match) for <q, dtitle>
        if (!featureArrayIdx[9].equals("")) {
            double score = getTermOverlap(docId, "title", terms);
            featureScore.put(10, score);
        }
        //f11: BM25 score for <q, durl>
        if (!featureArrayIdx[10].equals("")) {
            double score = getBM25Score(docId, "url", terms);
            featureScore.put(11, score);
        }
        //f12: Indri score for <q, durl>
        if (!featureArrayIdx[11].equals("")) {
            double score = getIndriScore(docId, "url", terms);
            featureScore.put(12, score);
        }
        //f13: Term overlap score (also called Coordination Match) for <q, durl>.
        if (!featureArrayIdx[12].equals("")) {
            double score = getTermOverlap(docId, "url", terms);
            featureScore.put(13, score);
        }
        //f14: BM25 score for <q, dinlink>.
        if (!featureArrayIdx[13].equals("")) {
            double score = getBM25Score(docId, "inlink", terms);
            featureScore.put(14, score);
        }
        //f15: Indri score for <q, dinlink>.
        if (!featureArrayIdx[14].equals("")) {
            double score = getIndriScore(docId, "inlink", terms);
            featureScore.put(15, score);
        }
        //f16: Term overlap score (also called Coordination Match) for <q, dinlink>
        if (!featureArrayIdx[15].equals("")) {
            double score = getTermOverlap(docId, "inlink", terms);
            featureScore.put(16, score);
        }
        //f17: Custom feature - document length
        if (!featureArrayIdx[16].equals("")) {
            double score = (double) Idx.getFieldLength("body", docId);
            featureScore.put(17, score);
        }
        //f18: Custom features - title length
        if (!featureArrayIdx[17].equals("")) {
            double score = (double) Idx.getFieldLength("title", docId);
            featureScore.put(18, score);
        }
        return featureScore;
    }

    public double getBM25Score(int docid, String field, String[] terms) throws IOException {
        double result = 0.0;

        double doclen = (double) (Idx.getFieldLength(field, docid));
        double avgLen = (double) Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field);
        TermVector tv = new TermVector(docid, field);
        //the document don't have this field
        if (tv.stemsLength() == 0) {
            return 0.0;
        }

        for (String term : terms) {
            int idx = tv.indexOfStem(term);
            if (idx == -1) {
                continue;
            }
            int tf = tv.stemFreq(idx);
            int df = tv.stemDf(idx);
            double idf = Math.max(0.0, Math.log((double) Idx.getNumDocs() - (double) df + 0.5) / (double) df + 0.5);
            double tfw = tf / (tf + k1 * (1.0 - b + b * (doclen / avgLen)));
            double score = idf * tfw;
            result += score;
        }
        return result;
    }

    public double getIndriScore(int docid, String field, String[] terms) throws IOException {
        double result = 1.0;

        TermVector tv = new TermVector(docid, field);

        double tmp_sum = (double) Idx.getSumOfFieldLengths(field);
        double tmp_length = (double) Idx.getFieldLength(field, docid);
        double tf;
        int count = 0;

        for (String term : terms) {
            int idx;
            //the document don't have this field
            if (tv.stemsLength() == 0) {
                idx = -1;
            } else {
                idx = tv.indexOfStem(term);
            }
            double ctf = Idx.getTotalTermFreq(field, term);
            double pMLE = ctf / tmp_sum;

            if (idx == -1) {
                //if term is not exist
                count++;
                result *= (1 - lambda) * mu * pMLE / (tmp_length + mu) + lambda * pMLE;
            } else {
                //if the term exist
                tf = (double) tv.stemFreq(idx);
                result *= (1 - lambda) * (tf + mu * pMLE) / (tmp_length + mu) + lambda * pMLE;
            }
        }
        //if the result is completely not match, give a 0 score
        if (count == terms.length) {
            result = 0.0;
        } else {
            result = Math.pow(result, 1.0 / terms.length);
        }
        return result;
    }

    public double getTermOverlap(int docid, String field, String[] terms) throws IOException {
        double result = 0.0;
        int countMatch = 0;
        TermVector tv = new TermVector(docid, field);
        //the document don't have this field
        if (tv.stemsLength() == 0) {
            return 0.0;
        }

        for (String term : terms) {
            int idx = tv.indexOfStem(term);
            if (idx != -1) {
                countMatch++;
            }
        }
        if (terms.length > 0) {
            result = (double) countMatch / (double) terms.length;
        }
        return result;
    }

}

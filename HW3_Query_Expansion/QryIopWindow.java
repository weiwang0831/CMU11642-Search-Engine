/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The NEAR operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {


    private int maxDiff;

    public QryIopWindow() {
    }

    public QryIopWindow(int maxDiff) {
        this.maxDiff = maxDiff;
    }

    /**
     * Evaluate the query operator; the result is an internal inverted
     * list that may be accessed via the internal iterators.
     *
     * @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate() throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.

        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) {
            return;
        }

        //make sure inverted lists have common doc id
        while (this.docIteratorHasMatchAll(null)) {

            //boolean check if we should add this result to position list
            boolean shouldAdd;
            //boolean check if we should move document to next id
            boolean move = false;

            //  Each pass of the loop adds 1 document to result inverted list
            //  until all of the argument inverted lists are depleted.
            List<Integer> positions = new ArrayList<>();
            //get the current minimum document id
            int minDocId = this.args.get(0).docIteratorGetMatch();

            //Iterate through locations for this document
            while (true) {

                //if no location has match
                if (!((QryIop) this.args.get(0)).locIteratorHasMatch()) {
                    break;
                }
                //the very first match location on left inverted lost
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                int loc_i, loc_diff, indx = -1;
                //shouldAdd = true;

                //loop through each query, make sure they are WINDOW
                for (int i = 0; i < this.args.size(); i++) {

                    //get the pointer point to the location that match the location (at least past it)
                    QryIop q_i = (QryIop) this.args.get(i);
                    //q_i.locIteratorAdvancePast(loc_0);
                    if (!q_i.locIteratorHasMatch()) {
                        max = Integer.MIN_VALUE;
                        move = true;
                        break;
                    }
                    loc_i = q_i.locIteratorGetMatch();
                    if (loc_i < min) {
                        indx = i;
                    }
                    min = Math.min(loc_i, min);
                    max = Math.max(loc_i, max);
                }

                loc_diff = Math.abs(max - min);

                //check if the location differences is larger than max difference
                //if so, move the min location pointer to next location
                if (loc_diff <= maxDiff) {
                    shouldAdd = true;
                } else {
                    shouldAdd = false;
                }

                //if the boolean shouldAdd is true, which indicates it pass all break condition
                //then put max into the position list
                //advance all iterator to the next location
                if (shouldAdd) {
                    positions.add(max);
                    for (Qry arg : this.args) {
                        ((QryIop) arg).locIteratorAdvance();
                    }
                } else {
                    ((QryIop) this.args.get(indx)).locIteratorAdvance();
                    if (move) {
                        break;
                    }
                }
            }

            //if position list for this document is not empty
            if (positions.size() > 0) {
                Collections.sort(positions);
                this.invertedList.appendPosting(minDocId, positions);
            }

            //continue to next document id
            this.args.get(0).docIteratorAdvancePast(minDocId);
        }
    }
}

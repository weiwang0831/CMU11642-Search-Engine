/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

/**
 * The NEAR operator for all retrieval models.
 */
public class QryIopNear extends QryIop {


    private int maxDiff;

    public QryIopNear() {
    }

    public QryIopNear(int maxDiff) {
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
                int loc_0 = ((QryIop) this.args.get(0)).locIteratorGetMatch();
                int loc_1, loc_diff;
                shouldAdd = true;

                //loop through each query, make sure they are NEAR by pair
                for (int i = 1; i < this.args.size(); i++) {

                    //get the pointer point to the location that match the leftmost location (at least past it)
                    QryIop q_i = (QryIop) this.args.get(i);
                    q_i.locIteratorAdvancePast(loc_0);
                    if (!q_i.locIteratorHasMatch()) {
                        shouldAdd = false;
                        move = true;
                        break;
                    }
                    loc_1 = q_i.locIteratorGetMatch();
                    loc_diff = loc_1 - loc_0;

                    //check if the location differences is larger than max difference
                    //if so, move the left location pointer to next location
                    if (loc_diff <= maxDiff) {
                        loc_0 = loc_1;
                    } else {
                        ((QryIop) this.args.get(0)).locIteratorAdvance();
                        shouldAdd = false;
                        break;
                    }
                }

                //if the boolean shouldAdd is true, which indicates it pass all break condition
                //then put loc_0 into the position list
                //advance all iterator to the next location
                if (shouldAdd) {
                    positions.add(loc_0);
                    for (Qry arg : this.args) {
                        ((QryIop) arg).locIteratorAdvance();
                    }
                } else {
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

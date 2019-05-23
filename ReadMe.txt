                          QryEval, version 3.3.3
                            January 20, 2018


This software illustrates the architecture for the portion of a search
engine that evaluates queries.  It is a template for class homework
assignments, so it emphasizes simplicity over efficiency.  It has just
a few main components.

QryEval is the main class. Given a parameter file which specifies the
index path and query file in a key value pair (e.g.,
index=path_to_index), it opens the index, evaluates the queries, and
prints the results. You will need to modify this class so that it
reads in more parameters, and writes results to another file.  You
will also need to extend the query parser. This should be fairly
simple; the only slightly complicated part is handling operators that
have weights.

Qry is an abstract class for all query operators (e.g., AND, OR, SYN,
NEAR/n, WINDOW/n, etc).  It has just a few data structures and methods
that are common to all query operators.  The rest of the class is
just abstract definitions of query operator capabilities.

QryIop and QrySop are extensions of Qry that are specialized for
query opeators that produce inverted lists (e.g., TERM, SYN, NEAR/n)
and query operators that produce score lists (e.g., AND, SCORE).

QryIopTerm, QryIopSyn, and QrySopOr are query operator
implementations for term (e.g., "apple"), synonym ("SYN"), and boolean
OR query operators.

This implementation contains 4 types of query operators:

  * The Term operator, which just fetches an inverted list from the index;

  * The Syn operator, which combines inverted lists;

  * The Score operator, which converts an inverted list into a score list; and

  * The Or operator, which combines score lists.

Query operator behavior depends upon the type of retrieval model being
used.  Some retrieval models have parameters.  RetrievalModel is an
abstract class for all retrieval models.  Its subclasses provide
places to store parameters and methods used to accomplish different
types of query evaluation.  This implementation contains a
RetrievalModelUnrankedBoolean that contains no parameters, but notice
how the behavior of QrySopScore and QrySopOr can be altered depending
upon the specific retrieval model being used.

You will need to implement several other retrieval models.  For
example, to implement the Indri retrieval model, do the following.

  * Read the retrieval model name from the parameter file, and
    create the appropriate retrieval model.

  * Modify the QrySopScore function to calculate a query likelihood
    score with Dirichlet smoothing, and to calculate default scores.

  * Modify the getScore method of each query operator of type QrySop
    to to implement the Indri score combinations.

This architecture makes it easy to support multiple retrieval models
within one implementation.

The ScoreList class provides a very simple implementation of a score
list.

The InvList class provides a very simple implementation of an inverted
list.

Query expansion and text mining operations require random access to
document term vectors. (Recall that a document term vector is a parsed
representation of a document. See lecture notes for details.)  The
TermVector class provides a simple, Indri-like API that gives access
to the number of terms in a document, the vocabulary of terms that
occur in the document, the terms that occur at each position in the
document, and the frequency of each term.

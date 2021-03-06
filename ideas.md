How do we handle:
* Deletion (when another user hasn't explicitly done that delete action)
* Error detection
* Insertions (when a subset of children match, rather than a whole subtree)

Ideas:
* A "what's wrong" button that compares your program to known final solutions and looks for mismatched subtrees to make suggestions
* Before being added to the hint graph, we should make sure actions are "kept." If a user makes a change, does that change survive until the end of their program? If not, was it a necessary incremental step or a mistake/experiement. We want to get rid of experimentation on our hints.
* Look for contradicting hints. In this case, there may be more than one way to modify a given set of code in two different places in the final program. Lacking subgoals, out best best is to rely more on context than quality in these situations.

Highlight Ideas
* Side scripts should be a separate, universal nesting level in snapshot matching.
* Make an expert solution editor with interchangeable parts.

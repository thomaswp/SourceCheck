
library(readr)
library(plyr)
library(R.oo)

loadRatings <- function(names) {
  allRatings <- NULL
  for (name in names) {
    ratings <- read_csv(paste0("../../data/hint-rating/isnapF16-F17/algorithms/", name, ".csv"))
    ratings$source <- name
    if (is.null(allRatings)) allRatings <- ratings
    else allRatings <- rbind(allRatings, ratings)
  }
  allRatings$validity[is.na(allRatings$validity)] <- 0
  allRatings
}

selectHintsForManualRating <- function(ratings, maxPerType=50) {
  set.seed(1234)
  dedup <- ddply(ratings, c("assignmentID", "requestID", "outcome", "type", "validity", "priority", "diff"), summarize, idx=sample.int(length(source), 1), source=source[[idx]], weight=weight[[idx]], hintID=hintID[[idx]])
  samples <- ddply(dedup, c("assignmentID", "requestID", "source", "type"), summarize, idx=sample(which(weight == max(weight)), 1), diff=diff[[idx]], hintID=hintID[[idx]])
  samples$priority <- 0
  for (type in unique(samples$type)) {
    count <- sum(samples$type == type)
    samples$priority[samples$type == type] <- sample(count, count)
  }
  samples[,-4]
}

runMe <- function() {
  ratings <- loadRatings(c("SourceCheck", "CTD", "PQGram", "chf_with_past"))
  samples <- selectHintsForManualRating(ratings)
  first150 <- samples[samples$priority <= 50,]
}

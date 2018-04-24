
library(readr)
library(plyr)

loadRatings <- function(names) {
  allRatings <- NULL
  for (name in names) {
    ratings <- read_csv(paste0("../../data/hint-rating/isnapF16-F17/algorithms/", name, ".csv"))
    ratings$source <- name
    if (is.null(allRatings)) allRatings <- ratings
    else allRatings <- rbind(allRatings, ratings)
  }
  standard <- read_csv("../../data/hint-rating/isnapF16-F17/gold-standard.csv")
  years <- ddply(standard, c("requestID"), summarize, year=head(year, 1))
  allRatings <- merge(allRatings, years)
  allRatings$validity[is.na(allRatings$validity)] <- 0
  allRatings
}

selectHintsForManualRating <- function(ratings, maxPerType=50) {
  set.seed(1234)
  dedup <- ddply(ratings, c("assignmentID", "year", "requestID", "outcome", "type", "validity", "priority", "diff"), summarize, idx=sample.int(length(source), 1), source=source[[idx]], weight=weight[[idx]], hintID=hintID[[idx]])
  samples <- ddply(dedup, c("assignmentID", "year", "requestID", "source", "type"), summarize, idx=sample(which(weight == max(weight)), 1), diff=diff[[idx]], hintID=hintID[[idx]])
  samples$priority <- 0
  for (type in unique(samples$type)) {
    count <- sum(samples$type == type)
    samples$priority[samples$type == type] <- sample(count, count)
  }
  samples <- subset(samples, select = -c(idx))
  samples <- samples[order(samples$assignmentID, samples$year, samples$requestID, (samples$priority-1) %/% 25, samples$hintID),]
  samples
}

# TODO: Be careful - this doesn't escape the diff
writeSQL <- function(ratings, path) {
  sink(path)
  for (year in unique(ratings$year)) {
    rows <- ratings[ratings$year==year,]
    cat(paste0("use snap_", year, ";\n"))
    for (i in 1:nrow(rows)) {
      line = sprintf("INSERT INTO `handmade_hints` (`hid`, `userID`, `rowID`, `trueAssignmentID`, `hintCode`) VALUES ('%s', '%s', '%s', '%s', '%s');\n",
                     rows[i, "hintID"], "algorithms", rows[i, "requestID"], rows[i, "assignmentID"], rows[i, "diff"])
      cat(line)
    }
  }
  sink()
}

runMe <- function() {
  ratings <- loadRatings(c("SourceCheck", "CTD", "PQGram", "chf_with_past"))
  samples <- selectHintsForManualRating(ratings)
  
  write.csv(subset(samples, select=c(assignmentID, year, requestID, hintID, priority)), "C:/Users/Thomas/Desktop/samples.csv", row.names = F)
  first75 <- samples[samples$priority <= 25,]
  second75 <- samples[samples$priority > 25 & samples$priority <= 50,]
  writeSQL(first75, "C:/Users/Thomas/Desktop/samples.sql")
  writeSQL(second75, "C:/Users/Thomas/Desktop/samples2.sql")
}

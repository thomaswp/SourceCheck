
library(readr)
library(plyr)
library(ggplot2)
library(reshape2)

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))

loadRatings <- function(dataset, names) {
  allRatings <- NULL
  for (name in names) {
    ratings <- read_csv(paste0("../../data/hint-rating/", dataset, "/algorithms/", name, ".csv"))
    ratings$source <- name
    if (is.null(allRatings)) allRatings <- ratings
    else allRatings <- rbind(allRatings, ratings)
  }
  standard <- read_csv(paste0("../../data/hint-rating/", dataset, "/gold-standard.csv"))
  years <- ddply(standard, c("requestID"), summarize, year=head(year, 1))
  allRatings <- merge(allRatings, years)
  allRatings$validity[is.na(allRatings$validity)] <- 0
  allRatings$dataset <- dataset
  allRatings
}

selectHintsForManualRating <- function(ratings, maxPerType=50) {
  set.seed(1234)
  # NOTE: This method does not take the validity of the matched TutorHint into account, since the purpose is to
  # determine the best level of tutor accord to use
  dedup <- ddply(ratings, c("assignmentID", "year", "requestID", "outcome", "type", "validity", "priority", "diff"), summarize,
                 idx=sample.int(length(source), 1), source=source[[idx]], weight=weight[[idx]], hintID=hintID[[idx]])
  samples <- ddply(dedup, c("assignmentID", "year", "requestID", "source", "type"), summarize,
                   idx=sample(which(weight == max(weight)), 1), diff=diff[[idx]], hintID=hintID[[idx]])
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
    cat("DELETE FROM handmade_hints WHERE userID='algorithms';\n")
    for (i in 1:nrow(rows)) {
      line = sprintf("INSERT INTO `handmade_hints` (`hid`, `userID`, `rowID`, `trueAssignmentID`, `hintCode`) VALUES ('%s', '%s', '%s', '%s', '%s');\n",
                     rows[i, "hintID"], "algorithms", rows[i, "requestID"], rows[i, "assignmentID"], rows[i, "diff"])
      cat(line)
    }
  }
  sink()
}

getSamples <- function() {
  # Don't include both hint factories
  ratings <- loadRatings("isnapF16-F17", c("SourceCheck", "CTD", "PQGram", "chf_with_past"))
  samples <- selectHintsForManualRating(ratings)
  
  write.csv(subset(samples, select=c(assignmentID, year, requestID, hintID, priority)), "C:/Users/Thomas/Desktop/samples.csv", row.names = F)
  write.csv(samples, "C:/Users/Thomas/Desktop/samples-full.csv", row.names = F)
  writeSQL(samples[samples$priority <= 25,], "C:/Users/Thomas/Desktop/samples75.sql")
  writeSQL(samples[samples$priority <= 50,], "C:/Users/Thomas/Desktop/samples150.sql")
  writeSQL(samples[samples$priority <= 84,], "C:/Users/Thomas/Desktop/samples252.sql")
}

compare <- function() {
  isnap <- loadRatings("isnapF16-F17", c("SourceCheck", "CTD", "PQGram", "chf_with_past", "chf_without_past"))
  isnap$dataset <- "isnap"
  itap <- loadRatings("itapS16", c("SourceCheck", "CTD", "PQGram", "chf_with_past", "chf_without_past", "ITAP"))
  itap$dataset <- "itap"
  ratings <- rbind(isnap, itap)
  ratings <- ratings[order(ratings$dataset, ratings$assignmentID, ratings$year, ratings$requestID, ratings$source, ratings$order),]
  ratings$scoreFull <- ratings$weightNorm * ifelse(ratings$type=="Full" & ratings$validity >= 2, 1, 0)
  ratings$scorePartial <- ratings$weightNorm * ifelse(ratings$type!="None" & ratings$validity >= 2, 1, 0)
  ratings$source <- factor(ratings$source, c("PQGram", "chf_without_past", "chf_with_past", "CTD", "SourceCheck", "ITAP"))
  ratings$assignmentID <- factor(ratings$assignmentID, c("squiralHW", "guess1Lab", "helloWorld", "firstAndLast", "isPunctuation", "kthDigit", "oneToN"))
  requests <- ddply(ratings, c("dataset", "source", "assignmentID", "requestID"), summarize, scoreFull=sum(scoreFull), scorePartial=sum(scorePartial))
  
  ggplot(requests[requests$dataset=="isnap",], aes(x=source, y=scoreFull)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + facet_wrap(~assignmentID)
  ggplot(requests[requests$dataset=="itap",], aes(x=source, y=scoreFull)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + facet_wrap(~assignmentID)
  ggplot(requests[requests$dataset=="itap",], aes(x=source, y=scorePartial)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + facet_wrap(~assignmentID)
  ggplot(requests, aes(x=source, y=scoreFull)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + facet_wrap(~dataset)
  ggplot(requests, aes(x=source, y=scorePartial)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + facet_wrap(~dataset)
  
  assignments <- ddply(requests, c("dataset", "source", "assignmentID"), summarize, mScoreFull=mean(scoreFull), mScorePartial=mean(scorePartial),
                                                                                    seFull=se(scoreFull), sePartial=se(scorePartial))
  
  plotComparison(assignments, "isnap")
  plotComparison(assignments, "itap")
  plotComparisonTogether(requests)
  
  comp(requests, T, "itap", "SourceCheck", "chf_with_past")
}

plotComparison <- function(assignments, dataset) {
  assignments <- assignments[assignments$dataset == dataset,]
  means <- melt(assignments, id=c("dataset", "source", "assignmentID"), measure.vars=c("mScoreFull", "mScorePartial"), variable.name="type", value.name="mean")
  means$type <- ifelse(means$type == "mScoreFull", "full", "partial")
  ses <- melt(assignments, id=c("dataset", "source", "assignmentID"), measure.vars=c("seFull", "sePartial"), variable.name="type", value.name="se")
  ses$type <- ifelse(ses$type == "seFull", "full", "partial")
  all <- merge(means, ses)
  
  ggplot(all,aes(x=source, y=mean, fill=type)) + geom_bar(stat="identity", position="dodge") + 
    geom_errorbar(aes(ymax=mean+se, ymin=mean-se), position=position_dodge(width=0.9), width=0.5) + 
    facet_wrap(~assignmentID)
    #scale_fill_discrete(name="Match", labels=c("Partial", "Full"))
}

plotComparisonTogether <- function(requests) {
  together <- ddply(requests, c("dataset", "source"), summarize, mScoreFull=mean(scoreFull), mScorePartial=mean(scorePartial),
                       seFull=se(scoreFull), sePartial=se(scorePartial))
  means <- melt(together, id=c("dataset", "source"), measure.vars=c("mScoreFull", "mScorePartial"), variable.name="type", value.name="mean")
  means$type <- ifelse(means$type == "mScoreFull", "full", "partial")
  ses <- melt(together, id=c("dataset", "source"), measure.vars=c("seFull", "sePartial"), variable.name="type", value.name="se")
  ses$type <- ifelse(ses$type == "seFull", "full", "partial")
  all <- merge(means, ses)
  
  ggplot(all,aes(x=source, y=mean, fill=type)) + geom_bar(stat="identity", position="dodge") + 
    geom_errorbar(aes(ymax=mean+se, ymin=mean-se), position=position_dodge(width=0.9), width=0.5) + 
    facet_wrap(~dataset, scales = "free_x")
  #scale_fill_discrete(name="Match", labels=c("Partial", "Full"))
}

comp <- function(requests, partial, dataset, source1, source2) {
  column <- if (partial) "scorePartial" else "scoreFull"
  left <- requests[requests$source==source1 & requests$dataset==dataset, column]
  right <- requests[requests$source==source2 & requests$dataset==dataset, column]
  print(paste(mean(left > right), " vs ", mean(left < right)))
  wilcox.test(left, right, paired=T)
}

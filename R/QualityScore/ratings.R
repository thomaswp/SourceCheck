
library(readr)
library(plyr)
library(ggplot2)
library(reshape2)
library(psych)
library(irr)

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))
ifNA <- function(x, other) ifelse(is.na(x), other, x)

loadRatings <- function(dataset, names, dir="") {
  allRatings <- NULL
  if (dir != "") dir <- paste0(dir, "/")
  for (name in names) {
    ratings <- read_csv(paste0("../../data/hint-rating/", dataset, "/algorithms/", dir, name, ".csv"))
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
                     rows[i, "hintID"], "algorithms", rows[i, "requestID"], rows[i, "assignmentID"], gsub("'", "\\'", rows[i, "diff"], fixed =T))
      cat(line)
    }
  }
  sink()
}

getSamples <- function() {
  # Don't include both hint factories
  sampleRatings <- loadRatings("isnapF16-F17", c("SourceCheck", "CTD", "PQGram", "chf_with_past"), "sampling")
  samples <- selectHintsForManualRating(sampleRatings)
  
  write.csv(subset(samples, select=c(assignmentID, year, requestID, hintID, priority)), "C:/Users/Thomas/Desktop/samples.csv", row.names = F)
  write.csv(samples, "C:/Users/Thomas/Desktop/samples-full.csv", row.names = F)
  writeSQL(samples[samples$priority <= 25,], "C:/Users/Thomas/Desktop/samples75.sql")
  writeSQL(samples[samples$priority <= 50,], "C:/Users/Thomas/Desktop/samples150.sql")
  writeSQL(samples[samples$priority <= 84,], "C:/Users/Thomas/Desktop/samples252.sql")
  
  manual <- read_csv("data/manual.csv")
  manual$consensus <- suppressWarnings(as.integer(manual$consensus))
  manualT1 <- read_csv("data/manual-thomas.csv")
  manualT2 <- read_csv("data/manual-rui.csv")
  manualT3 <- read_csv("data/manual-yihuan.csv")
  manual$t1 <- manualT1$validity
  manual$t2 <- manualT2$validity
  manual$t3 <- manualT3$validity
  manual <- manual[!is.na(manual$consensus),]
  
  verifyRatings(manual, samples, sampleRatings)
  
  newRatings <- loadRatings("isnapF16-F17", c("SourceCheck_sampling", "CTD", "PQGram", "chf_with_past"))
  # 0.83 0.83 0.83 (One tutor)
  # 0.78 0.80 0.81 (Multiple tutors)
  # 0.81 0.81 0.81 (Consensus)
  # Note: to get perfect results for OneTutor or Consensus validity thresholds, you have to
  # re-rate hints with the RatingConfig's highestRequiredValidity set to the right value.
  verifyRatings(manual, samples, newRatings)
  
  
  # alpha = 0.673
  kripp.alpha(t(manual[,c("t1", "t2", "t3")]), method="ordinal")
  # Perfect agreement on 166/252 (65.9%) of them
  mean(manual$t1 == manual$t2 & manual$t2 == manual$t3)
  
  # 0.85 0.85 0.85
  cohen.kappa(cbind(manual$consensus, manual$t1))
  # 0.74 0.76 0.78
  cohen.kappa(cbind(manual$consensus, manual$t2))
  # 0.76 0.78 0.80
  cohen.kappa(cbind(manual$consensus, manual$t3))
}

verifyRatings <- function(manual, samples, ratings) {
  withSamples <- getSampleRatings(samples, ratings)
  withManual <- mergeManual(manual, withSamples)
  printVerify("One", withManual$consensus, withManual$tOne)
  printVerify("Multiple", withManual$consensus, withManual$tMultiple)
  printVerify("Consensus", withManual$consensus, withManual$tConsensus)
  
  columns <- c("year", "requestID", "hintID", "matchID", "type", "source")
  cat("Invalid hints rated valid (consensus):\n")
  print(withManual[withManual$consensus == 0 & withManual$tConsensus == "Full", columns])
  cat("Invalid hints rated valid (+multiple tutors):\n")
  print(withManual[withManual$consensus == 0 & withManual$tMultiple == "Full" & !withManual$tConsensus == "Full", columns])
  cat("Valid hints rated invalid (multiple tutors):\n")
  print(withManual[withManual$consensus == 2 & withManual$tMultiple == "None", columns])
  cat("Valid hints rated invalid (+consensus):\n")
  print(withManual[withManual$consensus == 2 & withManual$tConsensus == "None" & !withManual$tMultiple == "None", columns])
  cat("Valid hints rated partially valid:\n")
  print(withManual[withManual$consensus == 2 & withManual$tConsensus == "Partial", columns])
}

printVerify <- function(name, truth, rating) {
  print(name)
  cat(paste0("Accuracy: ", mean(truth + 1 == as.numeric(rating))), "\n")
  cat(paste0("Accuracy (0): ", mean(truth[truth==0] + 1 == as.numeric(rating[truth==0]))), "\n")
  cat(paste0("Accuracy (1): ", mean(truth[truth==1] + 1 == as.numeric(rating[truth==1]))), "\n")
  cat(paste0("Accuracy (2): ", mean(truth[truth==2] + 1 == as.numeric(rating[truth==2]))), "\n")
  print(table(truth, rating))
  print(cohen.kappa(cbind(truth + 1, as.numeric(rating))))
  cat("\n")
}

getSampleRatings <- function(samples, ratings) {
  ratings[ratings$source == "SourceCheck_sampling","source"] <- "SourceCheck"
  names(samples)[names(samples) == 'priority'] <- 'ratePriority'
  merged <- merge(ratings, samples[,c("requestID", "source", "hintID", "diff", "ratePriority")])
  merged <- merged[!duplicated(merged[,c("requestID", "source", "hintID", "diff")]),]
  merged$type <- ordered(merged$type, c("None", "Partial", "Full"))
  merged
}

mergeManual <- function(manual, samples) {
  names(manual)[names(manual) == 'priority'] <- 'ratePriority'
  manual <- merge(manual[,c("hintID", "consensus", "ratePriority")], samples)
  manual$tOne <- manual$type
  manual$tMultiple <- ordered(ifelse(manual$validity > 1, as.character(manual$type), "None"), c("None", "Partial", "Full"))
  manual$tConsensus <- ordered(ifelse(manual$validity == 3, as.character(manual$type), "None"), c("None", "Partial", "Full"))
  manual
}

findInterestingRequests <- function(requests, ratings) {
  algRequests <- requests[requests$source != "AllTutors",]
  algRequests$year <- sapply(1:nrow(algRequests), function(i) head(ratings$year[ratings$requestID == algRequests[i,"requestID"]], 1))
  
  byRequest <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                      PQGram=scorePartial[source=="PQGram"],
                      chf_without_past=scorePartial[source=="chf_without_past"],
                      chf_with_past=scorePartial[source=="chf_with_past"],
                      CTD=scorePartial[source=="CTD"],
                      SourceCheck=scorePartial[source=="SourceCheck"],
                      ITAP=(if (sum(source=="ITAP") == 0) NA else scorePartial[source=="ITAP"]))
  cor(byRequest[byRequest$dataset=="isnap",5:9])
  cor(byRequest[byRequest$dataset=="itap",5:10])
  
  onlyOne <- function (x) if (length(x) == 1) x else NA
  scores <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                  maxScore = max(scorePartial),
                  best = onlyOne(source[which(scorePartial==maxScore)]),
                  n0=sum(scorePartial==0), n1=sum(scorePartial==1), 
                  n05=sum(scorePartial > 0.5), n01=sum(scorePartial > 0.1), n025=sum(scorePartial > 0.25))
  table(scores$dataset, scores$best)
  table(scores$dataset, scores$n0)
  table(scores$dataset, scores$n01)
  
  byRequest[byRequest$requestID %in% scores[scores$dataset == "isnap" & scores$n01 == 0,"requestID"],]
  scores[scores$dataset == "isnap" & scores$n01 == 0,2:5]
  
  byRequest[byRequest$requestID %in% scores[scores$dataset == "isnap" & scores$n025 == 0,"requestID"],]
  scores[scores$dataset == "isnap" & scores$n025 == 0,2:5]
  
  byRequest[byRequest$requestID %in% scores[scores$dataset == "isnap" & scores$n01 == 1,"requestID"],]
  scores[scores$dataset == "isnap" & scores$n01 == 1,2:6]
  # TODO: refactor!
  test <- merge(scores, ratings, by.x = c("dataset", "assignmentID", "year", "requestID", "best"), by.y = c("dataset", "assignmentID", "year", "requestID", "source"))
  test2 <- test[test$requestID %in% scores[scores$dataset == "isnap" & scores$n01 == 1,"requestID"],]
  test2 <- test2[test2$scorePartial > 0.1,c("dataset", "assignmentID", "year", "requestID", "best", "maxScore", "diff")]
  for (i in 1:nrow(test2)) {
    print(test2[i,1:6])
    cat(test2[i,"diff"])
    cat("\n")
  }
  
  byRequest[byRequest$requestID %in% scores[scores$dataset == "itap" & scores$n05 == 0,"requestID"],]
  scores[scores$dataset == "itap" & scores$n05 == 0,2:5]
  
  byRequest[byRequest$requestID %in% scores[scores$dataset == "itap" & scores$n01 == 1,"requestID"],]
  scores[scores$dataset == "itap" & scores$n01 == 1,2:5]
  test <- merge(scores, ratings, by.x = c("dataset", "assignmentID", "year", "requestID", "best"), by.y = c("dataset", "assignmentID", "year", "requestID", "source"))
  test2 <- test[test$requestID %in% scores[scores$dataset == "itap" & scores$n01 == 1,"requestID"],]
  test2 <- test2[test2$scorePartial > 0.1,c("dataset", "assignmentID", "year", "requestID", "best", "maxScore", "diff")]
  for (i in 1:nrow(test2)) {
    print(test2[i,1:6])
    cat(test2[i,"diff"])
    cat("\n")
  }
  
  table(scores$dataset, scores$n05)
  table(scores$dataset, scores$n1)
}

compare <- function() {
  isnap <- loadRatings("isnapF16-F17", c("SourceCheck", "CTD", "PQGram", "chf_with_past", "chf_without_past", "gross", "AllTutors"))
  isnap$dataset <- "isnap"
  itap <- loadRatings("itapS16", c("SourceCheck", "CTD", "PQGram", "chf_with_past", "chf_without_past", "gross", "ITAP", "AllTutors"))
  itap$dataset <- "itap"
  ratings <- rbind(isnap, itap)
  ratings <- ratings[order(ratings$dataset, ratings$assignmentID, ratings$year, ratings$requestID, ratings$source, ratings$order),]
  ratings$scoreFull <- ratings$weightNorm * ifelse(ratings$type=="Full" & ratings$validity >= 2, 1, 0)
  ratings$scorePartial <- ratings$weightNorm * ifelse(ratings$type!="None" & ratings$validity >= 2, 1, 0)
  # TODO: Priority doesn't match with Java output, but we don't have it for non-consensus hints, so maybe not a priority
  ratings$priorityFull <- ifNA(4 - ratings$priority, 0) * ratings$scoreFull
  ratings$priorityPartial <- ifNA(4 - ratings$priority, 0) * ratings$scorePartial
  ratings$source <- factor(ratings$source, c("PQGram", "gross", "chf_without_past", "chf_with_past", "CTD", "SourceCheck", "ITAP", "AllTutors"))
  ratings$assignmentID <- factor(ratings$assignmentID, c("squiralHW", "guess1Lab", "helloWorld", "firstAndLast", "isPunctuation", "kthDigit", "oneToN"))
  requests <- ddply(ratings, c("dataset", "source", "assignmentID", "requestID"), summarize, 
                    scoreFull=sum(scoreFull), scorePartial=sum(scorePartial),
                    priorityFull=sum(priorityFull), priorityPartial=sum(priorityPartial))
  
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
  
  assignments <- ddply(requests, c("dataset", "source", "assignmentID"), summarize, 
                       mScoreFull=mean(scoreFull), mScorePartial=mean(scorePartial),
                       seFull=se(scoreFull), sePartial=se(scorePartial),
                       mPriorityFull=mean(priorityFull), mPriorityPartial=mean(priorityPartial))
  
  plotComparison(assignments, "isnap")
  plotComparison(assignments, "itap")
  plotComparisonTogether(requests)
  
  plotComparisonStacked(assignments, "isnap")
  plotComparisonStacked(assignments, "itap")
  plotComparisonTogetherStacked(requests)
  
  # Getting slightly inconsistent results for these on different machines:
  
  # Sig p = 0.048
  comp(requests, F, "isnap", "SourceCheck", "CTD")
  # NS  p = 0.817 - big difference between partial and full
  comp(requests, T, "isnap", "SourceCheck", "CTD")
  # Sig p < 0.001
  comp(requests, F, "isnap", "SourceCheck", "chf_with_past")
  
  # NS  p = 0.121
  comp(requests[requests$assignmentID=="guess1Lab",], F, "isnap", "SourceCheck", "CTD")
  # NS  p = 0.264 - As many do better as worse
  comp(requests[requests$assignmentID=="squiralHW",], F, "isnap", "SourceCheck", "CTD")
  
  # NS  p = 0.065
  comp(requests, F, "itap", "ITAP", "SourceCheck")
  # Sig p = 0.024
  comp(requests, T, "itap", "ITAP", "SourceCheck")
  # NS  p = 0.188
  comp(requests, T, "itap", "SourceCheck", "CTD")
  # Sig p < 0.001
  comp(requests, F, "itap", "SourceCheck", "CTD")
  # Sig p = 0.002
  comp(requests, T, "itap", "SourceCheck", "chf_with_past")
  # Sig p = 0.012
  comp(requests, F, "itap", "SourceCheck", "chf_with_past")
  
  kruskal.test(scoreFull ~ source, requests[requests$dataset=="isnap",])
  summary(aov(scoreFull ~ source + assignmentID + source * assignmentID, requests[requests$dataset=="isnap",]))
  
  kruskal.test(scoreFull ~ source, requests[requests$dataset=="itap",])
  summary(aov(scoreFull ~ source + assignmentID + source * assignmentID, requests[requests$dataset=="itap",]))
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

plotComparisonStacked <- function(assignments, dataset) {
  assignments <- assignments[assignments$dataset == dataset,]
  assignments$mScorePartialPlus <- assignments$mScorePartial - assignments$mScoreFull
  melted <- melt(assignments[,c("dataset", "source", "assignmentID", "mScorePartialPlus", "mScoreFull")], id=c("dataset", "source", "assignmentID"))
  ggplot(melted,aes(x=source, y=value, fill=variable)) + geom_bar(stat="identity") +
    facet_wrap(~assignmentID, scales = "free_x")
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

plotComparisonTogetherStacked <- function(requests) {
  together <- ddply(requests, c("dataset", "source"), summarize, mScorePartialPlus=mean(scorePartial)-mean(scoreFull), mScoreFull=mean(scoreFull))
  
  ggplot(melt(together, id=c("dataset", "source")),aes(x=source, y=value, fill=variable)) + geom_bar(stat="identity") +
    scale_fill_discrete(labels=c("Full", "Partial")) + 
    labs(fill="Match Type", x="Algorithm", y="QualityScore") +
    coord_flip() + facet_wrap(~dataset, scales = "free_y") 
  #scale_fill_discrete(name="Match", labels=c("Partial", "Full"))
}

comp <- function(requests, partial, dataset, source1, source2) {
  column <- if (partial) "scorePartial" else "scoreFull"
  left <- requests[requests$source==source1 & requests$dataset==dataset,]
  right <- requests[requests$source==source2 & requests$dataset==dataset,]
  left <- left[order(left$requestID),column]
  right <- right[order(right$requestID),column]
  print(paste(mean(left > right), " vs ", mean(left < right)))
  wilcox.test(left, right, paired=T)
}

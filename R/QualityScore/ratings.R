
library(readr)
library(plyr)
library(ggplot2)
library(reshape2)
library(psych)
library(irr)

se <- function(x) ifelse(length(x) == 0, 0, sqrt(var(x, na.rm=T)/sum(!is.na(x))))
ifNA <- function(x, other) ifelse(is.na(x), other, x)
twoColors <- c("#a1d99b","#2c7fb8")

loadRatings <- function(dataset, threshold, names, dir="") {
  allRatings <- NULL
  if (dir != "") dir <- paste0(dir, "/")
  for (name in names) {
    ratings <- read_csv(paste0("../../data/hint-rating/", dataset, "/ratings/", threshold, "/", dir, name, ".csv"))
    ratings$source <- name
    if (is.null(allRatings)) allRatings <- ratings
    else allRatings <- rbind(allRatings, ratings)
  }
  standard <- read_csv(paste0("../../data/hint-rating/", dataset, "/gold-standard.csv"))
  years <- ddply(standard, c("requestID"), summarize, year=head(year, 1))
  allRatings <- merge(allRatings, years)
  if ("validity" %in% names(allRatings)) allRatings$validity[is.na(allRatings$validity)] <- 0
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

writeSamples <- function() {
  # This will not work as expected with the new ratings output and directory
  # It could be fixed, but for now it's easier to just read from the file
  
  # Don't include both hint factories
  sampleRatings <- loadRatings("isnapF16-F17", c("SourceCheck", "CTD", "PQGram", "chf_with_past"), "sampling")
  samples <- selectHintsForManualRating(sampleRatings)
  
  write.csv(subset(samples, select=c(assignmentID, year, requestID, hintID, priority)), "C:/Users/Thomas/Desktop/samples.csv", row.names = F)
  write.csv(samples, "data/samples-full.csv", row.names = F)
  writeSQL(samples[samples$priority <= 25,], "C:/Users/Thomas/Desktop/samples75.sql")
  writeSQL(samples[samples$priority <= 50,], "C:/Users/Thomas/Desktop/samples150.sql")
  writeSQL(samples[samples$priority <= 84,], "C:/Users/Thomas/Desktop/samples252.sql")
}

getSamples <- function() {
  samples <- read_csv("data/samples-full.csv")
  
  manual <- read_csv("data/manual.csv")
  manual$consensus <- suppressWarnings(as.integer(manual$consensus))
  manualT1 <- read_csv("data/manual-thomas.csv")
  manualT2 <- read_csv("data/manual-rui.csv")
  manualT3 <- read_csv("data/manual-yihuan.csv")
  manual$t1 <- manualT1$validity
  manual$t2 <- manualT2$validity
  manual$t3 <- manualT3$validity
  manual <- manual[!is.na(manual$consensus),]
  
  # TODO: Loading sample ratings does not currently work
  verifyRatingsOld(manual, samples, sampleRatings)
  
  newRatingsOne <- loadRatings("isnapF16-F17", "OneTutor", c("SourceCheck_sampling", "CTD", "PQGram", "chf_with_past"))
  newRatingsMT <- loadRatings("isnapF16-F17", "MultipleTutors", c("SourceCheck_sampling", "CTD", "PQGram", "chf_with_past"))
  newRatingsConsensus <- loadRatings("isnapF16-F17", "Consensus", c("SourceCheck_sampling", "CTD", "PQGram", "chf_with_past"))
  # 0.83 0.83 0.83 (One tutor)
  verifyRatings(manual, samples, newRatingsOne)
  # 0.75 0.78 0.80 (Multiple tutors)
  verifyRatings(manual, samples, newRatingsMT)
  # 0.81 0.81 0.81 (Consensus)
  verifyRatings(manual, samples, newRatingsConsensus)
  
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
  withManual <- manual
  names(withManual)[names(withManual) == 'priority'] <- 'ratePriority'
  withManual <- merge(withManual[,c("hintID", "consensus", "ratePriority")], withSamples)
  printVerify("", withManual$consensus, withManual$type)
  
  columns <- c("year", "requestID", "hintID", "matchID", "type", "source")
  cat("Invalid hints rated valid:\n")
  print(withManual[withManual$consensus == 0 & withManual$type == "Full", columns])
  cat("Valid hints rated invalid:\n")
  print(withManual[withManual$consensus == 2 & withManual$type == "None", columns])
  cat("Valid hints rated partially valid:\n")
  print(withManual[withManual$consensus == 2 & withManual$type == "Partial", columns])
}

verifyRatingsOld <- function(manual, samples, ratings) {
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

findInterestingRequests <- function(algRequests, ratings) {
  byRequest <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                      PQGram=scorePartial[source=="PQGram"],
                      # chf_without_past=scorePartial[source=="chf_without_past"],
                      chf_with_past=scorePartial[source=="chf_with_past"],
                      CTD=scorePartial[source=="CTD"],
                      SourceCheck=scorePartial[source=="SourceCheck"],
                      ITAP=(if (sum(source=="ITAP") == 0) NA else scorePartial[source=="ITAP"]))
  cor(byRequest[byRequest$dataset=="isnap",5:8])
  cor(byRequest[byRequest$dataset=="itap",5:9])
  
  onlyOne <- function (x) if (length(x) == 1) x else NA
  secondBest <- function(x) sort(x)[length(x) - 1]
  scores <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                  maxScore = max(scorePartial),
                  difficulty = 1 - secondBest(scorePartial),
                  best = onlyOne(source[which(scorePartial==maxScore)]),
                  treeSize = mean(treeSize),
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
  
  goldStandardiSnap <- read_csv("../../data/hint-rating/isnapF16-F17/gold-standard.csv")
  goldStandardiSnap$dataset <- "isnap"
  goldStandardITAP <- read_csv("../../data/hint-rating/itapS16/gold-standard.csv")
  goldStandardITAP$dataset <- "itap"
  goldStandard <- rbind(goldStandardiSnap, goldStandardITAP)
  gsRequests <- ddply(goldStandard, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                      nHints = sum(MultipleTutors),
                      nHP = sum(priority < 3, na.rm=T))
  
  goldStandard$nMatches <- sapply(goldStandard$hintID, function(hintID) sum(algRatings$matchID==hintID, na.rm=T))
  goldStandard$onlyMatch <- sapply(goldStandard$hintID, function(hintID) onlyOne(algRatings$source[!is.na(algRatings$matchID) & algRatings$matchID==hintID]))
  
  scores <- merge(scores, gsRequests)
  ggplot(scores, aes(x=difficulty>0.75,y=nHints)) + geom_boxplot()
  ggplot(scores, aes(x=difficulty>0.75,y=nHints)) + geom_boxplot() + facet_wrap(~ dataset)
  ggplot(scores, aes(x=as.ordered(nHints),y=difficulty)) + geom_boxplot() + facet_wrap(~ dataset, scales = "free_x")
  ggplot(scoresiSnap, aes(x=as.ordered(nHP),y=difficulty)) + geom_boxplot()
  
  scoresiSnap <- scores[scores$dataset == "isnap",]
  wilcox.test(scoresiSnap$nHints[scoresiSnap$difficulty > 0.75], 
              scoresiSnap$nHints[scoresiSnap$difficulty <= 0.75])
  plot(jitter(scoresiSnap$nHints), jitter(scoresiSnap$difficulty))
  cor.test(scoresiSnap$nHints, scoresiSnap$difficulty, method="spearman")
  wilcox.test(scoresiSnap$nHP[scoresiSnap$difficulty > 0.75], 
              scoresiSnap$nHP[scoresiSnap$difficulty <= 0.75])
  plot(jitter(scoresiSnap$nHP), jitter(scoresiSnap$difficulty))
  cor.test(scoresiSnap$nHP, scoresiSnap$difficulty, method="spearman")
  
  scoresITAP <- scores[scores$dataset == "itap",]
  wilcox.test(scoresITAP$nHints[scoresITAP$difficulty > 0.75], 
              scoresITAP$nHints[scoresITAP$difficulty <= 0.75])
  plot(jitter(scoresITAP$nHints), jitter(scoresITAP$difficulty))
  cor.test(scoresITAP$nHints, scoresITAP$difficulty, method="spearman")
  
  algRatings <- ratings[ratings$source != "AllTutors" & ratings$source != "chf_without_past",]
  algRatings$matched <- algRatings$type == "Full"
  algRatings$nEdits <- algRatings$nInsertions + algRatings$nDeletions + algRatings$nRelabels
  algRatings$delOnly <- algRatings$nInsertions==0 & algRatings$nRelabels==0
  
  ddply(algRatings, c("dataset", "source"), summarize, 
        successDel=mean(matched[delOnly]), 
        successNotDel=mean(matched[!delOnly]),
        oddsRatio=successDel/successNotDel)
  
  # Non-deletions do _much_ better than delete-only hints
  table(algRatings$delOnly, algRatings$type, algRatings$dataset)
  # There's an issue here with the fact that hints are not at all independent
  # Could some algorithsm be better (e.g. with insertions), but also happen to have fewer deletions
  # Should really test within an algorithm, within a hint request
  fisher.test(algRatings$delOnly[algRatings$dataset=="isnap"], algRatings$matched[algRatings$dataset=="isnap"])
  fisher.test(algRatings$delOnly[algRatings$dataset=="itap"], algRatings$matched[algRatings$dataset=="itap"])
  ggplot(algRatings, aes(x=delOnly, y=normScore)) + geom_boxplot() + facet_grid(~dataset)
  median(algRatings$normScore[algRatings$dataset=="isnap" & algRatings$delOnly])
  
  # Larger hints (+2 edits) do better for iSnap and worse for ITAP
  nonDeletes <- algRatings[!algRatings$delOnly,]
  medEditsiSnap <- median(nonDeletes$nEdits[nonDeletes$dataset=="isnap"]) # 3
  medEditsITAP <- median(nonDeletes$nEdits[nonDeletes$dataset=="itap"]) # 3
  algRatings$moreEdits <- F
  algRatings$moreEdits[algRatings$nEdits > medEditsiSnap & algRatings$dataset=="isnap"] <- T
  algRatings$moreEdits[algRatings$nEdits > medEditsITAP & algRatings$dataset=="itap"] <- T
  table(nonDeletes$moreEdits, nonDeletes$type, nonDeletes$dataset)
  # Same problem here of non-independence
  fisher.test(nonDeletes$moreEdits[nonDeletes$dataset=="isnap"], nonDeletes$matched[nonDeletes$dataset=="isnap"])
  fisher.test(nonDeletes$moreEdits[nonDeletes$dataset=="itap"], nonDeletes$matched[nonDeletes$dataset=="itap"])
  # Looks like the ITAP trend is not just attributable to the ITAP algorithm's large, successful hints
  fisher.test(nonDeletes$moreEdits[nonDeletes$dataset=="itap" & nonDeletes$source != "ITAP"], 
              nonDeletes$matched[nonDeletes$dataset=="itap" & nonDeletes$source != "ITAP"])
  
  
  # Medium positive correlation between tree size and difficulty
  plot(jitter(scores$difficulty), jitter(scores$treeSize))
  cor.test(scores$difficulty, scores$treeSize, method="spearman")
  ggplot(scores, aes(x=difficulty > 0.75, y=treeSize)) + geom_boxplot() + facet_grid(~dataset)
  wilcox.test(scoresiSnap$treeSize[scoresiSnap$difficulty > 0.75], 
              scoresiSnap$treeSize[scoresiSnap$difficulty <= 0.75])
  wilcox.test(scoresITAP$treeSize[scoresITAP$difficulty > 0.75], 
              scoresITAP$treeSize[scoresITAP$difficulty <= 0.75])
  
  noMatch <- goldStandard[goldStandard$nMatches==0 & goldStandard$MultipleTutors,c("dataset", "year", "hintID")]
  noMatch <- noMatch[order(noMatch$dataset, noMatch$year, noMatch$hintID),]
  write.csv(noMatch, "data/noMatch.csv")
}

loadAllRatings <- function() {
  isnap <- loadRatings("isnapF16-F17", "MultipleTutors", c("SourceCheck", "CTD", "PQGram", "chf_with_past", "chf_without_past", "gross", "AllTutors"))
  isnap$dataset <- "isnap"
  itap <- loadRatings("itapS16", "MultipleTutors", c("SourceCheck", "CTD", "PQGram", "chf_with_past", "chf_without_past", "gross", "ITAP", "AllTutors"))
  itap$dataset <- "itap"
  ratings <- rbind(isnap, itap)
  ratings <- ratings[order(ratings$dataset, ratings$assignmentID, ratings$year, ratings$requestID, ratings$source, ratings$order),]
  ratings$scoreFull <- ratings$weightNorm * ifelse(ratings$type=="Full" & ratings$valid, 1, 0)
  ratings$scorePartial <- ratings$weightNorm * ifelse(ratings$type!="None" & ratings$valid, 1, 0)
  # TODO: Priority doesn't match with Java output, but we don't have it for non-consensus hints, so maybe not a priority
  ratings$priorityFull <- ifNA(4 - ratings$priority, 0) * ratings$scoreFull
  ratings$priorityPartial <- ifNA(4 - ratings$priority, 0) * ratings$scorePartial
  ratings$source <- factor(ratings$source, c("PQGram", "gross", "chf_without_past", "chf_with_past", "CTD", "SourceCheck", "ITAP", "AllTutors"))
  ratings$assignmentID <- factor(ratings$assignmentID, c("squiralHW", "guess1Lab", "helloWorld", "firstAndLast", "isPunctuation", "kthDigit", "oneToN"))
  ratings
}
  
loadRequests <- function(ratings) {
  requests <- ddply(ratings, c("dataset", "year", "source", "assignmentID", "requestID"), summarize, 
                    treeSize=mean(requestTreeSize),
                    scoreFull=sum(scoreFull), scorePartial=sum(scorePartial),
                    priorityFull=sum(priorityFull), priorityPartial=sum(priorityPartial))
  requests <- requests[requests$source != "chf_without_past",]
  requests
}

compare <- function() {
  ratings <- loadAllRatings()
  requests <- loadRequests(ratings)
  algRequests <-  requests[requests$source != "AllTutors",]
  
  assignments <- ddply(requests, c("dataset", "source", "assignmentID"), summarize, 
                       mScoreFull=mean(scoreFull), mScorePartial=mean(scorePartial),
                       seFull=se(scoreFull), sePartial=se(scorePartial),
                       mPriorityFull=mean(priorityFull), mPriorityPartial=mean(priorityPartial))
  
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
  
  plotComparison(assignments, "isnap")
  plotComparison(assignments, "itap")
  plotComparisonTogether(requests)
  
  plotComparisonStacked(assignments, "isnap")
  plotComparisonStacked(assignments, "itap")
  plotComparisonTogetherStacked(requests)
  
  # Getting slightly inconsistent results for these on different machines:
  
  kruskal.test(scoreFull ~ source, algRequests[algRequests$dataset=="isnap",])
  summary(aov(scoreFull ~ source + assignmentID + source * assignmentID, algRequests[algRequests$dataset=="isnap",]))
  
  allComps(requests, F, "isnap")
  allComps(requests, T, "isnap")
  
  kruskal.test(scoreFull ~ source, algRequests[algRequests$dataset=="itap",])
  summary(aov(scoreFull ~ source + assignmentID + source * assignmentID, algRequests[algRequests$dataset=="itap",]))
  
  allComps(requests, F, "itap")
  allComps(requests, T, "itap")
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
  isnap <- dataset == "isnap"
  
  fillLabs <- 
    if (isnap) c(`squiralHW` = "Squiral", `guess1Lab` = "GuessingGame") 
    else c(`helloWorld`="HelloWorld", `firstAndLast`="FirstAndLast", `isPunctuation`="IsPunctuation", `kthDigit`="KthDigit", `oneToN`="OneToN")
  title <- paste("QualityScore Ratings -", if (isnap) "iSnap" else "ITAP")
  textOffsetX <- if (isnap) 0.15 else 0.16
  yMax <- if (isnap) 1.1 else 1.25
  ylabs <- c("Tutors", "ITAP", "SourceCheck", "CTD", "CHF", "NSNLS", "Zimmerman")
  if (isnap) ylabs <- ylabs[ylabs != "ITAP"]
  
  assignments <- assignments[assignments$dataset == dataset,]
  p <- plotComparisonStackedDS(assignments, ylabs, fillLabs, title, yMax, textOffsetX)
  if (!isnap) p <- p + theme(legend.position = c(0.75, 0.15))
  p
}

plotComparisonStackedDS <- function(assignments, ylabs, fillLabs, title, yMax, textOffsetX) {
  assignments$mScorePartialPlus <- assignments$mScorePartial - assignments$mScoreFull
  assignments <- assignments[assignments$source != "chf_without_past",]
  melted <- melt(assignments[,c("dataset", "source", "assignmentID", "mScorePartialPlus", "mScoreFull")], id=c("dataset", "source", "assignmentID"))
  ggplot(melted,aes(x=source, y=value, fill=variable)) + geom_bar(stat="identity") +
    suppressWarnings(geom_text(aes(x=source, y=mScoreFull+mScorePartialPlus+textOffsetX, label = sprintf("%.02f (%.02f)", mScoreFull, mScoreFull+mScorePartialPlus), fill=NULL), data = assignments)) +
    scale_fill_manual(labels=c("Partial", "Full"), values=twoColors) + 
    scale_x_discrete(labels=rev(ylabs)) +
    scale_y_continuous(limits=c(0, yMax), breaks = c(0, 0.25, 0.5, 0.75, 1.0)) +
    labs(fill="Match", x="Algorithm", y="QualityScore", title=title) +
    theme_bw(base_size = 17) +
    theme(plot.title = element_text(hjust = 0.5)) +
    coord_flip() + facet_wrap(~assignmentID, labeller = as_labeller(fillLabs), ncol=2)
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
  niSnap <- length(unique(requests$requestID[requests$dataset=="isnap"]))
  nITAP <- length(unique(requests$requestID[requests$dataset=="itap"]))
  requests <- requests[requests$source != "chf_without_past",]
  together <- ddply(requests, c("dataset", "source"), summarize, mScorePartialPlus=mean(scorePartial)-mean(scoreFull), mScoreFull=mean(scoreFull))
  
  ggplot(melt(together, id=c("dataset", "source")),aes(x=source, y=value, fill=variable)) + geom_bar(stat="identity") +
    suppressWarnings(geom_text(aes(x=source, y=mScoreFull+mScorePartialPlus+0.15, label = sprintf("%.02f (%.02f)", mScoreFull, mScoreFull+mScorePartialPlus), fill=NULL), data = together)) +
    scale_fill_manual(labels=c("Partial", "Full"), values=twoColors) + 
    scale_x_discrete(labels=rev(c("Tutors", "ITAP", "SourceCheck", "CTD", "CHF", "NSNLS", "Zimmerman"))) +
    scale_y_continuous(limits=c(0, 1.15), breaks = c(0, 0.25, 0.5, 0.75, 1.0)) +
    labs(fill="Match", x="Algorithm", y="QualityScore", title="QualityScore Ratings") +
    theme_bw(base_size = 17) +
    theme(plot.title = element_text(hjust = 0.5)) +
    coord_flip() + facet_wrap(~dataset, labeller = as_labeller(c(`isnap` = paste0("iSnap (n=", niSnap, ")"), `itap` = paste0("ITAP (n=", nITAP, ")")))) 
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

allComps <- function(requests, partial, dataset) {
  fRequests <- requests[requests$dataset == dataset,]
  fRequests <- fRequests[fRequests$source != "chf_without_past",]
  fRequests$score <- if (partial) fRequests$scorePartial else fRequests$scoreFull
  best <- ddply(fRequests, "source", summarize, mScore=mean(score))
  best <- best[order(best$mScore),]
  for (i in 1:(nrow(best)-1)) {
    worse <- best$source[i]
    better <- best$source[i+1]
    left <- fRequests[fRequests$source==worse,]
    right <- fRequests[fRequests$source==better,]
    left <- left[order(left$requestID),"score"]
    right <- right[order(right$requestID),"score"]
    test <- suppressWarnings(wilcox.test(left, right, paired=T))
    print(sprintf("%s vs %s: $W = %s$; $p = %.03f$", worse, better, as.character(test$statistic), test$p.value))
  }
}

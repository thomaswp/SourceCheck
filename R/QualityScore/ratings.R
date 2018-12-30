
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
    ratings <- read_csv(paste0("../../QualityScore/data/", dataset, "/ratings/", threshold, "/", dir, name, ".csv"))
    ratings$source <- name
    if (is.null(allRatings)) allRatings <- ratings
    else allRatings <- rbind(allRatings, ratings)
  }
  standard <- read_csv(paste0("../../QualityScore/data/", dataset, "/gold-standard.csv"))
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

testSamplesAgreement <- function() {
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
  # 0.76 0.83 0.89 (One tutor)
  verifyRatings(manual, samples, newRatingsOne)
  # 0.70 0.78 0.85 (Multiple tutors)
  verifyRatings(manual, samples, newRatingsMT)
  # 0.74 0.81 0.88 (Consensus)
  verifyRatings(manual, samples, newRatingsConsensus)
  
  # alpha = 0.673
  kripp.alpha(t(manual[,c("t1", "t2", "t3")]), method="ordinal")
  # Perfect agreement on 166/252 (65.9%) of them
  mean(manual$t1 == manual$t2 & manual$t2 == manual$t3)
  # 0.69
  cohen.kappa(cbind(manual$t1, manual$t2))
  # 0.69
  cohen.kappa(cbind(manual$t2, manual$t3))
  # 0.65
  cohen.kappa(cbind(manual$t1, manual$t3))
  
  # 0.79 0.85 0.92
  cohen.kappa(cbind(manual$consensus, manual$t1))
  # 0.68 0.76 0.84
  cohen.kappa(cbind(manual$consensus, manual$t2))
  # 0.71 0.78 0.85
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
  # Normalize line endings because apparently this changed at some point
  ratings$diff <- gsub("\r\n", "\n", ratings$diff)
  samples$diff <- gsub("\r\n", "\n", samples$diff)
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

onlyOne <- function (x) if (length(x) == 1) x else NA
secondBest <- function(x) sort(x)[length(x) - 1]

getScores <- function(algRequests) {
  algScores <- ddply(algRequests, c("dataset", "source"), summarize, expectedScore=mean(scoreFull), sdScore=sd(scoreFull))
  algRequests <- merge(algRequests, algScores)
  algRequests$normScoreFull <- (algRequests$scoreFull - algRequests$expectedScore)
  
  scores <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                  maxScore = max(scorePartial),
                  # TODO: Reconcile that this is partial
                  difficulty = 1 - secondBest(scorePartial),
                  normDifficulty = -median(normScoreFull),
                  medScore = median(scorePartial),
                  best = onlyOne(source[which(scorePartial==maxScore)]),
                  treeSize = mean(treeSize),
                  n0=sum(scorePartial==0), n1=sum(scorePartial==1), 
                  n05=sum(scorePartial > 0.5), n01=sum(scorePartial > 0.1), n025=sum(scorePartial > 0.25))
  table(scores$dataset, scores$best)
  table(scores$dataset, scores$n0)
  table(scores$dataset, scores$n01)
  
  scores
}

findInterestingRequests <- function(algRequests, ratings) {
  byRequest <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                      PQGram=scorePartial[source=="PQGram"],
                      # chf_without_past=scorePartial[source=="chf_without_past"],
                      chf_with_past=scorePartial[source=="chf_with_past"],
                      gross=scorePartial[source=="gross"],
                      CTD=scorePartial[source=="CTD"],
                      SourceCheck=scorePartial[source=="SourceCheck"],
                      ITAP=(if (sum(source=="ITAP") == 0) NA else scorePartial[source=="ITAP"]))
  
  isnapCor <- cor(byRequest[byRequest$dataset=="isnap",5:9])
  KMO(isnapCor)
  icc(byRequest[byRequest$dataset=="isnap",5:9], type="agreement")
  isnapCor[isnapCor == 1] <- NA
  mean(isnapCor, na.rm=T)
  
  itapCor <- cor(byRequest[byRequest$dataset=="itap",5:10])
  KMO(itapCor)
  icc(byRequest[byRequest$dataset=="itap",5:10], type="agreement")
  itapCor[itapCor == 1] <- NA
  mean(itapCor, na.rm=T)
  
  scores <- getScores(algRequests)
  
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
 
investigateHypotheses <- function() { 
  algRatings <- ratings[ratings$source != "AllTutors" & ratings$source != "chf_without_past",]
  algRatings$matched <- algRatings$type == "Full"
  algRatings$matchedP <- algRatings$type != "None"
  algRatings$nEdits <- algRatings$nInsertions + algRatings$nDeletions + algRatings$nRelabels - algRatings$nValueInsertions
  algRatings$delOnly <- algRatings$nDeletions == algRatings$nEdits
  algRatings$delMostly <- algRatings$nDeletions > algRatings$nEdits / 2
  allSame <- function(x) if (length(unique(x)) == 1) head(x, 1) else NA
  matchedHints <- ddply(algRatings[algRatings$matched,], c("dataset", "matchID"), summarize, 
                        n=length(nEdits), delOnly=allSame(delOnly), nEdits=mean(nEdits))
  
  goldStandardiSnap <- read_csv("../../QualityScore/data/isnapF16-F17/analysis/gold-standard-analysis.csv")
  goldStandardiSnap$dataset <- "isnap"
  goldStandardITAP <- read_csv("../../QualityScore/data/itapS16/analysis/gold-standard-analysis.csv")
  goldStandardITAP$dataset <- "itap"
  goldStandard <- rbind(goldStandardiSnap, goldStandardITAP)
  goldStandard$nEdits <- goldStandard$nInsertions + goldStandard$nDeletions + goldStandard$nRelabels - goldStandard$nValueInsertions
  goldStandard$delOnly <- goldStandard$nDeletions == goldStandard$nEdits
  goldStandard$nMatches <- sapply(1:nrow(goldStandard), function(i) {
    length(unique(algRatings$source[
      !is.na(algRatings$matchID) & 
        algRatings$matchID==goldStandard$hintID[i] & 
        algRatings$year==goldStandard$year[i]]))
  })
  goldStandard$matched <- goldStandard$nMatches > 0
  goldStandard$pValue <- goldStandard$nValueInsertions / goldStandard$minEdits
  
  gsRequests <- ddply(goldStandard, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                      traceLength=mean(traceLength),
                      requestTreeSize=mean(requestTreeSize),
                      nHints = sum(MultipleTutors),
                      nHP = sum(priority < 3, na.rm=T),
                      # Min and median distance from the request to another correct solution (not the hint itself)
                      minEdits=mean(minEdits), medEdits=mean(medEdits),
                      minTED=mean(minAPTED), medTED=mean(medAPTED))
  
  scores <- getScores(algRequests)
  scores <- merge(scores, gsRequests)
  scoresiSnap <- scores[scores$dataset == "isnap",]
  scoresITAP <- scores[scores$dataset == "itap",]
  
  medHintCounts <- ddply(algRequests, c("dataset", "requestID"), summarize, medAlgHintCount=median(hintCount))
  scores <- merge(scores, medHintCounts)
  
  byRequestFullNorm <- ddply(algRequests, c("dataset", "assignmentID", "year", "requestID"), summarize, 
                         PQGram=scoreFull[source=="PQGram"],
                         gross=scoreFull[source=="gross"],
                         chf_with_past=scoreFull[source=="chf_with_past"],
                         CTD=scoreFull[source=="CTD"],
                         SourceCheck=scoreFull[source=="SourceCheck"],
                         ITAP=(if (sum(source=="ITAP") == 0) NA else scoreFull[source=="ITAP"]))
  (coriSnap <- cor(byRequestFullNorm[byRequestFullNorm$dataset=="isnap",5:9], method="spearman"))
  mean(coriSnap[coriSnap!=1])
  (corITAP <- cor(byRequestFullNorm[byRequestFullNorm$dataset=="itap",5:10], method="spearman"))
  mean(corITAP[corITAP!=1])
  
### Effect of hint requests
  
  # USED: Significant effect of requestID on partial qscore
  kruskal.test(scorePartial ~ as.factor(requestID), algRequests[algRequests$dataset=="isnap",])
  kruskal.test(scorePartial ~ as.factor(requestID), algRequests[algRequests$dataset=="itap",])
  
  ddply(scores, "dataset", summarize, md=mean(difficulty), medd=median(difficulty))

### Hint Request Relationships
  
  # Unsurprisingly, later snapshots get bigger
  dsSpear(scores, "requestTreeSize", "traceLength")
  # But they aren't necessarily harder for iSnap; they seem to be for ITAP
  # Not sure how meaningful traceLength is, though...
  dsSpear(scores, "difficulty", "traceLength")
  # It's possible the size and deviance are the same idea
  dsSpear(scores, "requestTreeSize", "medTED")
  
  psych::corr.test(scores[scores$dataset=="isnap",c("requestTreeSize", "medTED", "nHints", "traceLength")], method="spearman")
  psych::corr.test(scores[scores$dataset=="itap",c("requestTreeSize", "medTED", "nHints", "traceLength")], method="spearman")
  
  
  isnapScores <- scores[scores$dataset=="isnap",]
  fullModel <- lm(difficulty ~ assignmentID + requestTreeSize + medTED + nHints, data=isnapScores)
  emptyModel <- lm(difficulty ~ 1, data=isnapScores)
  stepAIC(emptyModel, direction="forward", scope=list(upper=fullModel))
  m1 <- lm(difficulty ~ requestTreeSize, data=isnapScores)
  m2 <- lm(difficulty ~ requestTreeSize + medTED, data=isnapScores)
  anova(emptyModel, m1, m2)
  summary(m2)
  shapiro.test(m2$residuals)
  
  itapScores <- scores[scores$dataset=="itap",]
  fullModel <- lm(difficulty ~ assignmentID + requestTreeSize + medTED + nHints, data=itapScores)
  emptyModel <- lm(difficulty ~ 1, data=itapScores)
  stepAIC(emptyModel, direction="forward", scope=list(upper=fullModel))
  m1 <- lm(difficulty ~ medTED, data=itapScores)
  m2 <- lm(difficulty ~ medTED + assignmentID, data=itapScores)
  anova(emptyModel, m1, m2)
  summary(m2)
  shapiro.test(m2$residuals)

### Per-algorithm calculations:
  
  algRequests <- merge(algRequests, gsRequests)
  ddply(algRequests, c("dataset", "source"), summarize, 
        csSize=cor(requestTreeSize, scorePartial, method="spearman"),
        csDiverge=cor(medTED, scorePartial, method="spearman"),
        csGSHints=cor(nHints, scorePartial, method="spearman"))
  
### Too Much Code
  
  # Positive correlation between tree size and difficulty
  dsSpear(scores, "requestTreeSize", "difficulty")
  
  # Some correaltion between hint request tree size and # also hints generated (esp. for ITAP)
  dsSpear(scores, "requestTreeSize", "medAlgHintCount")
  # But no correlation between the number of GS hints for a request and its tree size
  dsSpear(scores, "requestTreeSize", "nHints")
  
  
  # Most algorithms do have a positive correlation between hint count and the number of hints requested
  algHintCounts <- ddply(algRatings, c("dataset", "source", "requestID", "requestTreeSize"), summarize, hintCount=length(requestTreeSize))
  # But importantly, not the best ones
  ddply(algHintCounts, c("dataset", "source"), summarize, cor(requestTreeSize, hintCount, method="spearman"))
  
### Too Few Correct Hints
  
  ggplot(scores, aes(x=difficulty>0.75,y=nHints)) + geom_boxplot()
  ggplot(scores, aes(x=difficulty>0.75,y=nHints)) + geom_boxplot() + facet_wrap(~ dataset)
  ggplot(scores, aes(x=as.ordered(nHints),y=difficulty)) + geom_boxplot() + facet_wrap(~ dataset, scales = "free_x")
  ggplot(scores, aes(x=as.ordered(nHints),y=normDifficulty)) + geom_boxplot() + facet_wrap(~ dataset, scales = "free_x")
  # No significant correlation, though there might be for iSnap with a bit more data
  dsSpear(scores, "difficulty", "nHints")
  # And a significant correlation between median hint count (among algorithms) and tutor hint count
  dsSpear(merge(medHintCounts, scores[,c("dataset", "requestID", "nHints")]), "medHintCount", "nHints")
  
  
# Unfiltered Hints
  
  dsSpear(algRequests, "hintCount", "scoreFull")
  dsSpear(algRequests, "hintCount", "scorePartial")
  ddply(algRequests, c("dataset", "source"), summarize, 
        chs=cor(hintCount, scoreFull, method="spearman"),
        chsPartial=cor(hintCount, scorePartial, method="spearman"), 
        mhc=mean(hintCount))
  
### Deviant Code
  
  # Robust evidence that the median distance of a hint request to a known solution impact difficulty
  dsSpear(scores, "medTED", "difficulty")
  cor.test(scoresiSnap$medTED, scoresiSnap$difficulty, method="spearman")
  plot(jitter(scoresiSnap$medTED), jitter(scoresiSnap$difficulty))
  cor.test(scoresiSnap$medTED, scoresiSnap$normDifficulty, method="spearman")
  plot(jitter(scoresiSnap$medTED), jitter(scoresiSnap$normDifficulty))
  # Consistent for ITAP dataset as well
  cor.test(scoresITAP$medTED, scoresITAP$difficulty, method="spearman")
  plot(jitter(scoresITAP$medTED), jitter(scoresITAP$difficulty))
  cor.test(scoresITAP$medTED, scoresITAP$normDifficulty, method="spearman")
  plot(jitter(scoresITAP$medTED), jitter(scoresITAP$normDifficulty))
  
### Unhelpful Deletions
  
  # GS deletions were rare and ~3x less likely to be matched than non-deletions
  table(goldStandard$delOnly, goldStandard$matched, goldStandard$dataset)
  
  # About half of GS hints were matched by an algorithm on both datasets
  ddply(goldStandard, "dataset", summarize, mean(matched))
  # The median matched hint had 2 matching algorithms on both datasets
  ddply(goldStandard, "dataset", summarize, median(goldStandard$nMatches[goldStandard$nMatches>0]))
  # 7 hints were matched by 5/5 algorithms for isnap
  table(goldStandard$nMatches[goldStandard$dataset=="isnap"])
  # 8 hints were matched by 5/6 algorithms for itap
  table(goldStandard$nMatches[goldStandard$dataset=="itap"])
  
  # Non-deletions do _much_ better than delete-only hints
  table(algRatings$delOnly, algRatings$type, algRatings$dataset)
  # There's an issue here with the fact that hints are not at all independent
  # Could some algorithms be better (e.g. with insertions), but also happen to have fewer deletions
  # Should really test within an algorithm, within a hint request
  fisher.test(algRatings$delOnly[algRatings$dataset=="isnap"], algRatings$matchedP[algRatings$dataset=="isnap"])
  fisher.test(algRatings$delOnly[algRatings$dataset=="itap"], algRatings$matchedP[algRatings$dataset=="itap"])
  # For ITAP we actually see that the poor-performing algorithms have more successful deletes, perhaps because there are so few
  ddply(algRatings, c("dataset", "source"), summarize, 
        ndel=sum(delOnly),
        successDel=mean(matchedP[delOnly]), 
        successNotDel=mean(matchedP[!delOnly]),
        oddsRatio=successDel/successNotDel,
        fishersp=if (!is.na(oddsRatio)) fisher.test(delOnly, matchedP)$p.value else 0)
  # However, we see that only 2.2 and 4.1% of matching hints are deletions
  table(matchedHints$dataset, matchedHints$delOnly)
  ddply(matchedHints, "dataset", summarize, p=mean(delOnly))
  # Compared to 17.7 and 18.0 for general hints
  table(algRatings$dataset, algRatings$delOnly)
  ddply(algRatings, "dataset", summarize, p=mean(delOnly))
  
  
### Reading Student Intent
  
  # No apparent relationship between adding nodes with values and how many matched
  ddply(goldStandard, c("dataset", "matched"), summarize, n=length(pValue), mp=median(pValue))
  ddply(goldStandard, c("dataset"), summarize, cor(nMatches, pValue, method="spearman"))
  ggplot(goldStandard, aes(x=as.ordered(nMatches), y=pValue)) + geom_boxplot() + facet_grid(~dataset)
  
### Hint Granulariy 
  
  ddply(goldStandard, c("dataset", "matched"), summarize, n=length(nEdits), mEdits=median(nEdits))
  ggplot(goldStandard, aes(x=matched, y=nEdits)) + geom_boxplot() + facet_grid(~dataset)
  # Significantly fewer edits for matched GS hints in iSnap
  wilcox.test(goldStandard$nEdits[goldStandard$dataset=="isnap" & goldStandard$matched], 
              goldStandard$nEdits[goldStandard$dataset=="isnap" & !goldStandard$matched])
  # but not ITAP
  wilcox.test(goldStandard$nEdits[goldStandard$dataset=="itap" & goldStandard$matched], 
              goldStandard$nEdits[goldStandard$dataset=="itap" & !goldStandard$matched])
  # Similarly, significant negative correlation between GS hint size and how many algorithms matched it, for iSnap only
  dsSpear(goldStandard, "nMatches", "nEdits")
  
  # Larger hints do better for iSnap and worse for ITAP
  ddply(algRatings, c("dataset", "matched"), summarize, medEdits=median(nEdits))
  
  nonDeletes <- algRatings[!algRatings$delOnly,]
  medEditsiSnap <- median(nonDeletes$nEdits[nonDeletes$dataset=="isnap"]) # 2
  medEditsITAP <- median(nonDeletes$nEdits[nonDeletes$dataset=="itap"]) # 3
  nonDeletes$moreEdits <- F
  nonDeletes$moreEdits[nonDeletes$nEdits > medEditsiSnap & nonDeletes$dataset=="isnap"] <- T
  nonDeletes$moreEdits[nonDeletes$nEdits > medEditsITAP & nonDeletes$dataset=="itap"] <- T
  
  table(nonDeletes$moreEdits, nonDeletes$type, nonDeletes$dataset)
  # Same problem here of non-independence
  fisher.test(nonDeletes$moreEdits[nonDeletes$dataset=="isnap"], nonDeletes$matched[nonDeletes$dataset=="isnap"])
  fisher.test(nonDeletes$moreEdits[nonDeletes$dataset=="itap"], nonDeletes$matched[nonDeletes$dataset=="itap"])
  # Looks like the ITAP trend is not just attributable to the ITAP algorithm's large, successful hints
  fisher.test(nonDeletes$moreEdits[nonDeletes$dataset=="itap" & nonDeletes$source != "ITAP"], 
              nonDeletes$matched[nonDeletes$dataset=="itap" & nonDeletes$source != "ITAP"])
  
  noMatch <- goldStandard[goldStandard$nMatches==0 & goldStandard$MultipleTutors,c("dataset", "year", "hintID")]
  noMatch <- noMatch[order(noMatch$dataset, noMatch$year, noMatch$hintID),]
  write.csv(noMatch, "data/noMatch.csv")
  
  
  ## Tutor ratings
  
  allManual <- rbind(manualT1[,1:7], manualT2[,1:7], manualT3[,1:7])
  names(allManual)[1] <- "assignmentID"
  names(allManual)[5] <- "ratePriority"
  uniqueRatings <- newRatingsMT
  uniqueRatings$nEdits <- uniqueRatings$nInsertions + uniqueRatings$nDeletions + uniqueRatings$nRelabels - uniqueRatings$nValueInsertions
  uniqueRatings$delOnly <- uniqueRatings$nDeletions == uniqueRatings$nEdits
  uniqueRatings <- uniqueRatings[,c(intersect(names(allManual), names(uniqueRatings)), "delOnly")]
  uniqueRatings <- uniqueRatings[!duplicated(uniqueRatings),]
  allManual <- merge(allManual, uniqueRatings, all.x=T, all.y=F)
  allManual <- allManual[!is.na(allManual$reason),]
  table(allManual$reason[allManual$delOnly]) / sum(allManual$delOnly)
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

dsSpear <- function(dataset, c1, c2) {
  print(isnapTest <- cor.test(dataset[dataset$dataset == "isnap",][[c1]], dataset[dataset$dataset == "isnap",][[c2]], method = "spearman"))
  print(itapTest <- cor.test(dataset[dataset$dataset == "itap",][[c1]], dataset[dataset$dataset == "itap",][[c2]], method = "spearman"))
  cat(sprintf("\\isnap ($\\rho = %.03f$; $p = %.03f$) and \\itap ($\\rho = %.03f$; $p = %.03f$) datasets\n", 
          isnapTest$estimate, isnapTest$p.value,
          itapTest$estimate, itapTest$p.value))
}

loadRequests <- function(ratings) {
  requests <- ddply(ratings, c("dataset", "year", "source", "assignmentID", "requestID"), summarize, 
                    treeSize=mean(requestTreeSize),
                    hintCount=length(scoreFull),
                    countFull=sum(scoreFull>0), countPartial=sum(scorePartial>0),
                    scoreFull=sum(scoreFull), scorePartial=sum(scorePartial),
                    priorityFull=sum(priorityFull), priorityPartial=sum(priorityPartial)
                    )
  requests <- requests[requests$source != "chf_without_past",]
  requests
}

detailedStats <- function(requests, groupings) {
  ddply(requests, groupings, summarize,
        pAnyValid=mean(scoreFull>0), pAnyPartial=mean(scorePartial>0),
        meanValid=mean(countFull), meanPartial=mean(countPartial),
        medHints=median(hintCount), meanHints=mean(hintCount))
}

getStatsTable <- function(stats, rows, rowName) {
  statsTable <- sapply(rows, function(rowValue) {
    sapply(unique(stats$source), function(source) {
      row <- stats[stats$source == source & stats[,rowName] == rowValue,]
      # return (sprintf("%.02f (%.02f/%.02f)", row$pAnyValid, row$meanValid, row$meanHints))
      return (sprintf("%.02f (%.02f)", row$pAnyValid, row$pAnyPartial))
    })
  })
  statsTable <- t(statsTable)
  statsTable <- data.frame(statsTable)
  for (j in 1:ncol(statsTable)) statsTable[,j] <- as.character(statsTable[,j])
  colnames(statsTable) <- unique(stats$source)
  rownames(statsTable) <- rows
  #statsTable[c("ITAP"),1:2] <- NA
  statsTable
}

detailedTables <- function(requests) {
  stats <- detailedStats(requests, c("dataset", "source", "assignmentID"))
  stats <- stats[stats$source != "chf_without_past" & stats$source != "AllTutors",]
  
  statsTableAssignment <- getStatsTable(stats, unique(stats$assignmentID), "assignmentID")
  
  stats <- detailedStats(requests, c("dataset", "source"))
  stats <- stats[stats$source != "chf_without_past" & stats$source != "AllTutors",]
  
  statsTableDataset <- getStatsTable(stats, unique(stats$dataset), "dataset")
  write.csv(rbind(statsTableAssignment, statsTableDataset), "data/stats.csv")
  
  stats$mScoreFull <- stats$pAnyValid
  stats$mScorePartialPlus <- stats$pAnyPartial - stats$pAnyValid
  plotComparisonTogetherStacked1(stats, 1, 1)
  stats$mScoreFull <- stats$meanValid
  stats$mScorePartialPlus <- stats$meanPartial - stats$meanValid
  plotComparisonTogetherStacked1(stats, 1, 1, F)
}

compare <- function() {
  ratings <- loadAllRatings()
  requests <- loadRequests(ratings)
  
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
  
  algRequests <-  requests[requests$source != "AllTutors",]
  
  kruskal.test(scoreFull ~ source, algRequests[algRequests$dataset=="isnap",])
  summary(aov(scoreFull ~ source + assignmentID + source * assignmentID, algRequests[algRequests$dataset=="isnap",]))
  
  allComps(requests, F, "isnap")
  allComps(requests, T, "isnap")
  
  kruskal.test(scoreFull ~ source, algRequests[algRequests$dataset=="itap",])
  kruskal.test(scorePartial ~ source, algRequests[algRequests$dataset=="itap",])
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
    if (isnap) c(`squiralHW` = "Squiral (n=30)", `guess1Lab` = "GuessingGame (n=31)") 
    else c(`helloWorld`="HelloWorld (n=7)", `firstAndLast`="FirstAndLast n=(7)", `isPunctuation`="IsPunctuation (n=13)", `kthDigit`="KthDigit (n=14)", `oneToN`="OneToN (n=10)")
  title <- paste("QualityScore Ratings -", if (isnap) "iSnap" else "ITAP")
  textOffsetX <- if (isnap) 0.15 else 0.16
  yMax <- if (isnap) 1.1 else 1.25
  ylabs <- c("Tutors", "ITAP", "SourceCheck", "CTD", "CHF", "NSNLS", "TR-ER")
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
  requests <- requests[requests$source != "chf_without_past",]
  niSnap <- length(unique(requests$requestID[requests$dataset=="isnap"]))
  nITAP <- length(unique(requests$requestID[requests$dataset=="itap"]))
  together <- ddply(requests, c("dataset", "source"), summarize, mScorePartialPlus=mean(scorePartial)-mean(scoreFull), mScoreFull=mean(scoreFull))
  #print(together)
  plotComparisonTogetherStacked1(together, niSnap, nITAP)
}
  
plotComparisonTogetherStacked1 <- function(together, niSnap, nITAP, lockXAxis=T) {
  together <- together[c("dataset", "source", "mScorePartialPlus", "mScoreFull")]
  #return (together)
  plot <- ggplot(melt(together, id=c("dataset", "source")),aes(x=source, y=value, fill=variable)) + geom_bar(stat="identity") +
    suppressWarnings(geom_text(aes(x=source, y=mScoreFull+mScorePartialPlus+0.15, label = sprintf("%.02f (%.02f)", mScoreFull, mScoreFull+mScorePartialPlus), fill=NULL), data = together)) +
    scale_fill_manual(labels=c("Partial", "Full"), values=twoColors) + 
    scale_x_discrete(labels=rev(c("Tutors", "ITAP", "SourceCheck", "CTD", "CHF", "NSNLS", "TR-ER"))) +
    labs(fill="Match", x="Algorithm", y="QualityScore", title="QualityScore Ratings") +
    theme_bw(base_size = 17) +
    theme(plot.title = element_text(hjust = 0.5)) +
    coord_flip() + facet_wrap(~dataset, labeller = as_labeller(c(`isnap` = paste0("iSnap (n=", niSnap, ")"), `itap` = paste0("ITAP (n=", nITAP, ")")))) 
  if (lockXAxis) {
    plot <- plot + scale_y_continuous(limits=c(0, 1.15), breaks = c(0, 0.25, 0.5, 0.75, 1.0))
  }
  return (plot)
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
  sources <- unique(fRequests$source)
  comps <- NULL
  for (i in 2:length(sources)) {
    for (j in 1:(i-1)) {
      a <- sources[i]
      b <- sources[j]
      left <- fRequests[fRequests$source==a,]
      right <- fRequests[fRequests$source==b,]
      left <- left[order(left$requestID),"score"]
      right <- right[order(right$requestID),"score"]
      test <- suppressWarnings(wilcox.test(left, right, paired=T))
      comps <- rbind(comps, data.frame(left=a, right=b, mLeft=mean(left), mRight=mean(right), p=test$p.value, stat=test$statistic))
    }
  }
  comps <- comps[order(comps$p),]
  comps$thresh <- sapply(1:nrow(comps), function(i) i * 0.05 / nrow(comps))
  comps$sig <- comps$p < comps$thresh
  comps
}

allCompsOrdered <- function(requests, partial, dataset) {
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

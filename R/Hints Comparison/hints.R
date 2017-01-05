source('util.R')

csv <- function(dir, file) {
  read.csv(paste(dir, file, sep=""))
}

loadData <- function() {
  rm(list=ls())
  
  dir2015 <- "../../data/csc200/fall2015/analysis/"
  dir2016 <- "../../data/csc200/fall2016/analysis/"
  
  projs2015 <- csv(dir2015, "attempts.csv")
  projs2016 <- csv(dir2016, "attempts.csv")
  
  projs <<- rbind(projs2015, projs2016)
  projs$pFollowed <<- projs$followed / projs$hints
  projs$follow2 <<- projs$followed >= 2;
  projs$hint3 <<- projs$hints >= 3;
  projs$percIdle <<- projs$idle / projs$total
  projs$pass <<- projs$grade >= 0.8
  
  projs$logs <<- projs$logs == "true"
  logs <<- projs[projs$logs,]
  
  totals <<- ddply(projs[,-3], c("dataset", "assignment"), colwise(safeMean))
  totalLogs <<- ddply(logs[,-3], c("dataset", "assignment"), colwise(safeMean))
  
  grades <<- ddply(projs[!is.na(projs$grade),-3], c("dataset", "assignment"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), percPass=mean(pass), n=length(grade))
  grades$completed <<- grades$n / ifelse(grades$dataset=="Fall2016", 68, 82)
  gradesByHints <<- ddply(projs[,-3], c("dataset", "assignment", "hint3"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), percPass=mean(pass), n=length(grade))
  gradesByFollowed <<- ddply(projs[,-3], c("dataset", "assignment", "follow2"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), percPass=mean(pass), n=length(grade))
  
  timeByYear <<- ddply(projs[,-3], c("dataset", "assignment"), summarize, meanActive=safeMean(active), meanTotal=safeMean(total), meanPercIdle=safeMean(percIdle), n=length(grade))
  timeByHints <<- ddply(projs[,-3], c("dataset", "assignment", "hint3"), summarize, meanActive=safeMean(active), meanTotal=safeMean(total), meanPercIdle=safeMean(percIdle), n=length(grade))
  timeByFollowed <<- ddply(projs[,-3], c("dataset", "assignment", "follow2"), summarize, meanActive=safeMean(active), meanTotal=safeMean(total), meanPercIdle=safeMean(percIdle), n=length(grade))
  # projs <<- projs[projs$hints < 60,]
  # totalsNO <<- ddply(projs[,-1], c(), colwise(sum))
  # projs$pFollowed <<- ifelse(projs$hints == 0, 0, projs$followed / projs$hints)
  
  hints <<- csv(dir2016, "hints.csv") 
  hints <<- hints[hints$attemptID %in% projs$id,]
  hints$duration[hints$duration < 0] <<- NA
  hints$duration[hints$duration > 500] <<- NA
  hints$pause[hints$pause < 0] <<- NA
  hints$pause[hints$pause > 500] <<- NA
  
  users <<- buildUsers()
  dedup <<- buildDedup()
  chances <<- buildChances()
}

hintStats <- function() {
  # 38.8% of hints displayed were followed
  mean(hints$followed)
  # 37.4% of hints were duplicates
  mean(hints$duplicate)
  # 14.5% of hints were exact repeats of the last hint
  mean(hints$repeat.)
  # 47.3% of hints were followed eventually
  mean(ddply(hints, c("attemptID", "hash"), summarize, f=any(followed))$f)

  ggplot(hints, aes(duration, color=followed)) + geom_density()
  ggplot(hints, aes(pause, color=followed)) + geom_density()
  # followed hints had non-significantly shorter view time and pause before action
  condCompare(hints$duration, hints$followed)
  condCompare(hints$pause, hints$followed)
}

testTimes <- function(assignment) {
  assignment <- "guess2HW" # TODO, remove
  ps <- projs[projs$logs=="true" & projs$assignment == assignment,]
  g15 <- ps[ps$dataset == "Fall2015",]
  g16 <- ps[ps$dataset == "Fall2016",]

  # Not-quite-significantly more total time on GG3
  compareStats(g15$total, g16$total)
  # Significantly more time on GG3
  compareStats(g15$active, g16$active)
  # Significantly less idle perc on GG1 and not-quite-significantly less on GG2
  compareStats(g15$percIdle, g16$percIdle)
}

testGrades <- function(assignment) {
  assignment <- "squiralHW" # TODO, remove
  ps <- projs[!is.na(projs$grade) & projs$assignment == assignment,]
  g15 <- ps[ps$dataset == "Fall2015",]
  g16 <- ps[ps$dataset == "Fall2016",]
  g16H <- g16[g16$hint3,]
  g16F <- g16[g16$follow2,]
  
  # Not-quite-significant for guess2HW
  compareStats(g15$grade, g16$grade)
  # Not significant  
  compareStats(g16$grade, g16H$grade)
  # Not significant
  compareStats(g16$grade, g16F$grade)
}

plotFollowedGrades <- function() {
  counts <- ddply(projs2016, c("dataset", "followed", "grade"), summarize, n=length(hints))
  counts <- counts[!is.na(counts$grade),]
  ggplot(counts, aes(followed, grade)) + geom_point(aes(size=sqrt(n)))
}

plotHintsGrades <- function() {
  counts <- ddply(projs2016, c("dataset", "hints", "grade"), summarize, n=length(hints))
  counts <- counts[!is.na(counts$grade),]
  ggplot(counts, aes(hints, grade)) + geom_point(aes(size=sqrt(n)))
}

buildUsers <- function() {
  users <- ddply(hints, c("assignment", "attemptID"), summarize, hints=length(duplicate), unq=sum(!duplicate), unqF=length(unique(hash[followed])), firstF=followed[[1]])
  users$percF <- users$unqF / users$unq
  users$grade <- sapply(1:nrow(users), function(i) projs[as.character(projs$assignment) == users[i,]$assignment & as.character(projs$id) == users[i,]$attemptID,]$grade)
  
  # Significant (but not as strong) correlation between unique hints requested and percent followed
  cor.test(users$unq, users$percF)
  
  # No significant correlations between grades and anything
  # but remember grades are uniformly high
  cor.test(users$percF, users$grade)
  cor.test(users$unqF, users$grade)
  cor.test(users$unq, users$grade)
  table(users$grade)
  
  # 11.8% of users who requested 1 unique hint followed it. Wow.
  mean(users$unqF[users$unq == 1] > 0)
  # 36.3% of users who requested 2 unique hints followed at least 1
  mean(users$unqF[users$unq == 2] > 0)
  # 9.1% followed both
  mean(users$unqF[users$unq == 2] > 1)
  # 96.2% of other users followed at least 1 hint
  mean(users$unqF[users$unq > 3] > 0)
  # 88.9% followed at least 2
  mean(users$unqF[users$unq > 3] > 1)
  # 51.9% followed at least half
  mean(users$percF[users$unq > 3] >= 0.5)
  
  # Only 18.2% of users who followed 2 hints followed their first one
  mean(users$firstF[users$unq == 2])
  # Only 1/3 of users who followed 1 of 2 hints followed their first one
  mean(users$firstF[users$unq == 2 & users$unqF == 1])
  
  # Followed numbers seems to vary quite a bit by assignmnet
  # 6+ followed ranges from 9-38%
  # 9+ followed ranges from 0-31%
  # most common is to follow 0
  # Sweet spot (maybe..?) of 2-5 hints was relatively rare
  ddply(users[users$unq>0,], c("assignment"), summarize, f0=mean(unqF==0), f1=mean(unqF==1), f2=mean(unqF==2), f35=mean(3 <= unqF & unqF <= 5), f68=mean(6 <= unqF & unqF <= 8), f9p=mean(9 <= unqF) )
  
  return (users)
}

# RQ: Do unfollowed hints cause students to stop asking for them?
# Not exactly, but followed hints definitely indicate students will keep asking

buildDedup <- function() {
  dedup <- ddply(hints, c("assignment", "attemptID", "hash"), summarize, type=first(type), startEdit=first(editPerc), endEdit=tail(editPerc, n=1), 
                 count=length(followed), anyF=any(followed), indexF=tail(c(NA, which(followed)), n=1), followEdit=editPerc[indexF], delete=all(delete),
                 followRowID=ifelse(!is.na(indexF), rowID[indexF], NA), rowID=rowID[1])
  
  dedup$edit <- ifNA(dedup$followEdit, dedup$startEdit)
  dedup <- dedup[order(dedup$assignment, dedup$attemptID, dedup$edit),]
  
  dedup$nth <- sapply(1:nrow(dedup), function(i) {
    row <- dedup[i,]
    user <- dedup[dedup$assignment == row$assignment & dedup$attemptID == row$attemptID,]
    nth <- match(row$hash, user$hash)
  })
  
  dedup$n <- sapply(1:nrow(dedup), function(i) {
    row <- dedup[i,]
    user <- dedup[dedup$assignment == row$assignment & dedup$attemptID == row$attemptID,]
    m <- nrow(user)
  })
  
  return (dedup)
}

library(rpart)
firstTree <- function() {
  # Only look at first hints before 75% of edits
  firsts <- dedup[dedup$nth==1 & dedup$edit < 0.75,]
  firsts$cont <- firsts$n > 1
  
  # 93.3% of those who follow their first hint ask for another
  mean(firsts$cont[firsts$anyF])
  # 68.6% for those who don't
  mean(firsts$cont[!firsts$anyF])
  
  # Median 12 unique hints for those who follow their first
  median(firsts$n[firsts$anyF])
  # Median 2 for those who don't
  median(firsts$n[!firsts$anyF])
  
  tree <- rpart(cont ~ type + delete + anyF + assignment, data=firsts)
}

buildChances <- function() {
  chances <- ddply(dedup, c("assignment", "attemptID"), summarize, unF=ifNA(first(nth[anyF]) - 1, length(nth)), 
                   everF=any(anyF), nFollow=sum(anyF), nHints=length(nth), firstHint=rowID[1], secondHint=rowID[2],
                   firstFollow=anyF[1], secondFollow=anyF[2])
  # For most students who don't follow a hint, 15 (65.2%) only tried one bad hint, 7 tried 2 and 1 tried more (5).
  # So it looks like for 2/3 of students (who will give up), we get one shot at a hint before they give up
  table(chances$unF, chances$everF)
  
  # How many hints we know for sure each student was willing to try before giving up
  chances$didntGiveUpBy <- ifelse(chances$everF, chances$unF + 1, chances$unF)
  # 24/55=43.5% were willing to try 2+, 12/55=21.8% were willing to try 3+ hints
  # Remember: students who get a good hint may have been willing to wait longer for it
  table(chances$didntGiveUpBy)
  
  # So, what are these bad hints?
  sadUsers <- chances[chances$unF == 1 & !chances$everF,]$attemptID
  sadHints <- dedup$rowID[dedup$attemptID %in% sadUsers & dedup$nth==1]
  
  chances <- labelChances(chances, 3)
  chances
}

labelChances <- function(chances, n) {
  chances$label <- 0
  for (assignment in unique(chances$assignment)) {
    labels <- getLabels(chances[chances$assignment==assignment,]$nHints, n)
    chances[chances$assignment==assignment,]$label <- labels
  }
  return (chances)
}

getLabels <- function(x, n) {
  qs <- quantile(x, seq(0, 1, 1 / n))
  # print(qs)
  labels <- rep(1, length(x))
  for (i in 2:n) {
    labels[x > qs[i]] <- i
  }
  return (labels)
}

buildHintHashes <- function() {
  hintHashes <- ddply(dedup, c("hash"), summarize, f=sum(anyF), u=sum(!anyF), n=length(anyF))
  
  # Rarer hints are less likely to be followed
  table(hintHashes$n, hintHashes$f==hintHashes$n)
  table(hintHashes$n, hintHashes$u==hintHashes$n)
  table(hintHashes$n, hintHashes$f/hintHashes$n)
  # 49% of dedup'd hints are followed
  mean(dedup$anyF)
  # but only 36% of unique hints are followed by 50%+ of takers
  mean(hintHashes$f>hintHashes$n/2)
  # and the average hint is followed by 38% of takers
  mean(hintHashes$f/hintHashes$n)
  
  commonHashes <- hintHashes$hash[hintHashes$n > 2]
  goodHashes <- hintHashes$hash[hintHashes$f >= 2]
  
  hintHashes
}

loadHintsFile <- function(path) {
  ratings <- read.csv(path)
  ratings$timing <- ordered(ratings$timing, levels=c("Start", "Early", "Mid", "Late"))
  ratings$score <- ratings$relevant + ratings$correct + ratings$interp
  ratings$zInsight <- ifNA(ratings$insight, 1)
  ratings
}

loadRatedHints <- function() {
  ratings <- rbind(loadHintsFile("data/firstHints.csv"), loadHintsFile("data/secondHints.csv"))
  firstHints <- ratings[ratings$id %in% chances$firstHint,]
  names(firstHints) <- sapply(names(firstHints), function(name) paste(name, "1", sep="_"))
  secondHints <- ratings[ratings$id %in% chances$secondHint,]
  names(secondHints) <- sapply(names(secondHints), function(name) paste(name, "2", sep="_"))
  ratedHints <- merge(chances, firstHints, by.x="firstHint", by.y="id_1", all=T)
  ratedHints <- merge(ratedHints, secondHints, by.x="secondHint", by.y="id_2", all=T)
  ratedHints
}

library(vcd)
library(Exact)
testRatedHints <- function() {
  ratedHints <- loadRatedHints()
  
  # Followed hints are rated significantly higher for first and second  
  condCompare(ratedHints$score_1, ratedHints$firstFollow)
  condCompare(ratedHints$score_2, ratedHints$secondFollow)
  
  # Dependence between following first and second hint are significant
  exact.test(table(ratedHints$firstFollow, ratedHints$secondFollow))
  # 4.8x as likely: 8/13 vs 6/25
  table(ratedHints$firstFollow, ratedHints$secondFollow)

  # No correlation between first hint score and second hint following  
  cor.test(ratedHints$score_1, as.numeric(ratedHints$secondFollow))
  
  # Students who receive a 3-correct first hint are not-quite-significantly less likely than those who 
  # receive a 1-correct first hint to have a label of 1 (few hints)
  mosaic(table(ratedHints$label, ratedHints$correct_1))
  firstNot2 <- ratedHints[ratedHints$correct_1 != 2,]
  exact.test(table(firstNot2$correct_1, firstNot2$label == 1))
  # Very expensive version, possibly more powerful
  # exact.test(table(firstNot2$label == 1, firstNot2$correct_1), alternative="two.sided", method="Boschloo", model="multinomial")
  # They are also significantly more likely to follow 2+ hints (but be careful, as this could just be because they're more likely to follow _this_ hint)
  exact.test(table(firstNot2$correct_1, firstNot2$nFollow > 1))
  firstNot2$nFollowLater = firstNot2$nFollow - firstNot2$firstFollow
  # Indeed, without the first hint, it trends but isn't significant
  exact.test(table(firstNot2$correct_1, firstNot2$nFollowLater > 0))

  secondHints <- ratedHints[!is.na(ratedHints$secondHint),]
  
  # For both hints, label correlates to each 
  fhs <- ddply(ratedHints, c("label"), summarize, n=length(timing_1), mT = mean(as.numeric(timing_1)), mRel=mean(relevant_1), mCorrect=mean(correct_1), mInterp=mean(interp_1), mInsight=safeMean(zInsight_1), mScore=mean(score_1))
  shs <- ddply(secondHints, c("label"), summarize, n=length(timing_2), mT = mean(as.numeric(timing_2)), mRel=mean(relevant_2), mCorrect=mean(correct_2), mInterp=mean(interp_2), mInsight=safeMean(zInsight_2), mScore=mean(score_2))
  
  # First hint score is a marginally significant predicor of label
  condCompare(ratedHints$score_1, ratedHints$label == 1)
  cor.test(ratedHints$score_1, ratedHints$label, method="spearman")
  
  # Second label and hint score are correlated, and label marginally singificantly predicts score
  condCompare(ratedHints$score_2, ratedHints$label == 1)
  condCompare(ratedHints$score_2, ratedHints$label == 3)
  cor.test(ratedHints$score_2, ratedHints$label, method="spearman")
  cor.test(ratedHints$score_2, ratedHints$nHints, method="spearman")
  # Same with relevance
  condCompare(ratedHints$relevant_2, ratedHints$label == 1)
  cor.test(ratedHints$relevant_2, ratedHints$label, method="spearman")
  cor.test(ratedHints$relevant_2, ratedHints$nHints, method="spearman")
  
  
}




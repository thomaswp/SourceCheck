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
  
  grades <<- ddply(projs[!is.na(projs$grade),-3], c("dataset", "assignment"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), percPass=mean(pass), meanGradePC=mean(gradePC), n=length(grade))
  grades$completed <<- grades$n / ifelse(grades$dataset=="Fall2016", 68, 82)
  gradesByHints <<- ddply(projs[,-3], c("dataset", "assignment", "hint3"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), percPass=mean(pass), meanGradePC=mean(gradePC), n=length(grade))
  gradesByFollowed <<- ddply(projs[,-3], c("dataset", "assignment", "follow2"), summarize, meanGrade=mean(grade), sdGrade=sd(grade), percPass=mean(pass), meanGradePC=mean(gradePC), n=length(grade))
  
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
  # Estimate of time spent considering the hint is the min of
  # time spent with dialog open, time before next action and 60s
  hints$focusTime <<- pmin(ifNA(pmin(hints$duration, hints$pause), 60), 60)
  
  dedup <<- buildDedup()
  users <<- buildUsers()
  allUsers <<- buildAllUsers()
  chances <<- buildChances()
}

buildProjs2016 <- function() {
  projs2016 <- allUsers[allUsers$dataset == "Fall2016",]
  
  grades <- ddply(projs2016, c("assignment"), summarize, g=mean(grade))
  projs2016$perf <- sapply(1:nrow(projs2016), function(i) {
    assignment <- projs2016$assignment[i]
    mg <- grades$g[grades$assignment==assignment]
    sign(projs2016$grade[i] - mg)
  })
  # So far partial credit grades seem to trend the same as grades
  # grades <- ddply(projs2016, c("assignment"), summarize, g=mean(gradePC))
  # projs2016$perfPC <- sapply(1:nrow(projs2016), function(i) {
  #   assignment <- projs2016$assignment[i]
  #   mg <- grades$g[grades$assignment==assignment]
  #   sign(projs2016$gradePC[i] - mg)
  # })
  projs2016
}

library(scales)
testProjs2016 <- function() {
  projs2016 <- buildProjs2016()
  hws <- projs2016[projs2016$assignment=="guess2HW" | projs2016$assignment=="squiralHW",]
  hws$assignment <- ordered(hws$assignment, c("squiralHW", "guess2HW"))
  hws$prettyAssignment <- ordered(ifelse(hws$assignment=="squiralHW", "Squiral", "Guessing Game 2"), c("Squiral", "Guessing Game 2"))
  hws$unq[is.na(hws$unq)] <- 0
  hws$unqF[is.na(hws$unqF)] <- 0
  hws$hint1 <- hws$unq > 0
  hws$hint2 <- hws$unq >= 2
  hws$hint3 <- hws$unq >= 3
  hws$follow1 <- hws$unqF > 0
  hws$h3f1 <- hws$hint3 & hws$follow1
  hws$cat <- ordered(ifelse(hws$hint1, ifelse(hws$follow1, "F1", "H1"), "H0"), c("H0", "H1", "F1"))
  hws$cat <- ordered(ifelse(hws$hint1, ifelse(hws$hint3, "H3", "H1"), "H0"), c("H0", "H1", "H3"))
  ddply(hws, c("assignment"), summarize, n=length(perf), pOver=mean(perf==1))
  ddply(hws, c("assignment", "hint1"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  # USED IN PAPER
  ddply(hws, c("assignment", "follow1"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  ddply(hws, c("assignment", "hint3"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  ddply(hws, c("assignment", "h3f1"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  ddply(hws, c("assignment", "cat"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  ddply(hws[hws$hint1,], c("assignment", "follow1"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  
  hws$nHints <- ifelse(hws$unq == 0, NA, hws$unq)
  hws <- labelChances(hws, 3)
  hws$labelComb <- pmin(hws$label, 2)
  hws$labelHigh <- hws$label > 1
  ddply(hws, c("assignment", "labelComb"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  ddply(hws, c("assignment", "labelHigh"), summarize, n=length(perf), pOver=mean(perf==1), pPass=mean(grade >= 0.8), mGrade=mean(grade), sdGrade=sd(grade))
  
  h1 <- hws
  h1$label <- "hint1"
  h1$val <- h1$hint1
  f1 <- hws
  f1$label <- "hint3"
  f1$val <- h1$hint3
  data <- rbind(h1, f1)
  data$label <- ordered(data$label, c("hint1", "hint3"))
  ggplot(data, aes(x=val, y=grade)) + geom_violin() + geom_boxplot(width=0.15, fill="#eeeeee") + stat_summary(fun.y="mean", geom="point", color="red") + facet_grid(assignment ~ label)
  
  hws$usage <- ordered(ifelse(hws$unq == 0, "none", ifelse(hws$unq < 3, "low", "med")), c("none", "low", "med"))
  ggplot(hws, aes(x=usage, y=grade)) + geom_violin() + geom_boxplot(width=0.15, fill="#eeeeee") + stat_summary(fun.y="mean", geom="point", color="red") + facet_grid(. ~ assignment)
  ggplot(hws, aes(x=cat, y=grade)) + geom_violin() + geom_boxplot(width=0.15, fill="#eeeeee") + stat_summary(fun.y="mean", geom="point", color="red") + facet_grid(. ~ assignment)
  ggplot(hws, aes(x=hint3, y=grade)) + geom_violin() + geom_boxplot(width=0.15, fill="#eeeeee") + stat_summary(fun.y="mean", geom="point", color="red") + facet_grid(. ~ assignment)
  # USED IN THE PAPER
  ggplot(hws, aes(x=follow1, y=grade)) + 
    geom_violin() + geom_boxplot(width=0.15, fill="#eeeeee") + 
    stat_summary(fun.y="mean", geom="point", color="red") + 
    facet_grid(. ~ prettyAssignment) +
    theme_bw() +
    scale_y_continuous(labels=percent, name="Grade (%)") +
    scale_x_discrete(labels=c("False (F0)", "True (F1)"), name="One Hint Followed")
  
  ggplot(hws[hws$hint1,], aes(x=follow1, y=grade)) + geom_violin() + geom_boxplot(width=0.15, fill="#eeeeee") + stat_summary(fun.y="mean", geom="point", color="red") + facet_grid(assignment ~ .)
  
  ggplot(hws) + geom_boxplot(aes(x=follow1, y=grade)) + facet_grid(. ~ assignment)
  ggplot(hws) + geom_density(aes(x=grade, color=hint1)) + facet_grid(. ~ assignment)
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
  users <- buildAllUsers()
  users[!is.na(users$unq),]
}

buildAllUsers <- function() {
  users <- ddply(dedup, c("assignment", "attemptID"), summarize, unq=length(anyF), unqF=sum(anyF), firstF=anyF[[1]])
  users <- merge(users, projs[,c("dataset", "id", "assignment", "active", "total", "grade")], by.x=c("attemptID", "assignment"), by.y=c("id", "assignment"), all=T)
  users$percF <- users$unqF / users$unq
  users
}

testUsers <- function() {
  # Significant correlation between hints, and even more so unique hints requested and percent followed
  cor.test(users$unq, users$percF, method="spearman")
  
  # 12.5% of users who requested 1 unique hint followed it. Wow.
  mean(users$unqF[users$unq == 1] > 0)
  # 36.3% of users who requested 2 unique hints followed at least 1
  mean(users$unqF[users$unq == 2] > 0)
  # 9.1% followed both
  mean(users$unqF[users$unq == 2] > 1)
  # 96.2% of other users followed at least 1 hint
  mean(users$unqF[users$unq > 3] > 0)
  # 88.9% followed at least 2
  mean(users$unqF[users$unq > 3] > 1)
  # 59.3% followed at least half
  mean(users$percF[users$unq > 3] >= 0.5)
  
  # Only 18.2% of users who followed exactly 2 hints followed their first one
  mean(users$firstF[users$unq == 2])
  # Only 1/3 of users who followed 1 of 2 hints followed their first one
  mean(users$firstF[users$unq == 2 & users$unqF == 1])
  
  # Followed numbers seems to vary quite a bit by assignmnet
  # 6+ followed ranges from 9-38%
  # 9+ followed ranges from 0-31%
  # most common is to follow 0
  # Sweet spot (maybe..?) of 2-5 hints was relatively rare
  ddply(users[users$unq>0,], c("assignment"), summarize, f0=mean(unqF==0), f1=mean(unqF==1), f2=mean(unqF==2), f35=mean(3 <= unqF & unqF <= 5), f68=mean(6 <= unqF & unqF <= 8), f9p=mean(9 <= unqF) )
  
  # Hints requested by assignment in bins 1, 2, 3-12, 13+
  hMed <- median(users$unq[users$unq>2])
  ddply(users, c("assignment"), n=length(unq), summarize, h1=mean(unq==1), h2=mean(unq==2), h12=mean(unq>2 & unq<hMed), hm=mean(unq>=hMed), max=max(unq))
  ddply(users, c("assignment"), n=length(unq), summarize, h1=sum(unq==1), h2=sum(unq==2), h13=sum(unq>2 & unq<hMed), hm=sum(unq>=hMed), max=max(unq))
  ggplot(users) + geom_density(aes(x=hintsPerMinute, color=assignment))
  ggplot(users) + geom_density(aes(x=hintsPerMinute))
  ggplot(users) + geom_histogram(aes(x=hintsPerMinute))
  ggplot(users) + geom_boxplot(aes(y=hintsPerMinute, x=assignment))
  
  return (users)
}

plotHists <- function(x, y) {
  library(gridExtra)
  hist_top <- ggplot()+geom_histogram(aes(x))
  empty <- ggplot()+geom_point(aes(1,1), colour="white")+
    theme(axis.ticks=element_blank(), 
          panel.background=element_blank(), 
          axis.text.x=element_blank(), axis.text.y=element_blank(),           
          axis.title.x=element_blank(), axis.title.y=element_blank())
  
  scatter <- ggplot(NULL, aes(x, y))+geom_point(shape=1) + geom_smooth(method=lm, se=F)
  hist_right <- ggplot()+geom_histogram(aes(y))+coord_flip()
  grid.arrange(hist_top, empty, scatter, hist_right, ncol=2, nrow=2, widths=c(4, 1), heights=c(1, 4))
}

# RQ: Do unfollowed hints cause students to stop asking for them?
# Not exactly, but followed hints definitely indicate students will keep asking

buildDedup <- function() {
  hints$editBin <- round(hints$editPerc, 1)
  hints$timeBin <- round(hints$time / 1000 / 60)
  for (i in 1:nrow(hints)) {
    row <- hints[i,]
    minTime <- row$time - 1000 * 60
    possibleIDs <- hints[hints$assignment == row$assignment & hints$attemptID == row$attemptID & hints$hash == row$hash & hints$rowID <= row$rowID & 
                           (hints$time >= minTime | hints$codeHash == row$codeHash),]$rowID
    hints[i,"dedupID"] <- min(possibleIDs)
  }
  #dedup <- ddply(hints, c("assignment", "attemptID", "hash"), summarize, 
  #dedup <- ddply(hints, c("assignment", "attemptID", "hash", "editPerc"), summarize, 
  dedup <- ddply(hints, c("assignment", "attemptID", "hash", "dedupID"), summarize, 
                 type=first(type), startEdit=first(editPerc), endEdit=tail(editPerc, n=1), 
                 count=length(followed), anyF=any(followed), indexF=tail(c(NA, which(followed)), n=1), followEdit=editPerc[indexF], delete=all(delete),
                 followRowID=ifelse(!is.na(indexF), rowID[indexF], NA), rowID=rowID[1], pauseF=pause[ifNA(indexF, 1)], durationF=duration[ifNA(indexF, 1)])
  
  dedup$edit <- ifNA(dedup$followEdit, dedup$startEdit)
  # dedup <- dedup[order(dedup$assignment, dedup$attemptID, dedup$edit),]
  dedup <- dedup[order(dedup$assignment, dedup$attemptID, dedup$startEdit),]
  
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
  
  # 94.4% of those who follow their first hint ask for another
  mean(firsts$cont[firsts$anyF])
  # 68.8% for those who don't
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
                   thirdHint=rowID[3], firstFollow=anyF[1], secondFollow=anyF[2], thirdFollow=anyF[3])
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

labelChances <- function(data, n) {
  data$label <- 0
  for (assignment in unique(data$assignment)) {
    print(assignment)
    labels <- getLabels(data[data$assignment==assignment,]$nHints, n)
    data[data$assignment==assignment,]$label <- labels
  }
  return (data)
}

getLabels <- function(x, n) {
  qs <- quantile(x[!is.na(x)], seq(0, 1, 1 / n))
  print(qs)
  labels <- rep(1, length(x))
  labels[is.na(x)] <- 0
  for (i in 2:n) {
    labels[!is.na(x) & x > qs[i]] <- i
  }
  return (labels)
}

buildHintHashes <- function() {
  hintHashes <- ddply(dedup, c("hash"), summarize, f=sum(anyF), u=sum(!anyF), n=length(anyF))
  
  # Rarer hints are less likely to be followed
  table(hintHashes$n, hintHashes$f==hintHashes$n)
  table(hintHashes$n, hintHashes$u==hintHashes$n)
  table(hintHashes$n, hintHashes$f/hintHashes$n)
  # 48.3% of dedup'd hints are followed
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

testAllHints <- function() {
  ratings <- rbind(loadHintsFile("data/firstHints.csv"), loadHintsFile("data/secondHints.csv"))
  ratings <- ratings[ratings$id %in% chances$firstHint | ratings$id %in% chances$secondHint,]
  cor.test(ratings$relevant, ratings$correct, method="spearman")
  cor.test(ratings$correct, ratings$interp, method="spearman")
  cor.test(ratings$relevant, ratings$interp, method="spearman")
  
  ratings <- merge(ratings, hints, by.x="id", by.y="rowID")
  # no significant correlation between score and duration dialog was viewed
  cor.test(ratings$score, ratings$duration, method="spearman")
  # small, negative, significant correlation between score and pause before next action
  cor.test(ratings$score, ratings$pause, method="spearman")
}

library(vcd)
library(Exact)
testRatedHints <- function() {
  ratedHints <- loadRatedHints()
  ratedHints$pFollowed <- ratedHints$nFollow / ratedHints$nHints
  ratedHints$pFollowed1 <- (ratedHints$nFollow - ratedHints$firstFollow) / (ratedHints$nHints - 1)
  #ratedHints$pFollowed1[is.nan(ratedHints$pFollowed1)] <- 0
  ratedHints$pFollowed2 <- (ratedHints$nFollow - ratedHints$firstFollow - ratedHints$secondFollow) / (ratedHints$nHints - 2)
  #ratedHints$pFollowed2[is.nan(ratedHints$pFollowed2)] <- 0
  
  # Followed hints are rated significantly higher for first and second
  # Also worth noting: followed first and second hints had a mean score of 7.5 and 8.5 respectively,
  # so students seem to have quite high standards
  condCompare(ratedHints$score_1, ratedHints$firstFollow)
  condCompare(ratedHints$score_2, ratedHints$secondFollow)
  
  # First hints rated at least the median (7) score were >3x as likely to be followed (2/23=8.7% vs 14/48=29.2%)
  ratedHints$firstBetter <- ratedHints$score_1 >= median(ratedHints$score_1)
  ddply(ratedHints, c("firstBetter"), summarize, pFollow=mean(firstFollow), followed=sum(firstFollow), n=length(firstFollow))
  # Second hints rated at least the median (8) score were >3x as likely to be followed (3/21=14.3% vs 11/32=34.4%)
  ratedHints$secondBetter <- ratedHints$score_2 >= median(ratedHints$score_2, na.rm=T)
  ddply(ratedHints[!is.na(ratedHints$secondHint),], c("secondBetter"), summarize, pFollow=mean(firstFollow), followed=sum(firstFollow), n=length(firstFollow))
  
  secondHints <- ratedHints[!is.na(ratedHints$secondHint),]
  
  # Dependence between following first and second hint is borderline significant
  exact.test(table(secondHints$firstFollow, secondHints$secondFollow), method="boschloo", model="Multinomial")
  # Quicker, less powerful version of the above that shows near-significance
  exact.test(table(secondHints$firstFollow, secondHints$secondFollow))
  # 2.15x as likely: 10/17 vs 6/22
  table(ratedHints$firstFollow, ratedHints$secondFollow)

  # No correlation between first hint score and second hint following  
  cor.test(ratedHints$score_1, as.numeric(ratedHints$secondFollow))
  
  # Students who receive a 3-correct first hint are significantly less likely than those who 
  # receive a 1-correct first hint to have a label of 1 (few hints)
  mosaic(table(ratedHints$label, ratedHints$correct_1))
  firstNot2 <- ratedHints[ratedHints$correct_1 != 2,]
  exact.test(table(firstNot2$correct_1, firstNot2$label == 1))
  # Very expensive version, possibly more powerful
  # exact.test(table(firstNot2$label == 1, firstNot2$correct_1), alternative="two.sided", method="Boschloo", model="multinomial")
  # They are also more likely to follow 1+ hints afterwards, but it's not sifnificant
  firstNot2$nFollowLater = firstNot2$nFollow - firstNot2$firstFollow
  exact.test(table(firstNot2$correct_1, firstNot2$nFollowLater > 0))

  
  # For both hints, label correlates to each 
  fhs <- ddply(ratedHints, c("label"), summarize, n=length(timing_1), mT = mean(as.numeric(timing_1)), mRel=mean(relevant_1), sdRel=sd(relevant_1), mCorrect=mean(correct_1), sdCorrect=sd(correct_1), mInterp=mean(interp_1), sdInter=sd(interp_1), mInsight=safeMean(zInsight_1), mScore=mean(score_1), sdScore=sd(score_1))
  shs <- ddply(secondHints, c("label"), summarize, n=length(timing_2), mT = mean(as.numeric(timing_2)), mRel=mean(relevant_2), sdRel=sd(relevant_2), mCorrect=mean(correct_2), sdCorrect=sd(correct_2), mInterp=mean(interp_2), sdInter=sd(interp_2), mInsight=safeMean(zInsight_2), mScore=mean(score_2), sdScore=sd(score_2))
  
  kruskal.test(ratedHints$score_1, ratedHints$label)
  kruskal.test(secondHints$score_2, secondHints$label)
  
  # First hint score is a marginally significant predicor of label
  condCompare(ratedHints$score_1, ratedHints$label == 1)
  # It does significantly correlate
  cor.test(ratedHints$score_1, ratedHints$label, method="spearman")
  # But not to number of hitns
  cor.test(ratedHints$score_1, ratedHints$nHints, method="spearman")
  # Doesn't correlate with future hints followed or percentage followed
  cor.test(ratedHints$score_1, ratedHints$nFollow - ratedHints$firstFollow, method="spearman")
  cor.test(ratedHints$score_1, ratedHints$pFollowed1, method="spearman")
  
  # Significance is not achieved with nHints >= 3, though it is marginal for score_2
  condCompare(ratedHints$score_1, ratedHints$nHints >=3)
  condCompare(ratedHints$score_2, ratedHints$nHints >=3)
  
  # Second label and hint score are correlated, and label (marginally) singificantly predicts score
  condCompare(ratedHints$score_2, ratedHints$label == 1)
  condCompare(ratedHints$score_2, ratedHints$label == 3)
  cor.test(ratedHints$score_2, ratedHints$label, method="spearman")
  cor.test(ratedHints$score_2, ratedHints$nHints, method="spearman")
  # Strong correlation between second hint rating and future hints followed
  cor.test(ratedHints$score_2, ratedHints$nFollow - ratedHints$firstFollow - ratedHints$secondFollow, method="spearman")
  plot(jitter(ratedHints$score_2), jitter(ratedHints$nFollow - ratedHints$firstFollow - ratedHints$secondFollow))
  # And marginal significant correlation with percentage of future hints followed
  cor.test(ratedHints$score_2, ratedHints$pFollowed2, method="spearman")
  plot(jitter(ratedHints$score_2), jitter(ratedHints$pFollowed2))
  
  # Same with relevance
  condCompare(ratedHints$relevant_2, ratedHints$label == 3)
  cor.test(ratedHints$relevant_2, ratedHints$label, method="spearman")
  cor.test(ratedHints$relevant_2, ratedHints$nHints, method="spearman")
  
  condCompare(ratedHints$label, ratedHints$score_1 + ratedHints$score_2 >= 15)
  table(ratedHints$label, ratedHints$score_1 + ratedHints$score_2 >= 15)

  # Oddly, filtering out late hints reverses the trend to some extent  
  earlyFirst <- ratedHints[ratedHints$timing_1 != "Late",]
  cor.test(earlyFirst$score_1, earlyFirst$label, method="spearman")
  # Same with second hints  
  earlySecond <- ratedHints[ratedHints$timing_2 != "Late",]
  cor.test(earlySecond$score_2, earlySecond$label, method="spearman")
  
  # Hmm... first hint score is significantly, negatively correlated with progress
  cor.test(ratedHints$score_1, as.numeric(ratedHints$timing_1), method="spearman")
  # Negative but not significant for the second hint
  cor.test(ratedHints$score_2, as.numeric(ratedHints$timing_2), method="spearman")
  
  # Students stop asking for hints after a hint that is at least median Quality only 
  # 16.7\% of the time (11/66), while they stop 42.1\% of the time after other hints (16/38). 
  table(ratedHints$score_1 >= 7 & ratedHints$score_2 >= 8, ratedHints$nHints > 2)
}




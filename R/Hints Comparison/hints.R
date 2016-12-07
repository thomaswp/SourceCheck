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
  
  timeByHints <<- ddply(projs[,-3], c("dataset", "assignment", "hint3"), summarize, meanActive=safeMean(active), meanTotal=safeMean(total), meanPercIdle=safeMean(percIdle), n=length(grade))
  timeByFollowed <<- ddply(projs[,-3], c("dataset", "assignment", "follow2"), summarize, meanActive=safeMean(active), meanTotal=safeMean(total), meanPercIdle=safeMean(percIdle), n=length(grade))
  # projs <<- projs[projs$hints < 60,]
  # totalsNO <<- ddply(projs[,-1], c(), colwise(sum))
  # projs$pFollowed <<- ifelse(projs$hints == 0, 0, projs$followed / projs$hints)
  
  hints <<- csv(dir2016, "hints.csv") 
  hints <<- hints[hints$id %in% projs$id,]
  hints$duration[hints$duration < 0] <<- NA
  hints$duration[hints$duration > 500] <<- NA
  hints$pause[hints$pause < 0] <<- NA
  hints$pause[hints$pause > 500] <<- NA
}

hintStats <- function() {
  # 38.8% of hints displayed were followed
  mean(hints$followed)
  # 37.4% of hints were duplicates
  mean(hints$duplicate)
  # 14.5% of hints were exact repeats of the last hint
  mean(hints$repeat.)
  # 47.3% of hints were followed eventually
  mean(ddply(hints, c("id", "hash"), summarize, f=any(followed))$f)

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
  users <- ddply(hints, c("assignment", "id"), summarize, hints=length(duplicate), unq=sum(!duplicate), unqF=length(unique(hash[followed])), firstF=followed[[1]])
  users$percF <- users$unqF / users$unq
  users$grade <- sapply(1:nrow(users), function(i) projs[as.character(projs$assignment) == users[i,]$assignment & as.character(projs$id) == users[i,]$id,]$grade)
  
  # Significant (but not as strong) correlation between unique hints requested and percent followed
  cor.test(users$unq, users$percF)
  
  # No significant correlations between grades and anything
  # but remember grades are uniformly high
  cor.test(users$percF, users$grade)
  cor.test(users$unqF, users$grade)
  cor.test(users$unq, users$grade)
  table(users$grade)
  
  # 5.9% of users who requested 1 unique hint followed it. Wow.
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
  
  return (users)
}

# RQ: Do unfollowed hints cause students to stop asking for them?
# Not exactly, but followed hints definitely indicate students will keep asking

buildDedup <- function() {
  dedup <- ddply(hints, c("assignment", "id", "hash"), summarize, type=first(type), startEdit=first(editPerc), endEdit=tail(editPerc, n=1), 
                 count=length(followed), anyF=any(followed), indexF=tail(c(NA, which(followed)), n=1), followEdit=editPerc[indexF], delete=all(delete))
  
  dedup$edit <- ifNA(dedup$followEdit, dedup$startEdit)
  dedup <- dedup[order(dedup$assignment, dedup$id, dedup$edit),]
  
  dedup$nth <- sapply(1:nrow(dedup), function(i) {
    row <- dedup[i,]
    user <- dedup[dedup$assignment == row$assignment & dedup$id == row$id,]
    nth <- match(row$hash, user$hash)
  })
  
  dedup$n <- sapply(1:nrow(dedup), function(i) {
    row <- dedup[i,]
    user <- dedup[dedup$assignment == row$assignment & dedup$id == row$id,]
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

findChances <- function() {
  chances <- ddply(dedup, c("assignment", "id"), summarize, unF=ifNA(first(nth[anyF]) - 1, length(nth)), everF=any(anyF), hints=length(nth))
  # For most students who don't follow a hint, 15 (65.2%) only tried one bad hint, 7 tried 2 and 1 tried more (5).
  # So it looks like for 2/3 of students (who will give up), we get one shot at a hint before they give up
  table(chances$unF, chances$everF)
  
  # How many hints we know for sure each student was willing to try before giving up
  chances$didntGiveUpBy <- ifelse(chances$everF, chances$unF + 1, chances$unF)
  # 24/55=43.5% were willing to try 2+, 12/55=21.8% were willing to try 3+ hints
  # Remember: students who get a good hint may have been willing to wait longer for it
  table(chances$didntGiveUpBy)
  
  # So, what are these bad hints?
  sadUsers <- chances[chances$unF == 1 & !chances$everF,]$id
  sadHints <- dedup$hash[dedup$id %in% sadUsers & dedup$nth==1]
  cat(paste(sadUsers, sadHints, sep=","))
  # sadUsers <- chances[chances$unF == 2 & !chances$everF,]$id
}





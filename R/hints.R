library(ggplot2)
library(plyr)
library(reshape2)

loadData <- function() {
  rm(list=ls())
  
  projs <<- read.csv("../data/csc200/spring2016/analysis/guess1Lab-projs.csv") 
  totals <<- ddply(projs[,-1], c(), colwise(sum)) 
  projs <<- projs[projs$hints < 60,]
  totalsNO <<- ddply(projs[,-1], c(), colwise(sum))
  projs$letter <<- sapply(projs$grade, binGrade)
  projs$pFollowed <<- ifelse(projs$hints == 0, 0, projs$followed / projs$hints)
  
  hints <<- read.csv("../data/csc200/spring2016/analysis/guess1Lab-hints.csv") 
  hints <<- hints[hints$id %in% projs$id,]
  
  fall <<- read.csv("../data/csc200/fall2015/analysis/guess1Lab-projs.csv")  
  fall$letter <<- sapply(fall$grade, binGrade)
  
  hint <- read.csv("../data/csc200/spring2016/hint.csv")  
  hint$type <- "hint"
  snapshot <<- read.csv("../data/csc200/spring2016/snapshot.csv")  
  snapshot$isTaken <<- NA
  snapshot$type <<- "snapshot"
  snapshot <<- rbind(snapshot, hint)
  
  maxTime <- data.frame(id=levels(snapshot$id))
  maxTime$time <- sapply(maxTime$id, function(id) max(snapshot[snapshot$id==id,"time"]))
  snapshot$maxTime <<- sapply(snapshot$id, function(id) maxTime[maxTime$id==id,"time"])
  snapshot$timeNorm <<- snapshot$time / snapshot$maxTime
  
  maxDis <- data.frame(id=levels(snapshot$id))
  maxDis$dis <- sapply(maxDis$id, function(id) max(snapshot[snapshot$id==id,"distance"]))
  snapshot$maxDis <<- sapply(snapshot$id, function(id) maxDis[maxDis$id==id,"dis"])
  snapshot$distanceNorm <<- snapshot$distance / snapshot$maxDis
  
  
  goals <<- read.csv("../data/csc200/spring2016/analysis/guess1Lab-goals.csv") 
  goals$percSat <<- goals$satisfied / goals$finished
  goals[goals$finished == 0,]$percSat <<- 0
  goals$used <<- goals$gap > 60 & goals$finished > 0
  
  part <<- goals[goals$finished > 0,]
  
  all <- merge(projs, goals)
}

binGrade <- function(grade) {
  projs <- as.ordered(c("F", "CD", "B", "A"))
  if (grade == 1) return (projs[4])
  else if (grade >= 0.85) return (projs[3])
  else if (grade >= 0.65) return (projs[2])
  return (projs[1])
}

plotStudent <- function(id) {
  data <- snapshot[snapshot$id==id,]
  ggplot(data, aes(x = time, y = distance)) +
    geom_line() +
    geom_smooth() + 
    geom_point(aes(color=type))
}

plotStudents <- function(id) {
  ggplot(snapshot, aes(x = timeNorm, y = distanceNorm, group=id)) +
    geom_line()
    #geom_smooth() + 
    #geom_point(aes(color=type))
}

plotRequestedGrades <- function() {
  hintQ <- ddply(projs, c("grade", "hints"), "nrow")
  qplot(hints, grade, data=hintQ, size=nrow) +
    labs(x="Hints hints", y="Grade", size="Frequency", title="Grade vs Hints hints")
}

plotFollowedGrades <- function() {
  hintQ <- ddply(projs, c("grade", "followed"), "nrow")
  qplot(followed, grade, data=hintQ, size=nrow) +
    labs(x="Hints Followed", y="Grade", size="Frequency", title="Grade vs Hints Followed")
}

plotOverTime <- function(bins = 10) {
  hints <- hints[!hints$unchanged,]
  hints$bin <- floor(hints$editPerc * bins) + 1
  data <- ddply(hints, c("id", "bin"), summarize, accepted = sum(followed), rejected = sum(!followed), total=length(followed))
  for (id in unique(data$id)) {
    for (bin in 1:bins) {
      if (sum(data$id == id & data$bin == bin) == 0) {
        data <- rbind(data, data.frame(id=id, bin=bin, accepted=0, rejected=0, total=0))
      } 
    }
  }
  orderedIds <- projs$id[order(projs$pFollowed)]
  data$id <- match(data$id, orderedIds)
  data <- melt(data, id=c("id", "bin"))
  data$group <- paste(data$id, data$variable)
  ggplot(data, aes(x=bin, y=value, color=variable, group=group)) +
    geom_line() +
    geom_point() +
    facet_wrap(~id)
}

nf <- function(x, n) {
  if (length(x) <= n) return (-1)
  return (x[[n]])
}

nthCor <- function(nth) {
  nth <<- nth
  hints <- hints[!hints$unchanged,]
  data <- ddply(hints, c("id"), summarize,
               perc = mean(followed), 
               nFollowed = sum(followed),
               percAfter = mean(followed[-1:-nth]),
               nFollowedAfter = sum(followed[-1:-nth]),
               nthFollowed = nf(followed, nth))
  data <- data[data$nthFollowed >= 0,]
  data
  #print (cor(data$perc, data$nthFollowed))
  #plot(data$perc ~ jitter(data$nthFollowed))
}

se <- function(x) sqrt(var(x, na.rm=TRUE)/sum(!is.na(x)))

plotAfter <- function(cutoff = 4) {
  data <- NA
  i <- 1
  while (T) {
    nc <- nthCor(i)
    if (nrow(nc) < cutoff) break
    row <- ddply(nc, "nthFollowed", summarize, mean=mean(nFollowed), se=se(nFollowed))
    row$n <- i
    data <- rbind(data, row)
    i <- i + 1
  }
  data <- data[-1,]
  
  data$nthFollowed <- ordered(data$nthFollowed)
  
  ggplot(data, aes(x=n, y=mean, color=nthFollowed, group=nthFollowed)) +
    geom_line() + geom_point() +
    geom_ribbon(aes(ymin=mean-se, ymax=mean+se), alpha=0.3)
}

plotCor <- function(cutoff = 4) {
  data <- NA
  i <- 1
  while (T) {
    nc <- nthCor(i)
    if (nrow(nc) < cutoff) break
    test <- cor.test(nc$nFollowedAfter, nc$nthFollowed)
    row <- data.frame(n=i, cor=test$estimate, min=test$conf.int[[1]], max=test$conf.int[[2]], p=test$p.value)
    data <- rbind(data, row)
    i <- i + 1
  }
  data <- data[-1,]
  data <- data[data$p < 0.05,]
  
  ggplot(data, aes(x=n, y=cor)) +
    geom_line() + geom_point() +
    geom_ribbon(aes(ymin=min, ymax=max), alpha=0.3)
}

hintsTests <- function() {
  # 42.8% of hints were followed
  mean(hints$followed)
  # but 58.3% of objective completing hints were followed  
  mean(hints[hints$obj != "",]$followed)

  # Hint requests were mostly normal, with a bit of bimodality  
  hist(hints$timePerc)
  
  # followed hints generally came a bit earlier
  ggplot(hints, aes(x=as.factor(followed), y=timePerc)) + geom_boxplot()
  # this difference is significant
  wilcox.test(hints$timePerc ~ hints$followed)
  # but the difference in means was not much (6% or so)
  
  hints$early <- hints$editPerc < 0.5
  evl <- ddply(hints, c("id"), summarize, 
               percEarly = sum(followed & early) / sum(early), 
               rejEarly = sum(early & !followed), 
               perc = mean(followed), 
               er = sum(early), lt = sum(!early), n = length(early),
               firstFollowed = followed[[1]])
  cor.test(evl$percEarly, evl$lt)
  cor.test(evl$perc, evl$lt)
  cor.test(evl$perc, evl$n)
  
  plot(evl$perc ~ jitter(evl$firstFollowed))
  wilcox.test(evl$perc ~ evl$firstFollowed)
  cor.test(evl$perc, evl$firstFollowed)
  
  cor.test(evl$rejEarly, evl$late)
  
  # Maybe the more hints asked for early, the more asked for late (non-sig)
  plot(evl$early, evl$late)
  cor.test(evl$early, evl$late)
  
  table(hints$followed, hints$delete)
  table(hints$followed, hints$change < -5)
}

subgoalTests <- function() {
  
  # Of those who check goals, their accuracy correlated with grade
  cor.test(part$percSat, part$grade)
  plot(jitter(part$grade) ~ part$percSat, col=as.factor(part$used))
  # But their total correct completed goals did not
  cor.test(part$satisfied, part$grade)
  plot(jitter(part$grade) ~ part$satisfied, col=as.factor(part$used))
  # But... that could just be that the people who wait until the end do better?
  
  # Strong correlation between number of goals you said you got and how many were right
  # But that kind of meaningless b/c one bounds the other
  cor.test(part$finished, part$satisfied)
  plot(jitter(part$finished) ~ part$satisfied, col=as.factor(part$used))
  
  # Still percentage was generally high  
  hist(part$percSat)
  mean(part$percSat)
  boxplot(part$percSat)
  
  # All with low median gap finished "all objectives"  
  plot(part$finished ~ log(part$gap), col=as.factor(part$used))
  
  # Those using the subgoals perform about the same as the others
  wilcox.test(goals$grade ~ goals$used)
  plot(jitter(goals$grade) ~ log(goals$gap + 1), col=as.factor(goals$used))
  
  # No correlation at all between hint and subgoal usage
  cor.test(all$finished, all$hints)
}

projTests <- function() {
  
  # all non-significantly positively correlate to performance
  cor.test(projs$hints, projs$grade)
  cor.test(projs$followed, projs$grade)
  cor.test(projs$pFollowed, projs$grade) # none are significant
  
  # Hint usage and percFollowed are very correlated
  cor.test(projs$pFollowed, projs$hints)
  
  # students following 1+ hints don't do significantly better
  wilcox.test(projs[projs$followed > 1, "grade"], projs[projs$followed <= 1, "grade"])
  
  # No students following 1+ hints misses more than 1 objective
  table(projs[projs$followed > 1, "grade"])
  
  # students following 1+ hints do 4% better... but this isn't really a meaningful measure with nonnormal data
  mean(projs[projs$followed > 1, "grade"]) - mean(projs[projs$followed <= 1, "grade"])
}
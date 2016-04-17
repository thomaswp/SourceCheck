library(ggplot2)
library(plyr)

loadData <- function() {
  rm(list=ls())
  
  hint <<- read.csv("../data/csc200/spring2016/analysis/guess1Lab-hints.csv")  
  hint$letter <<- sapply(hint$grade, binGrade)
  hint$pFollowed <<- ifelse(hint$hints == 0, 0, hint$followed / hint$hints)
  hint <<- hint[hint$hints < 60,]
  
  fall <<- read.csv("../data/csc200/fall2015/analysis/guess1Lab-hints.csv")  
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
  
  all <- merge(hint, goals)
}

binGrade <- function(grade) {
  hint <- as.ordered(c("F", "CD", "B", "A"))
  if (grade == 1) return (hint[4])
  else if (grade >= 0.85) return (hint[3])
  else if (grade >= 0.65) return (hint[2])
  return (hint[1])
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
  hintQ <- ddply(hint, c("grade", "hints"), "nrow")
  qplot(hints, grade, data=hintQ, size=nrow) +
    labs(x="Hints hints", y="Grade", size="Frequency", title="Grade vs Hints hints")
}

plotFollowedGrades <- function() {
  hintQ <- ddply(hint, c("grade", "followed"), "nrow")
  qplot(followed, grade, data=hintQ, size=nrow) +
    labs(x="Hints Followed", y="Grade", size="Frequency", title="Grade vs Hints Followed")
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

tests <- function() {
  
  # all non-significantly positively correlate to performance
  cor.test(hint$hints, hint$grade)
  cor.test(hint$followed, hint$grade)
  cor.test(hint$pFollowed, hint$grade) # none are significant
  
  # Hint usage and percFollowed are very correlated
  cor.test(hint$pFollowed, hint$hints)
  
  # students following 1+ hints don't do significantly better
  wilcox.test(hint[hint$followed > 1, "grade"], hint[hint$followed <= 1, "grade"])
  
  # No students following 1+ hints misses more than 1 objective
  table(hint[hint$followed > 1, "grade"])
  
  # students following 1+ hints do 4% better... but this isn't really a meaningful measure with nonnormal data
  mean(hint[hint$followed > 1, "grade"]) - mean(hint[hint$followed <= 1, "grade"])
}
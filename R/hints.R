library(ggplot2)

loadData <- function() {
  rm(list=ls())
  
  grades <<- read.csv("../data/csc200/spring2016/grades.csv")  
  grades$letter <<- sapply(grades$grade, binGrade)
  grades$pFollowed <<- ifelse(grades$requested == 0, 0, grades$followed / grades$requested)
  
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
}

binGrade <- function(grade) {
  grades <- as.ordered(c("F", "CD", "B", "A"))
  if (grade == 1) return (grades[4])
  else if (grade >= 0.85) return (grades[3])
  else if (grade >= 0.65) return (grades[2])
  return (grades[1])
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

tests <- function() {
  
  # all significantly correlate to performance
  cor.test(grades$requested, grades$grade)
  cor.test(grades$followed, grades$grade)
  cor.test(grades$pFollowed, grades$grade) # only this is significant, though
  
  # students following 1+ hinst do significantly better
  wilcox.test(grades[grades$followed > 1, "grade"], grades[grades$followed <= 1, "grade"])
  
  # only 1/16 student following 1+ hints misses more than 1 objective
  table(grades[grades$followed > 1, "grade"])
  
  # students following 1+ hints do 13% better
  mean(grades[grades$followed > 1, "grade"]) - mean(grades[grades$followed <= 1, "grade"])
}
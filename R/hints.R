loadData <- function() {
  rm(list=ls())
  
  grades <<- read.csv("../data/csc200/spring2016/grades.csv")  
  grades$letter <<- sapply(grades$grade, binGrade)
  grades$pFollowed <<- ifelse(grades$requested == 0, 0, grades$followed / grades$requested)
}

binGrade <- function(grade) {
  grades <- as.ordered(c("F", "CD", "B", "A"))
  if (grade == 1) return (grades[4])
  else if (grade >= 0.85) return (grades[3])
  else if (grade >= 0.65) return (grades[2])
  return (grades[1])
}

tests <- function() {
  
  # all significantly correlate to performance
  cor(grades$requested, grades$grade)
  cor(grades$followed, grades$grade)
  cor(grades$pFollowed, grades$grade)
  
  # students following 1+ hinst do significantly better
  wilcox.test(grades[grades$followed > 1, "grade"], grades[grades$followed <= 1, "grade"])
  
  # only 1/16 student following 1+ hints misses more than 1 objective
  table(grades[grades$followed > 1, "grade"])
  
  # students following 1+ hints do 13% better
  mean(grades[grades$followed > 1, "grade"]) - mean(grades[grades$followed <= 1, "grade"])
}
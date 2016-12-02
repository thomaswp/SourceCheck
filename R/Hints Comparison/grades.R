source('util.R')

loadData <- function() {
  fall2015 <<- read.csv("grades/fall2015.csv")
  spring2016 <<- read.csv("grades/spring2016.csv")
  
  fall2015$dataset <- "Fall2015"
  spring2016$dataset <- "Spring2016"
  
  # Grades were out of 70 points in Spring
  spring2016$guess1Lab <- spring2016$guess1Lab / .7
  spring2016$polygonMakerLab <- spring2016$polygonMakerLab / .7
  
  
  grades <- c.merge(fall2015, spring2016)
  
  compare <- ddply(grades, c("dataset"), colwise(safeMean))
  compareCompletion <- ddply(grades, c("dataset"), colwise(function(x) sum(x > 0)))
}
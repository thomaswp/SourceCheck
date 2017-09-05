source('util.R')

loadData <- function() {
  #fall2015 <<- read.csv("grades/fall2015.csv")
  #fall2016 <<- read.csv("grades/fall2016.csv")
  spring2016 <<- read.csv("grades/spring2016.csv")
  spring2017 <<- read.csv("grades/spring2017.csv")
  
  spring2016$dataset <- "S16"
  spring2017$dataset <- "S17"
  
  # Grades were out of 70 points in 2016
  spring2016$guess1Lab <- spring2016$guess1Lab / .7
  spring2016$polygonMakerLab <- spring2016$polygonMakerLab / .7
  spring2017$guess1Lab <- spring2017$guess1Lab / .7
  spring2017$polygonMakerLab <- spring2017$polygonMakerLab / .7
  
  
  grades <- c.merge(spring2016, spring2017)
  
  compare <- ddply(grades, c("dataset"), colwise(safeMean))
  # Why does squiral'15 have only 77 non-0 grades when I have 79 valid submissions?
  compareCompletion <- ddply(grades, c("dataset"), colwise(function(x) mean(x > 0)))
  
  
}
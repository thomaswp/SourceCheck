source('util.R')

loadData <- function() {
  fall2015 <<- read.csv("grades/fall2015.csv")
  spring2016 <<- read.csv("grades/spring2016.csv")
  fall2016 <<- read.csv("grades/fall2016.csv")
  
  fall2015$dataset <- "Fall2015"
  spring2016$dataset <- "Spring2016"
  fall2016$dataset <- "Fall2016"
  
  # Grades were out of 70 points in 2016
  spring2016$guess1Lab <- spring2016$guess1Lab / .7
  spring2016$polygonMakerLab <- spring2016$polygonMakerLab / .7
  fall2016$guess1Lab <- fall2016$guess1Lab / .7
  fall2016$polygonMakerLab <- fall2016$polygonMakerLab / .7
  
  
  grades <- c.merge(c.merge(fall2015, spring2016), fall2016)
  
  compare <- ddply(grades, c("dataset"), colwise(safeMean))
  # Why does squiral'15 have only 77 non-0 grades when I have 79 valid submissions?
  compareCompletion <- ddply(grades, c("dataset"), colwise(function(x) sum(x > 0)))
}
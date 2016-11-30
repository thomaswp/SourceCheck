source('util.R')

loadData <- function() {
  fall2015 <<- read.csv("grades/fall2015.csv")
  spring2016 <<- read.csv("grades/spring2016.csv")
}
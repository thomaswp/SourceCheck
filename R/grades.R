

loadData <- function() {
  
  maxTest <<- 8;
  
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  percs <<- sapply(0:maxTest, function(i) paste("perc", i, sep=""))
    
  grades <<- read.csv("~/GitHub/SnapHints/data/csc200/fall2015/anlysis/guess1Lab/grade.csv")
  for (i in 1:(maxTest+1)) {
    grades[,percs[i]] <<- grades[,tests[i]] / grades$total
  }
  
  policies <<- sapply(unique(grades$policy), as.character)
  students <<- sapply(unique(grades$student), as.character)
}

compare <- function(action) {
  sapply(policies, function(policy) {
    colSums(grades[grades$policy==policy & grades$action==action, percs]) / length(students)
  })
}

compareSign <- function(action) {
  sapply(policies, function(policy) {
    colSums(sign(grades[grades$policy==policy & grades$action==action, percs])) / length(students)
  })
}

compareHelpful <- function(action) {
  sapply(policies, function(policy) {
    do <- grades[grades$policy==policy & grades$action=="do", percs]
    undo <- grades[grades$policy==policy & grades$action=="undo", percs]
    return (colSums(do > undo) / length(students))
  })
}

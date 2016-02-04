

loadData <- function() {
  
  maxTest <<- 8;
  
  tests <<- sapply(0:maxTest, function(i) paste("test", i, sep=""))
  percs <<- sapply(0:maxTest, function(i) paste("perc", i, sep=""))
    
  grade <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/grade.csv")
  for (i in 1:(maxTest+1)) {
    grade[,percs[i]] <<- grade[,tests[i]] / grade$total
  }
  
  chain <<- read.csv("../data/csc200/fall2015/anlysis/guess1Lab/chain.csv")
  chain <<- rbind(grade[grade$policy=="Hint All" | grade$policy=="Hint Exemplar",], chain)
  for (i in 1:(maxTest+1)) {
    chain[,percs[i]] <<- chain[,tests[i]] / chain$total
  }
  
  students <<- sapply(unique(grades$student), as.character)
}

policies <- function(grades) {
  sapply(unique(grades$policy), as.character)
}

compare <- function(grades, action) {
  sapply(policies(grades), function(policy) {
    colSums(grades[grades$policy==policy & grades$action==action, percs]) / length(students)
  })
}

compareSign <- function(grades, action) {
  sapply(policies(grades), function(policy) {
    colSums(sign(grades[grades$policy==policy & grades$action==action, percs])) / length(students)
  })
}

compareHelpful <- function(grades) {
  sapply(policies(grades), function(policy) {
    do <- grades[grades$policy==policy & grades$action=="do", percs]
    undo <- grades[grades$policy==policy & grades$action=="undo", percs]
    return (colSums(do > undo) / length(students))
  })
}

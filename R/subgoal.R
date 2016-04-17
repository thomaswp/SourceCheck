
loadData <- function() {
  rm(list=ls())
  goals <<- read.csv("../data/csc200/spring2016/analysis/guess1Lab-goals.csv") 
  goals$percSat <<- goals$satisfied / goals$finished
  goals[goals$finished == 0,]$percSat <<- 0
  goals$used <<- goals$gap > 60 & goals$finished > 0
  
  part <<- goals[goals$finished > 0,]
}

tests <- function() {
  
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
}
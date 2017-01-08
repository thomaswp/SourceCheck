
source("util.R")

library(irr)

agreement <- function(x, y) {
  df <- data.frame(x=x, y=y)
  kappa2(df, weight='squared')
}

testAgreement <- function() {
  r1 <- c.merge(read.csv("data/firstHints1.csv"), read.csv("data/secondHints1.csv"))
  r2 <- c.merge(read.csv("data/firstHints2.csv"), read.csv("data/secondHints2.csv"))

  usedHints <- c(chances$firstHint, chances$secondHint)
  r1 <- r1[r1$Row.ID %in% usedHints,]
  r2 <- r2[r2$Row.ID %in% usedHints,]
  
  # 0.840
  agreement(r1$Relevance, r2$Relevance)
  # 0.729
  agreement(r1$Correctness, r2$Correctness)
  # 0.637
  agreement(r1$Interpretability, r2$Interpretability)
}
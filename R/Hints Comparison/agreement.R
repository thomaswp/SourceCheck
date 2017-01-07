
source("util.R")

library(irr)

agreement <- function(x, y) {
  df <- data.frame(x=x, y=y)
  kappa2(df, weight='squared')
}

testAgreement <- function() {
  r1 <- c.merge(read.csv("data/firstHints1.csv"), read.csv("data/secondHints1.csv"))
  r2 <- c.merge(read.csv("data/firstHints2.csv"), read.csv("data/secondHints2.csv"))

  # 0.863  
  agreement(r1$Relevance, r2$Relevance)
  # 0.769
  agreement(r1$Correctness, r2$Correctness)
  # 0.696
  agreement(r1$Interpretability, r2$Interpretability)
  # 0.48
  agreement(r1$Insightfulness, r2$Insightfulness)
  
  # total agreement
  tR1 <- c(r1$Relevance, r1$Correctness, r1$Interpretability, r1$Insightfulness)
  tR2 <- c(r2$Relevance, r2$Correctness, r2$Interpretability, r2$Insightfulness)
  
  #0.726 0
  agreement(tR1, tR2)
  
}

## Interrater agreement for assignments grading
# File name format: D - Dragon R - Rui T- Thomas 1: 2015  2:2016

testAgreementForGG1 <- function(){
  # Guessing Game 1
  gg1R1 <- c.merge(read.csv("data/2015Fall/GG1_D1.csv"), read.csv("data/2016Fall/GG1_D2.csv"))
  gg1R2 <- c.merge(read.csv("data/2015Fall/GG1_R1.csv"), read.csv("data/2016Fall/GG1_R2.csv"))
  
  #0.507
  agreement(gg1R1$Greet.by.name, gg1R2$Greet.by.name)
  
  #0.86
  agreement(gg1R1$Store.random.number, gg1R2$Store.random.number)
  
  #0.857
  agreement(gg1R1$Loop.until.it.s.guessed, gg1R2$Loop.until.it.s.guessed)
  
  #0.609
  agreement(gg1R1$Ask.for.guess, gg1R2$Ask.for.guess)
  
  #0.79
  agreement(gg1R1$Tell.if.too.high.low, gg1R2$Tell.if.too.high.low)
  
  #0.349
  agreement(gg1R1$Tell.if.correct, gg1R2$Tell.if.correct)
  
  # total agreement
  tgg1R1 <- c(gg1R1$Greet.by.name, gg1R1$Store.random.number, 
              gg1R1$Loop.until.it.s.guessed, gg1R1$Ask.for.guess,
              gg1R1$Tell.if.too.high.low, gg1R1$Tell.if.correct)
  
  tgg1R2 <- c(gg1R2$Greet.by.name, gg1R2$Store.random.number, 
              gg1R2$Loop.until.it.s.guessed, gg1R2$Ask.for.guess,
              gg1R2$Tell.if.too.high.low, gg1R2$Tell.if.correct)
  
  #0.724 0
  agreement(tgg1R1, tgg1R2)
}


testAgreementForGG2 <- function(){
  # Guessing Game 2
  gg2R1 <- c.merge(read.csv("data/2015Fall/GG2_D1.csv"), read.csv("data/2016Fall/GG2_D2.csv"))
  gg2R2 <- c.merge(read.csv("data/2015Fall/GG2_T1.csv"), read.csv("data/2016Fall/GG2_T2.csv"))
  
  #0.856
  agreement(gg2R1$Greets.by.name, gg2R2$Greets.by.name)
  
  #0.704
  agreement(gg2R1$Repeats.until.guessed, gg2R2$Repeats.until.guessed)
  
  #0.515
  agreement(gg2R1$Gives.feedback, gg2R2$Gives.feedback)
  
  #0.877
  agreement(gg2R1$Min.and.max, gg2R2$Min.and.max)
  
  #0.648
  agreement(gg2R1$Counts.guesses, gg2R2$Counts.guesses)
  
  #0.717
  agreement(gg2R1$Reports.guesses, gg2R2$Reports.guesses)
  
  # total agreement
  tgg2R1 <- c(gg2R1$Greets.by.name, gg2R1$Repeats.until.guessed, 
              gg2R1$Gives.feedback, gg2R1$Min.and.max,
              gg2R1$Counts.guesses, gg2R1$Reports.guesses)
  
  tgg2R2 <- c(gg2R2$Greets.by.name, gg2R2$Repeats.until.guessed, 
              gg2R2$Gives.feedback, gg2R2$Min.and.max,
              gg2R2$Counts.guesses, gg2R2$Reports.guesses)
  
  #0.765 0
  agreement(tgg2R1, tgg2R2)
}

testAgreementForGG3 <- function(){
  # Guessing Game 3
  gg3R1 <- c.merge(read.csv("data/2015Fall/GG3_D1.csv"), read.csv("data/2016Fall/GG3_D2.csv"))
  gg3R2 <- c.merge(read.csv("data/2015Fall/GG3_T1.csv"), read.csv("data/2016Fall/GG3_T2.csv"))
  
  #0.495
  agreement(gg3R1$Gets.Min.Max, gg3R2$Gets.Min.Max)
  
  #0.721
  agreement(gg3R1$Resets.List, gg3R2$Resets.List)
  
  #0.806
  agreement(gg3R1$Repeats, gg3R2$Repeats)
  
  #0.732
  agreement(gg3R1$Guess.not.in.list, gg3R2$Guess.not.in.list)
  
  #0.8
  agreement(gg3R1$Guess.added, gg3R2$Guess.added)
  
  #0.23
  agreement(gg3R1$Check.guess, gg3R2$Check.guess)
  
  # total agreement
  tgg3R1 <- c(gg3R1$Gets.Min.Max, gg3R1$Resets.List, 
              gg3R1$Repeats, gg3R1$Guess.not.in.list,
              gg3R1$Guess.added, gg3R1$Check.guess)
  
  tgg3R2 <- c(gg3R2$Gets.Min.Max, gg3R2$Resets.List, 
              gg3R2$Repeats, gg3R2$Guess.not.in.list,
              gg3R2$Guess.added, gg3R2$Check.guess)
  
  #0.729 0
  agreement(tgg3R1, tgg3R2)
}

testAgreementForPM <- function(){
  # Polygon Maker
  pmR1 <- c.merge(read.csv("data/2015Fall/PM_R1.csv"), read.csv("data/2016Fall/PM_R2.csv"))
  pmR2 <- c.merge(read.csv("data/2015Fall/PM_T1.csv"), read.csv("data/2016Fall/PM_T2.csv"))
  
  #1
  agreement(pmR1$X3.Block.Inputs, pmR2$X3.Block.Inputs)
  
  #0.507
  agreement(pmR1$Pen.thickness, pmR2$Pen.thickness)
  
  #0.854
  agreement(pmR1$Repeat...of.Sides, pmR2$Repeat...of.Sides)
  
  #1
  agreement(pmR1$Move.Size, pmR2$Move.Size)
  
  #1
  agreement(pmR1$Turn.sides...360, pmR2$Turn.sides...360)
 
  # total agreement
  tpmR1 <- c(pmR1$X3.Block.Inputs, pmR1$Pen.thickness, 
             pmR1$Repeat...of.Sides, pmR1$Move.Size,
             pmR1$Turn.sides...360)
  
  tpmR2 <- c(pmR2$X3.Block.Inputs, pmR2$Pen.thickness, 
             pmR2$Repeat...of.Sides, pmR2$Move.Size,
             pmR2$Turn.sides...360)
  #0.731 0
  agreement(tpmR1, tpmR2) 
}

testAgreementForSquiral <- function(){
  # Squiral
  sR1 <- c.merge(read.csv("data/2015Fall/S_D1.csv"), read.csv("data/2016Fall/S_D2.csv"))
  sR2 <- c.merge(read.csv("data/2015Fall/S_R1.csv"), read.csv("data/2016Fall/S_R2.csv"))
  
  #0.883
  agreement(sR1$X1.Block.Input, sR2$X1.Block.Input)
  
  #0.868
  agreement(sR1$Pen.down, sR2$Pen.down)
  
  #0.587
  agreement(sR1$Variable.Initialization, sR2$Variable.Initialization)
  
  #0.929
  agreement(sR1$Repeat.Rotations...4, sR2$Repeat.Rotations...4)
  
  #0.544
  agreement(sR1$Move...Turn, sR2$Move...Turn)
 
  #0.872
  agreement(sR1$Variable.Increment, sR2$Variable.Increment)
  
  
  # total agreement
  tsR1 <- c(sR1$X1.Block.Input, sR1$Pen.down, 
            sR1$Variable.Initialization, sR1$Repeat.Rotations...4,
            sR1$Move...Turn, sR1$Variable.Increment)
  
  tsR2 <- c(sR2$X1.Block.Input, sR2$Pen.down, 
            sR2$Variable.Initialization, sR2$Repeat.Rotations...4,
            sR2$Move...Turn, sR2$Variable.Increment)
  
  #0.826 0
  agreement(tsR1, tsR2) 
  
}

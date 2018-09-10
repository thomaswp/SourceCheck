library(plyr)
library(ggplot2)

read.qualtrics <- function(file) {
  data <- read.csv(file)
  data <- data[-1:-2,]
  for (i in 1:ncol(data)) {
    col <- data[,i]
    num <- suppressWarnings(as.numeric(as.character(col)))
    if (sum(!is.na(num)) > 0 && sum(!is.na(num)) / sum(col != "", na.rm=T) > 0.5) {
      data[,i] <- num
    } else if (class(col) == "factor") {
      data[,i] <- factor(col)
    }
  }
  data
}

loadData <- function() {
  consent <- read.qualtrics("data/consent.csv")
  post1 <- read.qualtrics("data/post1.csv")
  post2 <- read.qualtrics("data/post2.csv")
  post2 <- post2[post2$assignmentID == "drawTriangles",]
  preHelp <- read.qualtrics("data/pre-help.csv")
  postHelp <- read.qualtrics("data/post-help.csv")
  attempts <- read.csv("../../data/mturk/mturk2018/analysis/attempts.csv")
  print(sum(attempts$errors >= 5))
  attempts <- attempts[attempts$errors < 5 & !is.na(attempts$midSurveyTime) & !is.na(attempts$firstEditTime),]
  actions <- read.csv("../../data/mturk/mturk2018/analysis/actions.csv")
  
  length(users <- Reduce(intersect, list(
    consent$userID, 
    post1$userID, post2$userID, preHelp$userID, postHelp$userID, 
    attempts$userID[attempts$assignmentID == "polygonMakerSimple"],
    attempts$userID[attempts$assignmentID == "drawTriangles"]
  )))
  
  users <- users[!(users %in% post1$userID[duplicated(post1$userID)])]
  
  post1 <- post1[post1$userID %in% users,]
  post2 <- post2[post2$userID %in% users,]
  preHelp <- preHelp[preHelp$userID %in% users,]
  postHelp <- postHelp[postHelp$userID %in% users,]
  attempts <- attempts[attempts$userID %in% users, ]
  actions <- actions[actions$userID %in% users, ]
  
  preHelp <- merge(preHelp, actions)
  preHelp <- preHelp[order(preHelp$userID),]
  postHelp <- merge(postHelp, actions)
  postHelp <- postHelp[order(postHelp$userID),]
  
  attempts$hadCodeHints <- attempts$codeHints > 0
  attempts$hadTextHints <- attempts$textHints > 0
  attempts$hadReflects <- attempts$reflects > 0
  
  task1 <- attempts[attempts$assignmentID == "polygonMakerSimple",]
  task1 <- task1[order(task1$userID),]
  task2 <- attempts[attempts$assignmentID == "drawTriangles",]
  task2 <- task2[order(task2$userID),]
  
  
  hist(task1$objs)
  summary(lm(objs ~ hadCodeHints * hadTextHints + hadReflects, data=task1))
  
  task2$t1CodeHints <- task1$codeHints
  task2$t1TextHints <- task1$textHints
  task2$t1Reflects <- task1$reflects
  task2$t1HadCodeHints <- task2$t1CodeHints > 0
  task2$t1HadTextHints <- task2$t1TextHints > 0
  task2$t1HadReflects <- task2$t1Reflects > 0
  
  summary(lm(objs ~ t1HadCodeHints * t1HadTextHints + t1HadReflects, data=task2))
  
  hist(postHelp$Q10[postHelp$assignmentID=="polygonMakerSimple"])
  summary(lm(Q10 ~ codeHint * textHint + reflect, data=postHelp[postHelp$assignmentID=="polygonMakerSimple",]))
  summary(aov(Q10 ~ codeHint * textHint + reflect, data=postHelp[postHelp$assignmentID=="polygonMakerSimple",]))
  
  # Averaging over users, it looks like code and text hints are good, and relatively independent; reflections are bad
  task1Users <- ddply(postHelp[postHelp$assignmentID=="polygonMakerSimple",], 
                      c("userID", "assignmentID", "codeHint", "textHint", "reflect"), 
                      summarize, mRating=mean(Q10), nRating=length(Q10))
  hist(task1Users$mRating)
  summary(lm(mRating ~ codeHint * textHint + reflect, data=task1Users))
  anova(aov(mRating ~ codeHint + textHint + reflect, data=task1Users), aov(mRating ~ codeHint * textHint + reflect, data=task1Users))
  
  # Pretty similar results when randomizing, except smaller effect and reflect may not be bad
  hist(postHelp$Q10[postHelp$assignmentID=="drawTriangles"])
  summary(lm(Q10 ~ codeHint * textHint + reflect + userID, data=postHelp[postHelp$assignmentID=="drawTriangles",]))
  summary(aov(Q10 ~ codeHint * textHint + reflect + Error(userID), data=postHelp[postHelp$assignmentID=="drawTriangles",]))
  
  postHelp$followedHint <- postHelp$Q14 < 3
  table(postHelp$followedHint, postHelp$textHint, postHelp$assignmentID)
  fisher.test(postHelp$followedHint, postHelp$textHint)
  
  # How helpful was Snap?
  summary(lm(Q22_1 ~ codeHint + textHint + reflect, data=post1))
  # How difficult was the task?
  summary(lm(Q24_1 ~ codeHint + textHint + reflect, data=post1))
  # How prepared are you? - Really, this is where reflect prompts help?
  summary(lm(Q26_1 ~ codeHint + textHint + reflect, data=post1))
  
  ggplot(postHelp, aes(x=textHint==1, y=Q12_2)) + geom_boxplot() + facet_wrap(~assignmentID)
  
  hist(task1$obj2 / 60000)
  mean(task1$obj2 / 60000, na.rm=T)
  mean(!is.na(task1$obj2))
  hist(task1$idleTime / 60000)
  hist(task1$idleTime[task1$objs!=3] / 60000)
  
  hist(task2$obj2 / 60000)
  mean(task2$obj2 / 60000, na.rm=T)
  mean(!is.na(task2$obj2))
  hist(task2$idleTime / 60000)
  hist(task2$idleTime[task2$objs!=3] / 60000)
  
  #sink("C:/Users/Thomas/Desktop/poly.txt")
  cat(paste0(as.character(task1$lastCode[is.na(task1$obj2)]), "\n\n"))
  #sink("C:/Users/Thomas/Desktop/triangles.txt")
  cat(paste0(as.character(task2$lastCode[is.na(task2$obj2)]), "\n\n"))
  sink()
}


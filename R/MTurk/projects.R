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
  data$StartDate <- strptime(as.character(data$StartDate), "%Y-%m-%d %H:%M:%S")
  data$EndDate <- strptime(as.character(data$EndDate), "%Y-%m-%d %H:%M:%S")
  data <- data[data$StartDate > strptime("2018-09-05 00:00:00", "%Y-%m-%d %H:%M:%S"),]
  data
}

loadData <- function() {
  consentUF <- read.qualtrics("data/consent.csv")
  post1UF <- read.qualtrics("data/post1.csv")
  post2UF <- read.qualtrics("data/post2.csv")
  post2UF <- post2UF[post2$assignmentID == "drawTriangles",]
  preHelpUF <- read.qualtrics("data/pre-help.csv")
  postHelpUF <- read.qualtrics("data/post-help.csv")
  attemptsUF <- read.csv("../../data/mturk/mturk2018/analysis/attempts.csv")
  attemptsUF <- attemptsUF[attemptsUF$errors < 5 & !is.na(attemptsUF$firstEditTime),]
  actionsUF <- read.csv("../../data/mturk/mturk2018/analysis/actions.csv")
  
  usersUF <- Reduce(intersect, list(
    consentUF$userID, 
    preHelpUF$userID, postHelpUF$userID, 
    attemptsUF$userID
  ))
  
  usersUF <- usersUF[!(usersUF %in% post1UF$userID[duplicated(post1UF$userID)])]
  usersUF <- usersUF[!(usersUF %in% postHelpUF$userID[duplicated(postHelpUF$eventID)])]
  
  consentUF <- consentUF[consentUF$userID %in% usersUF,]
  post1UF <- post1UF[post1UF$userID %in% usersUF,]
  post2UF <- post2UF[post2UF$userID %in% usersUF,]
  preHelpUF <- preHelpUF[preHelpUF$userID %in% usersUF,]
  postHelpUF <- postHelpUF[postHelpUF$userID %in% usersUF,]
  attemptsUF <- attemptsUF[attemptsUF$userID %in% usersUF, ]
  actionsUF <- actionsUF[actionsUF$userID %in% usersUF, ]
  
  # Analysis of dropouts is difficult because they may have started multiple times in different conditions
  # We can only assign a condition if they complete the task
  dropouts <- consentUF$userID[!(consentUF$userID %in% post1UF$userID | consentUF$userID %in% post2UF$userID)]
  dropouts <- attemptsUF[attemptsUF$userID %in% dropouts,]
  
  consent <- consentUF
  post1 <- post1UF
  post2 <- post2UF
  preHelp <- preHelpUF
  postHelp <- postHelpUF
  attempts <- attemptsUF
  attempts <- attempts[!is.na(attempts$midSurveyTime),]
  
  users <- Reduce(intersect, list(
    consent$userID, 
    post1$userID, post2$userID, preHelp$userID, postHelp$userID, 
    attempts$userID[attempts$assignmentID == "polygonMakerSimple"],
    attempts$userID[attempts$assignmentID == "drawTriangles"]
  ))
  
  length(users)
  
  post1 <- post1[post1$userID %in% users,]
  post2 <- post2[post2$userID %in% users,]
  preHelp <- preHelp[preHelp$userID %in% users,]
  postHelp <- postHelp[postHelp$userID %in% users,]
  attempts <- attempts[attempts$userID %in% users, ]
  actions <- actions[actions$userID %in% users, ]
  
  preHelp <- merge(preHelp, actions)
  preHelp <- preHelp[order(preHelp$userID, preHelp$time),]
  postHelp <- merge(postHelp, actions)
  postHelp <- postHelp[order(postHelp$userID, postHelp$time),]
  
  attempts$hadCodeHints <- attempts$codeHints > 0
  attempts$hadTextHints <- attempts$textHints > 0
  attempts$hadReflects <- attempts$reflects > 0
  
  task1 <- attempts[attempts$assignmentID == "polygonMakerSimple",]
  task1 <- task1[order(task1$userID),]
  task2 <- attempts[attempts$assignmentID == "drawTriangles",]
  task2 <- task2[order(task2$userID),]
  
  
  hist(task1$objs)
  summary(lm(objs ~ hadCodeHints * hadTextHints + hadReflects, data=task1))
  summary(lm(objs==3 ~ hadCodeHints * hadTextHints + hadReflects, data=task1))
  
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
  summary(aov(mRating ~ codeHint * textHint + reflect, data=task1Users))
  anova(aov(mRating ~ codeHint + textHint + reflect, data=task1Users), aov(mRating ~ codeHint * textHint + reflect, data=task1Users))
  
  # Pretty similar results when randomizing, except smaller effect and reflect may not be bad
  hist(postHelp$Q10[postHelp$assignmentID=="drawTriangles"])
  summary(lm(Q10 ~ codeHint * textHint + reflect + userID, data=postHelp[postHelp$assignmentID=="drawTriangles",]))
  summary(aov(Q10 ~ codeHint * textHint + reflect + Error(userID), data=postHelp[postHelp$assignmentID=="drawTriangles",]))
  
  stepAIC(lm(mRating ~ 1, data=task1Users), scope=list(
    lower=lm(mRating ~ 1, data=task1Users), 
    upper=lm(mRating ~ codeHint * textHint * reflect, data=task1Users)), direction="forward")
  
  postHelp$followedHint <- postHelp$Q14 < 3
  table(postHelp$followedHint, postHelp$textHint, postHelp$assignmentID)
  fisher.test(postHelp$followedHint[postHelp$assignmentID == "polygonMakerSimple"], postHelp$textHint[postHelp$assignmentID == "polygonMakerSimple"])
  
  
  task1UF <- postHelpUF[postHelpUF$assignmentID=="polygonMakerSimple",]
  task1UF <- task1UF[task1UF$userID %in% consentUF$userID,]
  started <- ddply(task1UF, c("assignmentID", "userID", "codeHint", "textHint", "reflect"), summarize, n=length(codeHint))
  table(task1UF$codeHint, task1UF$textHint, task1UF$assignmentID)
  
  table(postHelp$codeHint, postHelp$textHint, postHelp$assignmentID)
  
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
  
  write.csv(post1[,lapply(post1, class) != "numeric"][,-1:-13], "C:/Users/Thomas/Desktop/post1-filtered-50.csv")
  write.csv(post2[,lapply(post2, class) != "numeric"][,-1:-13], "C:/Users/Thomas/Desktop/post2-filtered-50.csv")
  
  postHelp$minute <- floor(postHelp$time / 60000)
  ddply(postHelp, c("assignmentID", "codeHint", "textHint", "reflect"), summarize,
        n=length(Q10), spear=cor(Q10, minute, method="spearman"), p=cor.test(Q10, minute, method="spearman")$p.value)
  ddply(postHelp, c("assignmentID"), summarize,
        n=length(Q10), spear=cor(Q10, minute, method="spearman"), p=cor.test(Q10, minute, method="spearman")$p.value)
  
  postHelp$helpNeeded <- preHelp$Q6
  cor.test(postHelp$Q10, postHelp$helpNeeded, method="spearman")
  postHelp$helpNeededBinned <- cut(postHelp$helpNeeded, 4) # as.ordered(floor(5 * postHelp$helpNeeded / 11))
  ddply(postHelp, c("assignmentID", "codeHint", "textHint", "reflect"), summarize,
        n=length(Q10), spear=cor(Q10, helpNeeded, method="spearman"), p=cor.test(Q10, helpNeeded, method="spearman")$p.value)
  table(postHelp$helpNeededBinned)
  
  postHelp$anyHint <- postHelp$codeHint | postHelp$textHint
  postHelp$group <- paste0(postHelp$codeHint, postHelp$textHint)
  ggplot(postHelp, aes(y=Q10, x=group)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + 
    facet_wrap(~assignmentID)
  ggplot(postHelp, aes(x=helpNeededBinned, y=Q10, fill=group)) + geom_boxplot() + facet_wrap(~assignmentID)
  
  
  ratingsBinned <- ddply(postHelp[,c("assignmentID", "codeHint", "textHint", "reflect", "helpNeededBinned", "Q10")], 
                         c("assignmentID", "codeHint", "textHint", "reflect", "helpNeededBinned"), summarize,
                         nc=length(Q10), mRating=mean(Q10), sdRating=sd(Q10))
  ratingsBinned$group <- paste0(ratingsBinned$codeHint, ratingsBinned$textHint, ratingsBinned$reflect)
  ggplot(ratingsBinned, aes(x=helpNeededBinned, y=mRating, group=codeHint, color=codeHint)) + geom_line() + facet_wrap(~assignmentID)
}


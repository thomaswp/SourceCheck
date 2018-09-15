library(plyr)
library(ggplot2)
library(car)

source("../Hints Comparison/util.R")

# Use Type III ANOVA..?
options(contrasts = c("contr.sum", "contr.poly"))

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
  data$StartDate <- as.POSIXct(strptime(as.character(data$StartDate), "%Y-%m-%d %H:%M:%S"))
  data$EndDate <- as.POSIXct(strptime(as.character(data$EndDate), "%Y-%m-%d %H:%M:%S"))
  data <- data[data$StartDate > as.POSIXct(strptime("2018-09-05 00:00:00", "%Y-%m-%d %H:%M:%S")),]
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
  attempts <- attempts[!(attempts$userID %in% attempts$userID[duplicated(attempts[,c("assignmentID", "userID")])]),]
  actions <- actionsUF
  actions <- actions[actions$projectID %in% attempts$projectID,]
  
  users <- Reduce(intersect, list(
    consent$userID, 
    post1$userID, post2$userID, preHelp$userID, postHelp$userID, 
    attempts$userID[attempts$assignmentID == "polygonMakerSimple"],
    attempts$userID[attempts$assignmentID == "drawTriangles"],
    actions$userID
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
  
  task2$t1CodeHints <- task1$codeHints
  task2$t1TextHints <- task1$textHints
  task2$t1Reflects <- task1$reflects
  task2$t1HadCodeHints <- task2$t1CodeHints > 0
  task2$t1HadTextHints <- task2$t1TextHints > 0
  task2$t1HadReflects <- task2$t1Reflects > 0
  
  # Additional calculations
  postHelp$groupCT <- paste0(postHelp$codeHint, postHelp$textHint)
  postHelp$taskName <- ifelse(postHelp$assignmentID=="polygonMakerSimple", "Task 1", "Task 2")
  task1Users <- ddply(postHelp[postHelp$assignmentID=="polygonMakerSimple",], 
                      c("userID", "assignmentID", "codeHint", "textHint", "reflect", "groupCT"), 
                      summarize, mRating=mean(Q10), nRating=length(Q10))
  
  post1$groupCT <- paste0(post1$codeHint, post1$textHint)
  
  task1$groupCT <- paste0(sign(task1$codeHints), sign(task1$textHints))
  
  
  
  task2Hints <- ddply(actions[actions$assignmentID=="drawTriangles",], c("assignmentID", "userID"), summarize,
                      nCodeHints=sum(codeHint), nTextHints=sum(textHint), nReflects=sum(reflect),
                      nCodeHintOnly=sum(codeHint&!textHint), nTextHintOnly=sum(!codeHint&textHint), nBothHints=sum(codeHint&textHint))
  task2 <- merge(task2, task2Hints)
  
  ### Hint per user
  postHelpPerUser <- ddply(postHelp, c("assignmentID", "userID"), summarize, n=length(Q10))
  table(postHelpPerUser$assignmentID, postHelpPerUser$n)
  mean(postHelpPerUser$n[postHelpPerUser$assignmentID=="polygonMakerSimple"])
  mean(postHelpPerUser$n[postHelpPerUser$assignmentID=="drawTriangles"])
  
  #### How does hint type impact perceived usefulness?
  
  ### After the task
  
  ## Task 1
  
  # Code hints improved Snap's helpfulness, but not text or interraction
  Anova(aov(Q22_1 ~ codeHint * textHint + reflect, data=post1), type=3)
  # Nothing impacted difficulty
  Anova(aov(Q24_1 ~ codeHint * textHint + reflect, data=post1), type=3)
  # Reflective prompts may have impacted perceived preparedness (NS)
  Anova(aov(Q26_1 ~ codeHint * textHint + reflect, data=post1), type=3)
  
  # Code hints and text hints improved the utility of the action
  Anova(aov(Q30 ~ codeHint * textHint + reflect, data=post1), type=3)
  
  
  # Seems all help is useful and somewhat additive
  ggplot(post1, aes(y=Q30-1, x=groupCT)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) +
    scale_x_discrete(labels=c("None", "Text", "Code", "Code+Text")) +
    labs(x="Hint Type", y="User Rating")
  
  # code == text
  compareStats(post1$Q30[post1$groupCT=="10"], post1$Q30[post1$groupCT=="01"])
  # code < code + text
  compareStats(post1$Q30[post1$groupCT=="10"], post1$Q30[post1$groupCT=="11"])
  # text < code + text
  compareStats(post1$Q30[post1$groupCT=="01"], post1$Q30[post1$groupCT=="11"])
  
  # No impact of code hint on frequency appropraiteness
  Anova(aov(Q32 ~ codeHint * textHint + reflect, data=post1), type=3)
  ggplot(post1, aes(y=Q32-6, x=groupCT)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  
  # But a real impact on how well-time it was, with code hints improving significantly
  Anova(aov(Q36 ~ codeHint * textHint + reflect, data=post1), type=3)
  ggplot(post1, aes(y=Q36-1, x=groupCT)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  
  ## Task 2
  
  q30Cols <- c("userID", "Q30_Q30_1","Q30_Q30_3", "Q30_Q30_2", "Q30_Q30_4")
  for (col in q30Cols) post2[,col] <- ifelse(post2[,col] == 12, NA, post2[,col])
  # How helpful was each help type?
  post2Q30 <- melt(post2[,q30Cols], id.vars = "userID")
  hist(post2Q30$value)
  # Clear differences
  ggplot(post2Q30, aes(y=value-1, x=variable)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) +
    scale_x_discrete(labels=c("None", "Text", "Code", "Reflect")) +
      labs(x="Hint Type", y="User Rating")
  
  kruskal.test(value~variable, data=post2Q30)
  
  wilcoxSignRank <- function(x, y) wilcox.test(x, y, paired=T)
  # No help < reflect < text hint < code hint
  compareStats(post2$Q30_Q30_1, post2$Q30_Q30_4, test=wilcoxSignRank)
  compareStats(post2$Q30_Q30_4, post2$Q30_Q30_3, test=wilcoxSignRank)
  compareStats(post2$Q30_Q30_3, post2$Q30_Q30_2, test=wilcoxSignRank)
  
  # If Snap could only do one?
  post2Q32 <- melt(post2[,c("userID", "Q32_Q32_1", "Q32_Q32_2", "Q32_Q32_3", "Q32_Q32_4")], id.vars = "userID")
  hist(post2Q32$value)
  # Clear differences
  ggplot(post2Q32, aes(y=value-1, x=variable)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  
  # No help < reflect < text < code
  compareStats(post2$Q32_Q32_1, post2$Q32_Q32_4, test=wilcoxSignRank)
  compareStats(post2$Q32_Q32_4, post2$Q32_Q32_3, test=wilcoxSignRank)
  compareStats(post2$Q32_Q32_3, post2$Q32_Q32_2, test=wilcoxSignRank)
  
  # How helpful was this help (in the loop)
  post2Q37 <- melt(post2[,c("userID", "X1_Q37", "X2_Q37", "X3_Q37", "X4_Q37")], id.vars = "userID")
  ggplot(post2Q37, aes(y=value-1, x=variable)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  # No action = reflects < text < code
  compareStats(post2$X1_Q37, post2$X4_Q37, test=wilcoxSignRank)
  compareStats(post2$X4_Q37, post2$X3_Q37, test=wilcoxSignRank)
  compareStats(post2$X3_Q37, post2$X2_Q37, test=wilcoxSignRank)
  
  post2Q38 <- melt(post2[,c("userID", "X1_Q38", "X2_Q38", "X3_Q38", "X4_Q38")], id.vars = "userID")
  ggplot(post2Q38, aes(y=value-6, x=variable)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  ggplot(post2Q38, aes(y=abs(6-value), x=variable)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  kruskal.test(value~variable, post2Q38)
  
  post2Q40 <- melt(post2[,c("userID", "X1_Q40", "X2_Q40", "X3_Q40", "X4_Q40")], id.vars = "userID")
  ggplot(post2Q40, aes(y=value-1, x=variable)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE)
  # No action < reflects < text < code
  compareStats(post2$X1_Q40, post2$X4_Q40, test=wilcoxSignRank)
  compareStats(post2$X4_Q40, post2$X3_Q40, test=wilcoxSignRank)
  compareStats(post2$X3_Q40, post2$X2_Q40, test=wilcoxSignRank)
  
  # Most people thought text hints helped explain code hints...
  hist(post2$Q46_1[post2$Q46_1!=12])
  summary(post2$Q46_1[post2$Q46_1!=12])
  mean(post2$Q46_1[post2$Q46_1!=12], na.rm=T)
  sd(post2$Q46_1[post2$Q46_1!=12], na.rm=T)
  mean(post2$Q46_1==12, na.rm=T)
  
  ### Immediately after help?
  
  ## Plots
  
  # Seems both > code >? text > nothing
  ggplot(postHelp, aes(y=Q10, x=groupCT)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) +
    scale_x_discrete(labels=c("None", "Text", "Code", "Code+Text")) +
    labs(x="Hint Type", y="User Rating") +
    facet_wrap(~taskName) + theme_bw()
  
  ggplot(postHelp[postHelp$groupCT != "00",], aes(y=Q10, x=reflect==1)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) +
    facet_wrap(~assignmentID) + theme_bw()
  
  ## Task 1
  
  hist(postHelp$Q10[postHelp$assignmentID=="polygonMakerSimple"])
  # Averaging over users, it looks like code and text hints are good, and relatively independent; reflections are bad
  hist(task1Users$mRating)
  Anova(aov(mRating ~ codeHint * textHint + reflect, data=task1Users), type=3)
  
  # code == text
  compareStats(task1Users$mRating[task1Users$groupCT=="10"], task1Users$mRating[task1Users$groupCT=="01"])
  # code == code + text
  compareStats(task1Users$mRating[task1Users$groupCT=="10"], task1Users$mRating[task1Users$groupCT=="11"])
  # text <= code + text
  compareStats(task1Users$mRating[task1Users$groupCT=="01"], task1Users$mRating[task1Users$groupCT=="11"])
  
  ## Task 2
  postHelpT2 <- postHelp[postHelp$assignmentID=="drawTriangles",]
  postHelpT2$codeHint <- postHelpT2$codeHint == 1
  postHelpT2$textHint <- postHelpT2$textHint == 1
  postHelpT2$reflect <- postHelpT2$reflect == 1
  
  # WRONG: Need to treat CH TH and R as within-subjects, but I think it can't because not every
  # user got every condition
  Anova(aov(Q10 ~ codeHint * textHint + reflect + Error(userID), data=postHelpT2)$Within, type=3)
  # Evidence (I think) suggests that users like code hints better w/ text and vice versa
  Anova(aov(Q10 ~ codeHint + Error(userID), data=postHelpT2[postHelp$textHint==1,])$Within, type=3)
  Anova(aov(Q10 ~ textHint + Error(userID), data=postHelpT2[postHelp$codeHint==1,])$Within, type=3)
  
  task2Users <- ddply(postHelpT2, c("assignmentID", "userID"), summarize, 
                      nCode=sum(codeHint), nText=sum(textHint), nReflect=sum(reflect), 
                      mCode=mean(Q10[codeHint&!textHint]), mText=mean(Q10[textHint&!codeHint]), mBoth=mean(Q10[textHint&codeHint]))
  gotCodeBoth <- task2Users[!is.na(task2Users$mCode) & !is.na(task2Users$mBoth),]
  wilcox.test(gotCodeBoth$mText, gotCodeBoth$mBoth, paired=T)
  gotTextBoth <- task2Users[!is.na(task2Users$mText) & !is.na(task2Users$mBoth),]
  wilcox.test(gotTextBoth$mText, gotTextBoth$mBoth, paired=T)
  
  #### How does help impact outomes?
  
  ### Task 1
  
  hist(task1$objs)
  summary(lm(objs ~ hadCodeHints * hadTextHints + hadReflects, data=task1))
  Anova(aov(objs ~ hadCodeHints * hadTextHints + hadReflects, data=task1), type=3)
  # Significantly more objectives completed on task 1 if you had code hints, moderate effect size
  condCompare(task1$objs, task1$hadCodeHints)
  # No effect at all of text hints overall
  condCompare(task1$objs, task1$hadTextHints)
  # Small, positive, NS effect of reflects
  condCompare(task1$objs, task1$hadReflects, filter=task1$hadCodeHints|task1$hadTextHints)
  summary(lm(objs==3 ~ hadCodeHints * hadTextHints + hadReflects, data=task1))
  
  ### Task 2
  
  hist(task2$objs)
  summary(lm(objs ~ t1HadCodeHints * t1HadTextHints + t1HadReflects, data=task2))
  # Maybe reflects helped people in task 2?
  Anova(aov(objs ~ t1HadCodeHints * t1HadTextHints + t1HadReflects, data=task2), type=3)
  # No effect at all of code or text hints on learning 
  condCompare(task2$objs, task1$hadCodeHints)
  condCompare(task2$objs, task1$hadTextHints)
  condCompare(task2$objs, task1$hadCodeHints|task1$hadTextHints)
  # Moderate effect size of reflects but not _quite_ significant
  condCompare(task2$objs, task1$hadReflects, filter=task1$hadCodeHints|task1$hadTextHints)
  condCompare(task2$objs, task1$hadReflects)
  
  task2HadHints <- task2[task1$hadCodeHints|task1$hadTextHints,]
  # seems to really be a product of code hints now and reflects earlier
  Anova(aov(objs ~ t1HadCodeHints + t1HadTextHints + t1HadReflects + nCodeHints + nTextHints + nReflects, data=task2), type=3)
  # Anova(aov(objs ~ t1HadCodeHints + t1HadTextHints + t1HadReflects + nCodeHintOnly + nTextHintOnly + nBothHints + nReflects, data=task2), type=3)
  
  m0 <- lm(objs ~ 1, data=task2)
  m1 <- lm(objs ~ t1HadCodeHints + t1HadTextHints * t1HadReflects + nCodeHints * nTextHints + nReflects, data=task2)
  Anova(stepAIC(m0, direction="forward", scope = list(lower=m0, upper=m1)))
  
  summary(glm(objs>2 ~ t1HadCodeHints + t1HadTextHints + t1HadReflects + nCodeHints + nTextHints + nReflects, data=task2, family="binomial"))
  
  ## Other analysis
  
  # code > no code
  condCompare(task1Users$mRating, task1Users$codeHint==1)
  # text NS > no text
  condCompare(task1Users$mRating, task1Users$textHint==1)
  # reflect NS < no reflect
  condCompare(task1Users$mRating, task1Users$reflect==1, filter=task1Users$textHint+task1Users$codeHint>0)
  # code NS > no code when text hint
  condCompare(task1Users$mRating, task1Users$codeHint==1, filter=task1Users$textHint==1)
  # text NS > no text when code hint
  condCompare(task1Users$mRating, task1Users$textHint==1, filter=task1Users$codeHint==1)
  
  # text > no text
  condCompare(postHelpT2$Q10, postHelpT2$codeHint==1)
  # code > no code
  condCompare(postHelpT2$Q10, postHelpT2$textHint==1)
  # reflect NS < no reflect
  condCompare(postHelpT2$Q10, postHelpT2$reflect==1, filter=postHelpT2$textHint+postHelpT2$codeHint>0)
  # code > no code when text hint
  condCompare(postHelpT2$Q10, postHelpT2$codeHint==1, filter=postHelpT2$textHint==1)
  # text NS > no text when code hint
  condCompare(postHelpT2$Q10, postHelpT2$textHint==1, filter=postHelpT2$codeHint==1)
  
  
  
  
  ggplot(task1Users, aes(y=mRating, x=reflect==1)) + geom_boxplot() + facet_grid(codeHint ~ textHint==1)
  
  # Pretty similar results when randomizing, except smaller effect and reflect may not be bad
  hist(postHelp$Q10[postHelp$assignmentID=="drawTriangles"])
  summary(lm(Q10 ~ codeHint * textHint + reflect + userID, data=postHelp[postHelp$assignmentID=="drawTriangles",]))
  summary(aov(Q10 ~ codeHint * textHint + reflect + Error(userID/(codeHint * textHint + reflect)), data=postHelp[postHelp$assignmentID=="drawTriangles",]))
  summary(glm(good ~ codeHint * textHint + reflect + userID, family="binomial", data=postHelpT2))
  
  # TODO: Use ANOVA, not wilcox
  
  
  
  
  
  condCompare(postHelpT2$Q10, postHelpT2$codeHint==1, filter=postHelpT2$codeHint+postHelpT2$textHint==1)
  
  postHelp$goodness <- ifelse(postHelp$Q10 == 1, 0, ifelse(postHelp$Q10 < 11, 1, 2))
  
  ggplot(postHelpT2, aes(y=Q10, x=reflect==1)) + geom_boxplot() +
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) +
    facet_grid(codeHint ~ textHint==1)
  ggplot(postHelpT2, aes(y=goodness, x=reflect==1)) + geom_boxplot() +
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) +
    facet_grid(codeHint ~ textHint==1)
  ggplot(postHelpT2, aes(y=Q10)) + geom_boxplot() + facet_grid(codeHint ~ textHint==1)
  
  stepAIC(lm(mRating ~ 1, data=task1Users), scope=list(
    lower=lm(mRating ~ 1, data=task1Users), 
    upper=lm(mRating ~ codeHint * textHint * reflect, data=task1Users)), direction="forward")
  stepAIC(lm(Q10 ~ 1, data=postHelpT2), scope=list(
    lower=lm(Q10 ~ 1, data=postHelpT2), 
    upper=lm(Q10 ~ codeHint * textHint * reflect, data=postHelpT2)), direction="forward")
  
  postHelp$followedHint <- postHelp$Q14 < 3
  table(postHelp$followedHint, postHelp$textHint, postHelp$assignmentID)
  codeHinted <- postHelp[postHelp$codeHint == 1, ]
  # Text hints make you significantly more likely to follow a code hint (2x) on task1
  fisher.test(codeHinted$followedHint[codeHinted$assignmentID == "polygonMakerSimple"], codeHinted$textHint[codeHinted$assignmentID == "polygonMakerSimple"]==1)
  # But NS less likely on task 2... so maybe it's a cumulative effect?
  fisher.test(codeHinted$followedHint[codeHinted$assignmentID == "drawTriangles"], codeHinted$textHint[codeHinted$assignmentID == "drawTriangles"]==1)
  
  # But no difference in percieved quality
  condCompare(codeHinted$Q12_1, codeHinted$textHint==1, filter=codeHinted$assignmentID=="polygonMakerSimple")
  condCompare(codeHinted$Q12_2, codeHinted$textHint==1, filter=codeHinted$assignmentID=="polygonMakerSimple")
  condCompare(codeHinted$Q12_3, codeHinted$textHint==1, filter=codeHinted$assignmentID=="polygonMakerSimple")
  ggplot(postHelp, aes(x=textHint==1, y=Q12_3)) + geom_boxplot() + facet_wrap(~assignmentID)
  
  task1UF <- postHelpUF[postHelpUF$assignmentID=="polygonMakerSimple",]
  task1UF <- task1UF[task1UF$userID %in% consentUF$userID,]
  started <- ddply(task1UF, c("assignmentID", "userID", "codeHint", "textHint", "reflect"), summarize, n=length(codeHint))
  table(task1UF$codeHint, task1UF$textHint, task1UF$assignmentID)
  
  table(postHelp$codeHint, postHelp$textHint, postHelp$assignmentID)
  
  # How helpful was Snap? -- only code hint increased
  summary(lm(Q22_1 ~ codeHint + textHint + reflect, data=post1))
  # How difficult was the task? -- probably no effect (except maybe reflects decrease?)
  summary(lm(Q24_1 ~ codeHint + textHint + reflect, data=post1))
  # How prepared are you? -- probably no effect (except maybe reflects improve?)
  summary(lm(Q26_1 ~ codeHint + textHint + reflect, data=post1))
  
  hist(post1$Q30)
  summary(lm(Q30 ~ codeHint * textHint + reflect, data=post1))
  
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
  
  sink("C:/Users/Thomas/Desktop/poly.txt")
  cat(paste0(as.character(task1$lastCode[(task1$treeSize > 40 | task1$nBasics < 2) & task1$objs < 2]), "\n\n"))
  sink()
  
  post1 <- post1[order(post1$userID),]
  post2 <- post2[order(post2$userID),]
  test <- cbind(post1[,lapply(post1, class) != "numeric"][,-1:-13], post2[,lapply(post2, class) != "numeric"][,-1:-13])
  test <- test[order(post1$codeHint, post1$textHint, post1$reflect, post1$userID),]
  write.csv(test, "C:/Users/Thomas/Desktop/post-filtered-124.csv")
  
  qual <- c("Q34", "Q38", "X1_Q39",	"X1_Q41",	"X2_Q39",	"X2_Q41",	"X3_Q39",	"X3_Q41",	"X4_Q39",	"X4_Q41",	"Q44",	"Q45",	"Q47",	"Q48")
  
  sink("C:/Users/Thomas/Desktop/qual.txt")
  for (col in qual) {
    cat("----------------\n")
    cat(paste("#####", col, "#####"))
    cat("\n")
    for (i in 1:nrow(test)) {
      cat(paste("+", test$userID[i], test$hintTypes[i]))
      cat("\n")
      cat(as.character(test[i,col]))
      cat("\n\n")
    }
    cat("\n")
  }
  sink()
  
  postHelp$minute <- floor(postHelp$time / 60000)
  postHelp$number <- sapply(1:nrow(postHelp), function(i) {
    which(postHelp$eventID[postHelp$assignmentID == postHelp$assignmentID[i] & postHelp$userID == postHelp$userID[i]] == postHelp$eventID[i])
  })
  # Very little correlation between time and helpfulness
  ddply(postHelp, c("assignmentID", "codeHint", "textHint", "reflect"), summarize,
        n=length(Q10), spear=cor(Q10, minute, method="spearman"), p=cor.test(Q10, minute, method="spearman")$p.value)
  ddply(postHelp, c("assignmentID"), summarize,
        n=length(Q10), spear=cor(Q10, minute, method="spearman"), p=cor.test(Q10, minute, method="spearman")$p.value)
  ddply(postHelp, c("assignmentID", "codeHint"), summarize,
        n=length(Q10), spear=cor(Q10, minute, method="spearman"), p=cor.test(Q10, minute, method="spearman")$p.value)
  
  postHelp$helpNeeded <- preHelp$Q6
  # Small correlation between help needed and action utility overall (including no help)
  cor.test(postHelp$Q10, postHelp$helpNeeded, method="spearman")
  ddply(postHelp, c("assignmentID", "codeHint", "textHint", "reflect"), summarize,
        n=length(Q10), spear=cor(Q10, helpNeeded, method="spearman"), p=cor.test(Q10, helpNeeded, method="spearman")$p.value)
  
  # Seems clear that there's interaction between help needed and code hint when predicing help utility
  summary(aov(Q10 ~ codeHint * helpNeeded + Error(userID/assignmentID), data=postHelp))
  cor.test(postHelp$Q10[postHelp$codeHint==1], postHelp$helpNeeded[postHelp$codeHint==1], method="spearman")
  cor.test(postHelp$Q10[postHelp$codeHint==0], postHelp$helpNeeded[postHelp$codeHint==0], method="spearman")
  
  postHelp$helpNeededBinned <- cut(postHelp$helpNeeded, 4) # as.ordered(floor(5 * postHelp$helpNeeded / 11))
  table(postHelp$helpNeededBinned)
  
  postHelp$anyHint <- postHelp$codeHint | postHelp$textHint
  postHelp$group <- paste0(postHelp$codeHint, postHelp$textHint)
  ggplot(postHelp, aes(y=Q10, x=group)) + geom_boxplot() + 
    stat_summary(fun.y=mean, colour="darkred", geom="point", shape=18, size=3,show.legend = FALSE) + 
    facet_wrap(~assignmentID)
  ggplot(postHelp, aes(x=helpNeededBinned, y=Q10, fill=group)) + geom_boxplot() + facet_wrap(~assignmentID)
  
  
  perUser = ddply(postHelpT2, c("assignmentID", "userID"), summarize, 
                  mCode=mean(Q10[codeHint]), mText=mean(Q10[codeHint]), mReflect=mean(Q10[reflect]),
                  nCode=sum(codeHint), nText=sum(textHint), nReflect=sum(reflect))
  
  cor.test(post2$Q30_Q30_2, post2$Q30_Q30_4, method="spearman")
  plot(jitter(post2$Q30_Q30_2), jitter(post2$Q30_Q30_4))
  plot(jitter(post2$Q30_Q30_3), jitter(post2$Q30_Q30_4))
  mean(post2$Q30_Q30_2) - mean(post2$Q30_Q30_4)
  
  cor.test(post2$Q30_Q30_3, post2$Q30_Q30_4, method="spearman")
  summary(aov(Q30_Q30_4 ~ Q30_Q30_1 * Q30_Q30_2 * Q30_Q30_3, data=post2))
  
  ggplot(attempts, aes(y=treeSize, x=factor(objs))) + geom_boxplot() + facet_wrap(~assignmentID)
  ggplot(attempts, aes(y=nSprites, x=factor(objs))) + geom_boxplot() + facet_wrap(~assignmentID)
  ggplot(attempts, aes(y=nScripts, x=factor(objs))) + geom_boxplot() + facet_wrap(~assignmentID)
  ggplot(attempts, aes(y=nUniqueTypes, x=factor(objs))) + geom_boxplot() + facet_wrap(~assignmentID)
}




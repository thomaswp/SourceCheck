library(readr)
library(psych)

loadData <- function() {
  presurvey <- read_csv("data/presurvey_numbers.csv", 
    col_types = cols(Q6 = col_character(), 
      RecordedDate = col_datetime(format = "%m/%d/%Y %H:%M")))
  tutors <- read_csv("data/tutors.csv")
  users <- read_csv("data/users.csv", 
    col_types = cols(
      autoHelpAssignment = col_factor(levels = c("brickWall", "guess1Lab")),
      created = col_character(),
      tutoredAssignment = col_factor(levels = c("brickWall", "guess1Lab"))))
  hints <- read_csv("../../data/help-seeking/fall2016-spring2017/analysis/hints.csv", 
                    col_types = cols(assignment = col_factor(levels = c("brickWall", "guess1Lab"))))
  attempts <- read_csv("../../data/help-seeking/fall2016-spring2017/analysis/attempts.csv",
    col_types = cols(assignment = col_factor(levels = c("brickWall", "guess1Lab"))))
  attempts <- merge(attempts, users, all.x=T, by.x="userID", by.y="id")
  attempts$snapHelp = attempts$autoHelpAssignment == attempts$assignment
}

surveyAttrs <- function() {
  qWeights <- c(rep(1,6),rep(-1,6))
  confQs <- presurvey[,paste0("Q12_", 2:13)]
  attQs <- presurvey[,paste0("Q12_", 14:25)]
  
  # CS Confidence Alpha = 0.96
  alpha(cor(confQs), keys=qWeights)
  # CS Attitude Alpha = 0.80
  alpha(cor(attQs), keys=qWeights)
  
  qAverage <- function(columns, weights) {
    as.vector(t(t(weights) %*%  t(as.matrix(columns))) + 6*6) / length(weights)
  }
  presurvey$confidence <- qAverage(confQs, c(rep(1,6),rep(-1,6)))
  presurvey$attitude <- qAverage(attQs, c(rep(1,6),rep(-1,6)))
  
  mAppQs <- presurvey[,paste0("Q20_", c(1,7,3))]
  mAvQs <- presurvey[,paste0("Q20_", c(5,11,9))]
  pAppQs <- presurvey[,paste0("Q20_", c(4,2,8))]
  pAvQs <- presurvey[,paste0("Q20_", c(12,10,6))]
  
  # Mastery-approach Alpha: 0.81
  alpha(cor(mAppQs))
  # Mastery-avoidance Alpha: 0.73
  alpha(cor(mAvQs))
  # Performance-approach Alpha: 0.81
  alpha(cor(pAppQs))
  # Performance-avoidance Alpha: 0.80
  alpha(cor(pAvQs))
  
  presurvey$mApp <- rowSums(mAppQs) / 3
  presurvey$mAv <- rowSums(mAvQs) / 3
  presurvey$pApp <- rowSums(pAppQs) / 3
  presurvey$pAv <- rowSums(pAvQs) / 3
  
  # Mostly low correlations, except mAv/pAv, pApp/pAv
  cor(presurvey[,c("mApp", "mAv", "pApp", "pAv")])
  
  attributes <- presurvey[,c("id", "confidence", "attitude", "mApp", "mAv", "pApp", "pAv")]
  attempts <- merge(attempts, attributes, all.x=T, by.x = "userID", by.y = "id")
  
  hist(attributes$confidence)
  hist(attributes$attitude)
  hist(attributes$mApp)
  hist(attributes$mAv)
  hist(attributes$pApp)
  hist(attributes$pAv)
}

hunting <- function() {
  sAttempts <- attempts[attempts$snapHelp,]
  medHints <- median(sAttempts$hints)
  sAttempts$highHR <- sAttempts$hints >= medHints
  
  table(sAttempts$assignment, sAttempts$highHR)
  fisher.test(table(sAttempts$assignment, sAttempts$highHR))
}


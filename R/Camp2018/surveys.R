library(readr)

users <- read_csv("data/camp_users.csv")
users <- users[users$hashedUserID != "NULL" & users$tester == 0,]

# Two users had to leave early (one came back) and had to be excluded
exclude <- c(10, 13)
users <- users[!(users$row %in% exclude),]

loadSurvey <- function(users, path) {
  survey <- read_csv(path)
  survey <- survey[,-(10:17)]
  survey <- merge(survey, users, by.x = "UserID", by.y = "loginID")
  survey <- survey[order(survey$UserID),]
  survey
}

preS <- loadSurvey(users, "data/pre-survey-N.csv")
postS <- loadSurvey(users, "data/post-survey-N.csv")
preT <- loadSurvey(users, "data/pre-test-N.csv")
postT <- loadSurvey(users, "data/post-test-N.csv")

# Names match, so they can be deleted soon
# data.frame(preS$Name, postS$Name, preT$Name, postT$Name)

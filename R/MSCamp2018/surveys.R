library(readr)
library(ggplot2)

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

preT$score <- as.integer(preT$SC0)
preT$duration <- as.numeric(preT$`Duration (in seconds)`)
postT$score <- as.integer(postT$SC0)
postT$duration <- as.numeric(postT$`Duration (in seconds)`)

# Names match, so they can be deleted soon
# data.frame(preS$Name, postS$Name, preT$Name, postT$Name)

hist(preT$score)
hist(preT$duration)
hist(postT$score)
hist(postT$duration)

median(preT$score)
median(postT$score)

plot(preT$score, preT$duration)
plot(postT$score, postT$duration)

wilcox.test(preT$duration, postT$duration, paired=T)
mean(preT$duration) - mean(postT$duration)

wilcox.test(preT$score, postT$score, paired=T)

postT$score[preT$score > 5] - preT$score[preT$score > 5]

median(preT$score[preT$proactive])
median(preT$score[!preT$proactive])

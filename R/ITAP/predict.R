library(plyr)
library(MASS)

ddply(performance, c("ProblemID"), summarize, percentCorrect=mean(avg_prior_percent_correct_prob), students=length(unique(SubjectID)))


prob <- performance[performance$ProblemID == 14,]

prob <- prob[,c(4, 8, 10:15)]

simple <- lm(FirstCorrect ~ avg_prior_percent_correct, data=prob)#, family = "binomial")


all <- lm(FirstCorrect ~ ., data=prob) #, family = "binomial")

stepAIC(simple, scope=c("upper" = all, "lower"=simple), direction="forward")

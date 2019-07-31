library(plyr)
library(ggplot2)


survey <- read.csv("C:/Users/twprice/Desktop/LIVE+CA-Hosted+PCRS+Compare-Contrast+[Summer+2019]+-+Copy_July+22,+2019_13.41/LIVE CA-Hosted PCRS Compare-Contrast [Summer 2019] - Copy_July 22, 2019_13.41.csv")

# Correct for weird coding
# survey$Q25 <- pmax(survey$Q25 - 5, 1)

#Overall positive ratings
hist(survey$Q25)

# Plot helpfulness as a function of showing the Compare-Contrast prompt
ggplot(data=survey, aes(y=Q25, x=as.factor(showCC))) + geom_boxplot()
ggplot(data=survey, aes(y=Q25, x=as.factor(showBlanks))) + geom_boxplot()

# No significant effect of condition on perceived usefulness
# (Technically not ok - non-independent ratings from the same student)
summary(aov(Q25 ~ showCC * showBlanks, data=survey))


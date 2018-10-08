library(ggplot2)
library(plyr)

groups <- read.csv("data/hs/user_group.csv")
attempts <- read.csv("../../data/camp/campHS2018/analysis/attempts.csv")

attempts <- attempts[attempts$logs,]
attempts <- merge(attempts, groups, by="userID")

exclude <- c(
  # Came on second day
  "$2y$10$SummerZG9tIHN0YXRpYyBuD10h1lKutDfv2UGdJmFO2XqpaKzrvpy",
  # Changed to DaisyDesign 20 minutes after the demo version
  "$2y$10$SummerZG9tIHN0YXRpYyBuvlseZ1FSsTAyMpfldXkd8T/tOawFI0e"
)

attempts <- attempts[!(attempts$userID %in% exclude),]

counts <- ddply(attempts, c("userID"), summarize, nAssignments=length(active))
attempts <- merge(attempts, counts, by="userID")
attempts <- attempts[attempts$nAssignments == 3,]


ggplot(attempts, aes(x=isGroupA, y=total)) + geom_boxplot() + 
  geom_abline(slope=0, intercept = 2700, color="red") + 
  facet_grid(~ assignment)

ddply(attempts, c("assignment", "isGroupA"), summarize, sd=sd(total))


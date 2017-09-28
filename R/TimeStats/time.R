
library(readr)
library(reshape2)
library(plyr)
library(ggplot2)

se <- function(x) sqrt(var(x, na.rm=T)/sum(!is.na(x)))

spring2016 <- read_csv("~/GitHub/SnapHints/data/csc200/spring2016/analysis/attempts.csv")
fall2016 <- read_csv("~/GitHub/SnapHints/data/csc200/fall2016/analysis/attempts.csv")
spring2017 <- read_csv("~/GitHub/SnapHints/data/csc200/spring2017/analysis/attempts.csv")

all <- rbind(spring2016, fall2016[,1:15], spring2017)
all$assignment <- ordered(all$assignment, c("lightsCameraActionHW", "polygonMakerLab", "squiralHW", "guess1Lab", "guess2HW", "guess3Lab"))
all$total <- all$total / 60
all$active <- all$active / 60

summary <- ddply(all, c("assignment", "dataset"), summarize, 
                 meanTime=mean(total), sdTime=sd(total), medTime=median(total),
                 meanActive=mean(active), sdActive=sd(active), medActive=median(active))

ggplot(all, aes(y=total, x=assignment, color=dataset)) +
  geom_boxplot(position=position_dodge(.85)) +
  scale_y_continuous(limits = c(0,150), name="Minutes") +
  scale_x_discrete(name="Assignment", labels=c("LCA!", "Poly", "Squiral", "GG1", "GG2", "GG3")) +
  scale_color_discrete(name="Semester")

ggplot(all, aes(y=active, x=assignment, color=dataset)) +
  geom_boxplot(position=position_dodge(.85)) +
  scale_y_continuous(limits = c(0,150), name="Minutes") +
  scale_x_discrete(name="Assignment", labels=c("LCA!", "Poly", "Squiral", "GG1", "GG2", "GG3")) +
  scale_color_discrete(name="Semester")

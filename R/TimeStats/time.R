
library(readr)
library(reshape2)
library(plyr)
library(ggplot2)

se <- function(x) sqrt(var(x, na.rm=T)/sum(!is.na(x)))

spring2016 <- read_csv("../../data/csc200/spring2016/analysis/attempts.csv")
fall2016 <- read_csv("../../data/csc200/fall2016/analysis/attempts.csv")
spring2017 <- read_csv("../../data/csc200/spring2017/analysis/attempts.csv")

all <- rbind(spring2016, fall2016[,1:15], spring2017)
assignments <- c("lightsCameraActionHW", "polygonMakerLab", "squiralHW", "guess1Lab", "guess2HW", "guess3Lab")
all$assignment <- ordered(all$assignment, assignments)
all$dataset <- ordered(all$dataset, c("Spring2016", "Fall2016", "Spring2017"))
all$total <- all$total / 60
all$active <- all$active / 60

summary <- ddply(all, c("assignment", "dataset"), summarize, 
                 meanTime=mean(total), sdTime=sd(total), medTime=median(total),
                 meanActive=mean(active), sdActive=sd(active), medActive=median(active))
write.csv(summary, "C:/Users/Thomas/Desktop/time.csv")

ggplot(all, aes(y=total, x=assignment, color=dataset)) +
  geom_boxplot(position=position_dodge(.85)) +
  scale_y_continuous(limits = c(0,150), name="Minutes") +
  scale_x_discrete(name="Assignment", labels=c("LCA!", "Poly", "Squiral", "GG1", "GG2", "GG3")) +
  scale_color_discrete(name="Semester") +
  ggtitle("Time spent per Assignment")

ggplot(all, aes(y=active, x=assignment, color=dataset)) +
  geom_boxplot(position=position_dodge(.85)) +
  scale_y_continuous(limits = c(0,150), name="Minutes") +
  scale_x_discrete(name="Assignment", labels=c("LCA!", "Poly", "Squiral", "GG1", "GG2", "GG3")) +
  scale_color_discrete(name="Semester")



gradesSpring2016 <- read_csv("../Hints Comparison/grades/spring2016.csv")
gradesFall2016 <- read_csv("../Hints Comparison/grades/fall2016.csv")
gradesSpring2017 <- read_csv("../Hints Comparison/grades/spring2017.csv")

gradesSpring2016$dataset <- "Spring2016"
gradesFall2016$dataset <- "Fall2016"
gradesSpring2017$dataset <- "Spring2017"

grades <- rbind(gradesSpring2016, gradesFall2016, gradesSpring2017)
grades$dataset <- ordered(grades$dataset, c("Spring2016", "Fall2016", "Spring2017"))
grades$id <- 1:nrow(grades)

snapGrades <- grades[c(assignments, "id", "dataset")]
for (a in assignments) {
  snapGrades[,a] <- 100 * snapGrades[,a] / max(snapGrades[,a])
}

gradesMelted <- melt(snapGrades, id=c("id", "dataset"))
gradesMelted$variable <- ordered(gradesMelted$variable, assignments)

ggplot(gradesMelted, aes(y=value, x=variable, color=dataset)) +
  geom_boxplot(position=position_dodge(.85)) +
  scale_y_continuous(name="Grade") +
  scale_x_discrete(name="Assignment", labels=c("LCA!", "Poly", "Squiral", "GG1", "GG2", "GG3")) +
  scale_color_discrete(name="Semester") +
  ggtitle("Grade Distributions")

gradesSummary <- ddply(gradesMelted, c("variable", "dataset"), summarize, 
                 mean=mean(value), sd=sd(value), perc80=mean(value<80), perc60=mean(value<60))
write.csv(gradesSummary, "C:/Users/Thomas/Desktop/grades.csv")

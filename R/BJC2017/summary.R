library(plyr)
library(readr)
library(ggplot2)

as.udate <- function(x) as.POSIXct(x, origin="1970-01-01")
se = function(x) sd(x) / sqrt(length(x))

summary <- read_csv("data/summary.csv", 
  col_types = cols(
    classroom = col_factor(levels = c("Palooza_1", "Palooza_2", "Palooza_3", "Palooza_4")), 
    minutes = col_integer(), start = col_character()))
summary$hints = summary$hints == 1
summary$assignmentID <- ordered(summary$assignmentID, .(
    U1_L1_Alonzo, U1_L2_Gossip, U1_L2_P4_GreetPlayer, U1_L3_P1_Experiments,
    U1_L3_Pinwheel, U1_L3_P6_Looping, U1_L3_P7_Graphics, U1_P3_Pong,
    U2_L1_GuessingGame, U2_L1_P3_Alonzo, U2_L3_Predicates, U2_L3_P2_KeepingData,
    U2_L3_P3_WordPuzzleSolver, U2_L4_BrickWall, 
    # F2F
    U1_L3_P6_NestSquares, A1_eCard, U3_L1_ContactList, U5_L1_Search, U5_L1_P2_ImprovedSearch,
    U3_L2_P3_Sorting, U5_L2_Models, U5_L3_P3_DiseaseSpread, A2_ShoppingList,
    U3_L5_P1_Graphs, P1_Create
))
summary <- summary[!is.na(summary$assignmentID),]

projects <- ddply(summary, .(assignmentID, classroom, hints, userID), summarise,
  mi=head(which(minutes == max(minutes)), 1),
  projectID=projectID[mi], start=start[mi], meanMinute=meanMinute[mi], minutes=minutes[mi])

projects$start <- as.numeric(strptime(projects$start, "%Y-%m-%d %H:%M:%S"))
projects$meanMinute <- as.numeric(strptime(projects$meanMinute, "%Y-%m-%d %H:%M:%S"))

pdStart <- as.numeric(strptime('2017-07-17 06:00:00', "%Y-%m-%d %H:%M:%S"))

users <- ddply(projects, .(classroom, hints, userID), summarize,
  assignments=length(projectID), 
  prePDAssignments=sum(start < pdStart & minutes >= 10),
  totalMinutes=sum(minutes),
  startWork=min(start[minutes >= 10]))

projects <- merge(projects, users[,c("userID", "startWork")], by="userID", all.x = T)
projects$startS <- projects$start - projects$startWork
projects$startF <- pdStart - projects$start

projects <- projects[projects$minutes >= 5 & projects$startS >= 0, ]
prePD <- projects[projects$startF >= 0,]

as.date

ggplot(users, aes(y=as.udate(startWork), x=classroom)) + geom_violin() + geom_boxplot(width=0.1) + geom_abline(slope=0,intercept=pdStart)

ggplot(prePD, aes(y=startS, x=assignmentID, group=userID, color=classroom)) + geom_line() + geom_point(aes(size=minutes, shape=hints))
ggplot(prePD, aes(y=-startF, x=assignmentID, group=userID, color=classroom)) + geom_line() + geom_point(aes(size=minutes, shape=hints))

classesPPD <- ddply(prePD, .(assignmentID, classroom, hints), summarize,
  n=length(projectID),
  meanStart=mean(start), seStart=se(start),
  meanStartS=mean(startS), seStartS=se(startS),
  meanStartF=mean(startF), seStartF=se(startF),
  meanMinutes=mean(minutes), seMinutes=se(minutes))

countsPPD <- ddply(prePD, .(classroom), summarize, total=length(unique(userID)))

classesPPD <- merge(classesPPD, countsPPD, by="classroom", all.x=T)
classesPPD$perc <- classesPPD$n / classesPPD$total

ggplot(classesPPD, aes(y=meanStartS, x=assignmentID, group=classroom, color=classroom)) + geom_line() + geom_point(aes(size=perc)) + 
  geom_errorbar(aes(ymin=meanStartS-seStartS, ymax=meanStartS+seStartS), width=0.2)

ggplot(classesPPD, aes(y=meanStartF, x=assignmentID, group=classroom, color=classroom)) + geom_line() + geom_point(aes(size=perc)) + 
  geom_errorbar(aes(ymin=meanStartF-seStartF, ymax=meanStartF+seStartF), width=0.2)

ggplot(classesPPD, aes(y=perc, x=assignmentID, group=classroom, color=classroom)) + geom_line()

ggplot(classesPPD, aes(y=meanMinutes, x=assignmentID, fill=classroom, alpha=perc)) + geom_bar(stat="identity", position=position_dodge(), color='black') +
  geom_errorbar(aes(ymin=meanMinutes-seMinutes, ymax=meanMinutes+seMinutes), width=0.2, position=position_dodge(.9))

alonzo <- projects[prePD$assignmentID == "U1_L1_Alonzo",]
gossip <- projects[prePD$assignmentID == "U1_L2_Gossip",]

ggplot(gossip, aes(y=startS, x=classroom)) + geom_violin() + geom_boxplot(width=0.1)

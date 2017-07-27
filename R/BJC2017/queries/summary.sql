SELECT userID, hints, classroom, assignmentID, projectID, 
	MIN(time) AS start,
	FROM_UNIXTIME(ROUND(AVG(DISTINCT FLOOR(UNIX_TIMESTAMP(time) / 60))) * 60) AS meanMinute,
	COUNT(DISTINCT FLOOR(UNIX_TIMESTAMP(time) / 60)) AS minutes
FROM `trace` JOIN (
	SELECT edxID, hints, classroom FROM pd_users WHERE classroom LIKE 'Palooza%' AND bjcConsent=1 AND isParticipant=1    
) AS users ON users.edxID = trace.userID
GROUP BY userID, assignmentID, projectID, hints, classroom
HAVING minutes > 2 AND projectID <> ""
ORDER BY assignmentID, classroom, userID, projectID
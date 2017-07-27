SELECT userID, hints, classroom, assignmentID, projectID, MIN(time) AS start, COUNT(DISTINCT FLOOR(TO_SECONDS(time) / 60)) AS minutes
FROM `trace` JOIN (
	SELECT edxID, hints, classroom FROM pd_users WHERE classroom LIKE 'Palooza%' AND bjcConsent=1 AND isParticipant=1    
) AS users ON users.edxID = trace.userID
GROUP BY userID, assignmentID, projectID, hints, classroom
HAVING minutes > 2 AND projectID <> ""
ORDER BY assignmentID, classroom, userID, projectID
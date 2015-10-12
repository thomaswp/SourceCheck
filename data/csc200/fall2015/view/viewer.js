
function generateContent(data) {
	var content = document.getElementById("content");
	for (var student in data) {
		if (!data.hasOwnProperty(student)) continue;
				
		var div = document.createElement("div");
		content.appendChild(div);
				
		var html = "<h3>" + student + "</h3>";
		
		var rows = data[student];
		html += "<table>";
		for (var i = 0; i < rows.length; i++) {
			var row = rows[i];
			var hints = row.hints;
			if (row.from == row.to && hints.length == 0) continue;
			for (var j = 0; j < hints.length; j++) {
				html += "<tr>";
				if (j == 0) {
					html += "<td rowspan='" + hints.length + "'><div style='width: 400px'>";
					html += createDiff(row.from, row.to);
					html += "<div></td>";
				}
				var hint = hints[j];
				html += "<td>" + createDiff(hint.from, hint.to) + "</td>";
				var hintClass = hint.accepted ? "plus" : "minus";
				html += "<td><div style='width: 200px' class='" + hintClass + "'>" + hint.status + "</div></td>";
				html += "</tr>"
			}
		}
		html += "</table>";
		
		div.innerHTML = html;
	}
}

function createDiff(from, to) {
	var cssMap = {
		"+": "plus",
		"=": "equals",
		"-": "minus",
	};
	var matchRegex = /:|\[|\]|,|\s|\w*/g;
	var code0 = from.match(matchRegex);
	var code1 = to.match(matchRegex);
	var codeDiff = window.diff(code0, code1);
	var html = "<span class='hint'>";
	for (var j = 0; j < codeDiff.length; j++) {
		var block = cssMap[codeDiff[j][0]];
		var code = codeDiff[j][1].join("");
		if (block == "equals" && code.length > 50) {
			code = code.substring(0, 25) + "..." + code.substring(code.length - 25, code.length);
		}
		html += "<code class={0}>{1}</code>".format(block, code);
	}
	html += "</span>";
	return html;
}

// credit: http://stackoverflow.com/a/4673436
if (!String.prototype.format) {
  String.prototype.format = function() {
    var args = arguments;
    return this.replace(/{(\d+)}/g, function(match, number) { 
      return typeof args[number] != 'undefined'
        ? args[number]
        : match
      ;
    });
  };
}

if (window.hintData != null) {
	generateContent(window.hintData);
}
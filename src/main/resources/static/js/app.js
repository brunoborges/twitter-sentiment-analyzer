window.onload = function () {
    console.log("Protocol: " + location.protocol);
    var wsURL = "ws://" + location.hostname + ":8088/tweets"
    if (location.protocol == 'https:') {
        wsURL = "wss://" + location.hostname + ":8088/tweets"
    }
    console.log("WS URL: " + wsURL);

    var log = document.getElementById("tweets");

    function appendLog(item) {
        var doScroll = log.scrollTop > log.scrollHeight - log.clientHeight - 1;
        log.appendChild(item);
        if (doScroll) {
            log.scrollTop = log.scrollHeight - log.clientHeight;
        }
    }

    if (log) {
        sock = new WebSocket(wsURL);

        var connDiv = document.getElementById("connection-status");
        connDiv.innerText = "closed";

        sock.onopen = function () {
            console.log("connected to " + wsURL);
            connDiv.innerText = "open";
        };

        sock.onclose = function (e) {
            console.log("connection closed (" + e.code + ")");
            connDiv.innerText = "closed";
        };

        sock.onmessage = function (e) {
            console.log(e);
            var t = JSON.parse(e.data);
            console.log(t);

            var scoreStr = "neutral";
            var scoreAlt = "neutral: 0"
            scoreStr = t.sentiment.sentiment;
            scoreAlt = scoreStr + ": " + t.sentiment.sentimentScore;

            var tweetText = t.status.text.replace(/(http:\/\/[^\s]+)/g, "<a href='$1'>$1</a>");

            var item = document.createElement("div");
            item.className = "item";
            var postURL = t.status.user.screenName +
                "<a href='https://twitter.com/" + t.status.user.screenName + "/status/" + t.status.id +
                "' target='_blank'><img src='img/tw.svg' class='tweet-link' /></a></b>";

            var tmsg = "<img src='" + t.status.user.profileImageURL + "' class='profile-pic' />" +
                "<div class='item-text'><b><img src='img/" + scoreStr +
                ".svg' title='" + scoreAlt + "' class='sentiment' />" + postURL +
                "<br /><i>" + tweetText + "</i></div>";
            item.innerHTML = tmsg
            appendLog(item);
        };
    }

    searchBox = document.getElementById("search-keyword");
    searchBox.addEventListener("keyup", function (event) {
        if (event.key === 'Enter') {
            searchTerm = searchBox.value;
            event.preventDefault();
            console.log("searching for " + searchTerm);
            jsonData = {
                "command": "search",
                "searchTerms": searchTerm
            };
            sock.send(JSON.stringify(jsonData));
            searchBox.value = "";
            searchBox.placeholder = searchTerm;
        }
    });
};

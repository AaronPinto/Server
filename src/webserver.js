const http = require("http");
const http2 = require("http2");
const {readFileSync} = require("fs");

http2.createSecureServer({
    key: readFileSync("localhost-privkey.pem"),
    cert: readFileSync("localhost-cert.pem")
}, requestListener)
        .on("error", err => console.error(err))
        .on("secureConnection", socket => socket.on("error", (err) => console.error(err)))
        .listen(443, () => console.log(`${getLocalDateTime()} Server running on port 443`));

http.createServer(requestListener)
        .on("error", err => console.error(err))
        .listen(80, () => console.log(`${getLocalDateTime()} Server running on port 80`));

function requestListener(req, res) {
    const {httpVersion, method, socket, url} = req;
    const {remoteAddress} = socket;

    res.on("finish", () => {
        console.log(`${getLocalDateTime()} ${res.statusCode} Received ${method} request from ${remoteAddress} using HTTP ${httpVersion} for URL ${url}`);
    });

    res.writeHead(301, {"Location": "https://aaronslab.xyz" + url});
    res.end();
}

// https://stackoverflow.com/a/51643788/6713362
function getLocalDateTime() {
    const currDate = new Date();
    return new Date(currDate - currDate.getTimezoneOffset() * 60000).toISOString().slice(0, 23);
}

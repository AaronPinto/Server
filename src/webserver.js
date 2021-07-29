const http = require("http");

const server = http.createServer((req, res) => {
    const {httpVersion, method, socket, url} = req;
    const {remoteAddress} = socket;

    res.on("finish", () => {
        console.log(`${getLocalDateTime()} ${res.statusCode} Received ${method} request from ${remoteAddress} using HTTP ${httpVersion} for URL ${url}`);
    });

    res.writeHead(301, {"Location": "https://aaronslab.xyz" + url});
    res.end();
});

server.listen(443, () => {
    console.log(`${getLocalDateTime()} Server running on port 443`);
});

server.listen(80, () => {
    console.log(`${getLocalDateTime()} Server running on port 80`);
});

// https://stackoverflow.com/a/51643788/6713362
function getLocalDateTime() {
    const currDate = new Date();
    return new Date(currDate - currDate.getTimezoneOffset() * 60000).toISOString().slice(0, 23);
}

const fs = require("fs");
const http = require("http");
const http2 = require("http2");

const port = 443;
const key = fs.readFileSync("localhost-privkey.pem");
const cert = fs.readFileSync("localhost-cert.pem");
const data = fs.readFileSync("index.html");

const server = http2.createSecureServer({key, cert, allowHTTP1: true}, (req, res) => {
    const {httpVersion, method, socket, url} = req;
    const {remoteAddress} = socket;
    const {socket: {alpnProtocol}} = req.httpVersion === "2.0" ? req.stream.session : req;

    res.on("finish", () => {
        console.log(`${getLocalDateTime()} Received ${method} request from ${remoteAddress} using HTTP ${httpVersion} and ${alpnProtocol} for URL ${url}`);
    });

    res.writeHead(200, {"content-type": "text/html; charset=utf-8"});
    res.end(data);
});
server.on("error", err => console.error(err));

server.listen(port, () => {
    console.log(`${getLocalDateTime()} Server running at https://127.0.0.1:${port}/`);
});

// Redirect requests on port 80 to 443
http.createServer((req, res) => {
    const {httpVersion, method, socket, url} = req;
    const {remoteAddress} = socket;

    res.on("finish", () => {
        console.log(`${getLocalDateTime()} Received ${method} request from ${remoteAddress} using HTTP ${httpVersion} for URL ${url}`);
    });

    res.writeHead(301, {"Location": "https://" + req.headers.host + req.url});
    res.end();
}).listen(80);

// https://stackoverflow.com/a/51643788/6713362
function getLocalDateTime() {
    const currDate = new Date();
    return new Date(currDate - currDate.getTimezoneOffset() * 60000).toISOString().slice(0, 23);
}

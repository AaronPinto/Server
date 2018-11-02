const http = require('http'), fs = require('fs'), mime = require('mime');

http.createServer(function (req, res) {
    let path = decodeURI(req.url);
    fs.readFile("A&W" + path, (err, data) => {
        if (err) {
            console.log(err);
            res.writeHead(404, {'Content-Type': 'text/html'});
        } else {
            res.writeHead(200, {'Content-Type': mime.getType(path)});
            res.write(data);
        }
        res.end();
    });
}).listen(80);
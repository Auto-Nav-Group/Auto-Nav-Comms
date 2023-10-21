const http = require("http");

const host = 'localhost';
const port = 8080;

const server = http.createServer(function(req, res) {
    console.dir(req.param);
    
    if (req.method == `POST`) {
        console.log(`POST`);
        var body = '';
        req.on('data', function(data) {
            body += data;
            console.log(`Partial body: ${body}`);
        })
        req.on('end', function() {
            console.log(`Body: ${body}`);
            res.writeHead(200, {'Content-Type': 'application/json'});
            let jsonObj = {status: "Recieved", body: "Recieved request"};
            res.end(JSON.stringify(jsonObj));
        })
    }
})

server.listen(port, host);
console.log(`Listening at http://${host}:${port}`);

let jsonString = "{\"name\":\"name2\",\"body\":\"body2\"}";

fetch("http://localhost:3000", { //TODO: Fix the server not recieving the json
    method: "POST",
    headers: {
        "Content-Type": "application/json; charset=UTF-8"
    },
    body: jsonString 
})
.then((response) => response.json())
.then((json) => console.log(json))
.catch((error) => console.error("Error:", error));

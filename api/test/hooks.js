var hooks = require('hooks');
var http = require("http");


function get() {
    return new Promise(((resolve, reject) => {
        var request =  http.get({
            hostname: 'localhost',
            port: 8080,
            path: '/is_ready'
        }, (res) => {
            console.log(res.statusCode);
            resolve(res.statusCode);
        });
        request.on("error", () => {
            resolve(0);
        });
    }))
}

async function isonline(done) {
    var d = new Date().getTime();
    var status = 0;
    do {
        status = await get();
    } while (status !== 200 && new Date().getTime() - d < 30_000);
    done();
}

hooks.beforeAll(async function (transactions, done) {
    hooks.log('before all');
    await isonline(done);
});

hooks.beforeEach(function(transaction) {
    if (transaction.expected.headers['Content-Type'] === 'application/json') {
        transaction.expected.headers['Content-Type'] = 'application/json; charset=UTF-8';
    }
});

hooks.before('/api/v2/sykmeldinger > V2 av sykmeldinger api, returnerer liste av sykmeldinger for fnr i access_token > 200 > application/json', function (transaction) {
    transaction.fullPath = transaction.fullPath.replace("&include=APEN", "")
});








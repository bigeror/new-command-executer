// easiest way to interact with client is through net
const net = require("net");

// sleep function to add delays
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// example loop function
/** @param {net.Socket} client */
const tick = async (client) => {
  let loop = 0;

  while (true) {
    loop++;
    // send command to execute to the client
    client.write(
      `say ${loop}\n`
    );
    // execute command every tick
    await sleep(50);
  }
};

// create net server
const server = net.createServer((client) => {
  console.log("client connected");
  // start loop function
  const promise = new Promise(() => tick(client));

  // handle errors (on client disconnecting this will be called because of loop function)
  client.on("error", () => {});

  // code on client disconnecting
  client.on("close", () => {
    console.log("client disconnected");
  });
});

// start created server
server.listen(8000);

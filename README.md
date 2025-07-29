# Command Executor
This mod allows you to execute commands using JavaScript, using additional APIs.
## Developers
- Owner: bigeror
- Contributor: DevMevTV
- Helper: PilkeySek

This project is maintained by bigeror, but huge thanks for DevMevTV for reworking idea using Javet, which allowed to use additional APIs inside JavaScript and removed most of delays.

## Usage
To use this mod you need to install it inside your mod folder (this mod is developed for version 1.21.3 fabric 0.16.10 and fabricAPI 0.114.0+1.21.3) and launch minecraft. Once minecraft launched will be created config "command executor.properties" inside your config folder, and created folder "executor" inside your game folder with file "main.js". To code using this mod you need to edit "main.js" file. To execute the code you need to press "U" (by default) key, and to stop the code you need to press "O" (by default). In this project you can use any of the existing node.js packages, but you need to install them manually.

## Config
Inside the cofig.properties file you can change the path to the main.js file, and enable/disable info messages. (Info message is currently used only for starting the JavaScript runtime)

## APIs
### console
"console" is a default console class, which you can use to log, warn and error messages.
It replaces the default JavaScript console class. (types are important)
- log(String message) - logs the message to the console
- warn(String message) - warns the message to the console
- error(String message) - errors the message to the console

### world
"world" is huge class made specifically to manipulate with minecraft world.
- tell(String message) - sends the message to the chat
- whisper(String message) - sends the message to the player
- whisper(String message, Integer color) - sends the message to the player with the color
- runCommand(String command) - runs the command in the chat
- onTick(function function) - runs the given function every tick
- onMessage(function function) - runs the function every message, gives the message (String) as an argument
- executeDefault(String command) - executes the command in the default command block (which you look at the moment of activation)
- execute(String command, int posX, int posY, int posZ) - executes the command in the command block at the given position

  (score methods throw an error when scoreboard not found (not visible) or score not found)
- getScore(String objective, String target) - gets the score of the target in the objective (for this method to work scoreboard must be visible (sidebar, list, belowName etc.))
- getScoreboards() - gets the list of all scoreboards (scoreboard must be visible)
- getScores(String objective) - gets the list of all scores in the objective (scoreboard must be visible)
- getPlayers() - gets the list of all players in the world as an array of objects "player" (see below)

### player
"player" is an object which contains all the information about the player.
- name - the name of the player
- uuid - the uuid of the player
- pos - the position of the player as an array of numbers [x, y, z]
- rotation - the rotation of the player as an array of numbers [yaw, pitch]
- health - the health of the player
- food - the food level of the player
- motion - the motion of the player as an array of numbers [x, y, z]
- inventory - the Java inventory object of the player (use the class as you would in fabric client mod)
- player - the Java player object (use the class as you would in fabric client mod)

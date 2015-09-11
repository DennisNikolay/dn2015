var groupNumber = 69; // replace by your group number
var socket;
var userNr;
var userName;
var messageNr;
var msgID;
var port=parseInt(prompt("What port to use?"));
function main()
{
	document.getElementById("groupid").textContent = "Group " + groupNumber;
	
	// Insert any initialization code here
}

// Called when the "Connect" button is pressed
function connectButtonPressed()
{
	var server = document.getElementById("serverInput").value;
	document.getElementById("connect").setAttribute("disabled", "disabled");
	setStatusBarText("Connecting to " + server + "...");
	
	// Insert your code here
	socket = new WebSocket("ws://"+server+":"+port);
	socket.onopen = function(){ onConnected(server); };
	socket.onerror = function(){ onDisconnected(); };



}


// Called when the "Disconnect" button is pressed
function disconnectButtonPressed()
{
	document.getElementById("disconnect").setAttribute("disabled", "disabled");
	document.getElementById("login").setAttribute("disabled", "disabled");
	setStatusBarText("Disconnecting...");
	
	// Insert your code here
	socket.close();
	socket.onclose=function(){onDisconnected();};
}

// Called when the "Log in" button is pressed
function loginButtonPressed()
{
	var name = document.getElementById("nameInput").value;
	var password = document.getElementById("passwordInput").value;
	document.getElementById("login").setAttribute("disabled", "disabled");
	setStatusBarText("Authenticating...");
	
	// Insert your code here
	
	userNr = getRandomInt();
	// Sends an authentication request to the server with given name and password.
	socket.send("AUTH "+ userNr +"\r\n"+ name +"\r\n"+ password );
	onMessage();
	
	
	
}


// Called when the client should be listening for server replies.
function onMessage(){
	socket.onmessage=(function(evt){
						// separated lines of server message
						var msg = evt.data.split("\r\n");
						
						// first line of server message
						var head = msg[0].split(" ");

						// Switching over sent command
						switch (head[0]) {
						
						case "OKAY":
							// Login and authentication successful.
							if (head[1] == userNr) {
								onLoginSuccess();
								userName = name;
							// Message confirmed by the server.
							} else if(head[1] == msgID){
								setStatusBarText("Message sent!");
								markMessageConfirmed(msgID);
															
							}
							break;
							
						case "FAIL":
							// Wrong password.
							if (msg[1] == "PASSWORD") {
								onLoginFailed();							
								setStatusBarText("Wrong password. Please try again.");
							}
							// Invalid name, i.e. name already in use.
							if (msg[1] == "NAME") {
								onLoginFailed();
								setStatusBarText("Sorry, this name is already in use.");
							}
							// Invalid message id.
							if (msg[1] == "NUMBER"){
								setStatusBarText("Sry, invalid Message Number. Try again!");
							}
							// Invalid message, i.e. message is too long.
							if (msg[1] == "LENGTH"){
								setStatusBarText("Sry, too long Message. Please keep it short.");
							}

							break;
						
						
						case "ARRV":
							// New user has joined the chat and is shown as online. 
							addUser(head[1], msg[1], msg[2]);
							setStatusBarText(findUserName(head[1]) + " has joined the chat.")
							break;
						
						case "LEFT":
							// User has left the chat and is removed from online user list.
							setStatusBarText(findUserName(head[1]) + " has disconnected.");
							removeUser(head[1]);
							break;
							
						case "SEND":
							// Incoming message is shown on the chat client.
							addChatMessage(head[1], findUserName(msg[1]), msg[2], false);
							// Acknowledgement is sent to the server to notify user that the message was read.
							socket.send("ACKN "+ head[1]);
							break;
						
						case "ACKN":
							// Mark the message to be read by certain user.
							markMessageAcknowledged(head[1], findUserName(msg[1]));
							break;
		
						case "INVD":
							// Invalid message sent to server. As a consequence, the client disconnects.
							setStatusBarText("Invalid message. Connection closed.");
							onDisconnected();
							break;
		
						default:
							break;
						}
							

					});
}


// Called when the "Send" button is pressed
function sendButtonPressed()
{
	var message = document.getElementById("messageInput").value;
	if(message == "") return;
	var to = "*";
	if(message.substring(0, 1) == "@")
	{
		var index = message.indexOf(":");
		if(index > 0)
		{
			var toUser = findUserNumber(message.substring(1, index));
			if(toUser !== -1) to = toUser;
			else
			{
				addInfoMessage("Unknown user: " + message.substring(1, index) + ".");
				return;
			}
			message = message.substring(index + 1);
		}
	}
	message = message.trim();
	if(message == "") return;
	document.getElementById("messageInput").value = "";
	setStatusBarText("Sending message...");	
	
	// Insert your code here to send <message> to <to>
	
	msgID = getRandomInt();
	// Sends out the message to the server.
	socket.send("SEND " + msgID + "\r\n" + to + "\r\n" + message);
	// Adds the sent message to the client gui.
	addChatMessage(msgID, userName, message, true);
	// Listens again for server replies.
	onMessage();



}



// Use this function to get random integers for use with the Chat protocol
function getRandomInt()
{
  return Math.floor(Math.random() * 9007199254740991);
}

// The remaining functions in this file are helper functions to update
// the user interface when certain actions are performed (e.g. a message
// is sent and should be displayed in the message list) or certain
// events occur (e.g. a message arrives, or a user has gone offline).
// You should not need to modify them, but you can if you want.
// You can also just delete everything (including the functions above)
// and write a new user interface on your own.

// Call this function when the connection to the server has been established
function onConnected(server)
{
	if(server === undefined) document.getElementById("connectionStatusText").textContent = "Connected.";
	else document.getElementById("connectionStatusText").textContent = "Connected to " + server + ".";
	document.getElementById("connect").style.display = "none";
	document.getElementById("disconnect").style.display = "flex";
	document.getElementById("connect").removeAttribute("disabled");
	document.getElementById("login").removeAttribute("disabled");
	setStatusBarText("Connected.");
}

var isLoggedIn = false;
var suppressStatusBarUpdate = false;

// Call this function when the connection to the server has been closed
function onDisconnected()
{
	document.getElementById("disconnect").style.display = "none";
	document.getElementById("connect").style.display = "flex";
	document.getElementById("connect").removeAttribute("disabled");
	document.getElementById("disconnect").removeAttribute("disabled");
	document.getElementById("login").setAttribute("disabled", "disabled");
	document.getElementById("message").setAttribute("disabled", "disabled");
	document.getElementById("userlist").setAttribute("disabled", "disabled");
	if(!suppressStatusBarUpdate) setStatusBarText("Disconnected.");
	suppressStatusBarUpdate = false;
	if(isLoggedIn) addInfoMessage("Session ended, no more messages will be received.");
	clearUsers();
	isLoggedIn = false;
}

// Call this function when the connection to the server fails (i.e. you get an error)
function onConnectionFailed()
{
	setStatusBarText("Connection failed.");
	suppressStatusBarUpdate = true; // onDisconnected should also get called
}

// Call this function when login was successful
function onLoginSuccess()
{
	setStatusBarText("Successfully logged in.");
	document.getElementById("message").removeAttribute("disabled");
	document.getElementById("userlist").removeAttribute("disabled");
	addInfoMessage("Session started, now receiving messages.");
	isLoggedIn = true;
}

// Call this function when login failed
function onLoginFailed()
{
	setStatusBarText("Login failed.");
	document.getElementById("login").removeAttribute("disabled");
	isLoggedIn = false;
}

// Call this function to add informational text to the message list
function addInfoMessage(text)
{
	var msglist = document.getElementById("msglist");
	var infoDiv = document.createElement("div");
	infoDiv.className = "info";
	infoDiv.appendChild(document.createTextNode(text));
	msglist.appendChild(infoDiv);
	msglist.scrollTop = msglist.scrollHeight;
}

// Call this function to add a chat message to the message list.
// If isSent is true, then it is added as a "sent, but not confirmed"
// message; call markMessageConfirmed when the server has acknowledged
// that it received the message.
function addChatMessage(number, from, text, isSent)
{
	var msglist = document.getElementById("msglist");
	var msgDiv = document.createElement("div");
	msgDiv.className = isSent ? "sent" : "received";
	msgDiv.id = "msg" + number;
	var fromDiv = document.createElement("div");
	fromDiv.className = "from";
	fromDiv.appendChild(document.createTextNode(from === null ? "Unknown user" : from));
	msgDiv.appendChild(fromDiv);
	var textDiv = document.createElement("div");
	textDiv.className = "message";
	textDiv.appendChild(document.createTextNode(text));
	msgDiv.appendChild(textDiv);
	if(isSent)
	{
		var readersDiv = document.createElement("div");
		readersDiv.className = "readers";
		readersDiv.id = "msg" + number + "readers";
		msgDiv.appendChild(readersDiv);
		msgDiv.style.opacity = 0.5;
	}
	msglist.appendChild(msgDiv);
	msglist.scrollTop = msglist.scrollHeight;
}

// Call this function to mark a sent message as confirmed
function markMessageConfirmed(number)
{
	var msgDiv = document.getElementById("msg" + number);
	if(!msgDiv) return;
	msgDiv.style.opacity = 1.0;
}

// Call this function to indicate that a message has been acknowledged by a certain user
function markMessageAcknowledged(messageNumber, userName)
{
	var msgReadersDiv = document.getElementById("msg" + messageNumber + "readers");
	if(!msgReadersDiv) return;
	markMessageConfirmed(messageNumber);
	var readerSpan = document.createElement("span");
	readerSpan.appendChild(document.createTextNode(userName));
	msgReadersDiv.appendChild(readerSpan);
}

// Call this function to change the text in the status bar
function setStatusBarText(text)
{
	document.getElementById("statusbar").textContent = text;
}


var users = [];

// Call this function to show a user as online
function addUser(number, name, description)
{
	users.push({ number: number, name: name, description: description });
	var userlist = document.getElementById("userlist");
	var userSpan = document.createElement("span");
	userSpan.id = "user" + number;
	var userNameSpan = document.createElement("span");
	userNameSpan.className = "user-name";
	userNameSpan.appendChild(document.createTextNode(name));
	var userDescSpan = document.createElement("span");
	userDescSpan.className = "user-desc";
	userDescSpan.appendChild(document.createTextNode(description));
	userSpan.appendChild(userNameSpan);
	userSpan.appendChild(userDescSpan);
	userlist.appendChild(userSpan);
}

// Call this function when a user goes offline
function removeUser(number)
{
	var userlist = document.getElementById("userlist");
	for(var i = 0; i < users.length; ++i)
	{
		if(users[i].number == number)
		{
			users.splice(i--, 1);
			var userSpan = document.getElementById("user" + number);
			if(userSpan) userlist.removeChild(userSpan);
		}
	}
}

// Call this function to get the number of a user with the given name.
// Returns -1 if there is no user with this name.
function findUserNumber(name)
{
	for(var i = 0; i < users.length; ++i)
	{
		if(users[i].name == name) return users[i].number;
	}
	return -1;
}

// Call this function to get the name of a user with the given number.
// Returns null if there is no user with this number.
function findUserName(number)
{
	for(var i = 0; i < users.length; ++i)
	{
		if(users[i].number == number) return users[i].name;
	}
	return null;
}

// Called by onDisconnected
function clearUsers()
{
	var userlist = document.getElementById("userlist");
	for(var i = 0; i < users.length; ++i)
	{
		var userSpan = document.getElementById("user" + users[i].number);
		if(userSpan) userlist.removeChild(userSpan);
	}
	users = [];
}

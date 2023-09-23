Security Config
Files Added
In security package
- config : securityConfig.java
- models : Auth Response, Auth Req, User Model, UserRep
- service: JwtFilterRequest; UserService
- utils  : JwtUtils
- controller: auth controllers

Exposed end points ("/register","/auth")
All other endpoints behind security
To access endpoints the following header is needed
Authorization: "Bearer {token}"

Where to get the token:
in the auth endpoint; successfull log in will return a token.
add that toke to the header.

User in mongodb rn
{"username":"doc88",
"password":"12345678"}
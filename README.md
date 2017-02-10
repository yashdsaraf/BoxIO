# BoxIO `v1.0.0`

###Use your Box account as a named pipe (FIFO)

####BUILD
1. Clone this repo
2. Run `ant` in the cloned repo directory
3. The built jar file should be at *build/libs/BoxIO-(version).jar*

####USAGE
BoxIO can be used in 2 scenarios
 * LISTEN
 * UPLOAD

#####LISTEN
`java -jar BoxIO.jar <properties file> listen <file count>`
* <properties file> - File consisting of the necessary keys
* <file count> - How many file to download before quitting

#####UPLOAD
`java -jar BoxIO.jar <properties file> upload <file 1> <file 2> ....<file N>`
* <properties file> - File consisting of the necessary keys
* <file *> - Files to be uploaded
_Note: When running multiple instances of BoxIO at once, use separate jar files to avoid ConcurrentModificationException_

*Keys required in the properties file are*
`client_id`
`client_secret`
`private_key (base64 encoded)`
`private_key_password`
`user_id`
`public_key_id`
`folder_id`

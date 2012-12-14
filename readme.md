# Super Quick Usage Guide

1. download repository
2. run `perl viscel.pl`
3. navigate you web browser to `http://localhost/`
4. search for comics or click on one of the cores to see what is available
5. start reading

# Super Quick Troubleshooting

* you might need to change the port the webserver listens to with
  `perl viscel.pl --port=23456`
  and then connect to `http://localhost:23456/`
* if the server seems to be unresponsive
  the program might have decided to do maintenance.
  check the console output if any downloads are going on.
  just wait a short time and the program will be ready for you again.

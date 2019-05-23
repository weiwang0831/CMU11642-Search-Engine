all:
ifeq ($(OS),Windows_NT)
	# assume windows
	javac -Xlint -cp ".;lucene-6.6.0/*" -g *.java
else
	# assume Linux
	javac -Xlint -cp ".:lucene-6.6.0/*" -g *.java
endif

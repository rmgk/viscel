.DEFAULT_GOAL := bundle

stage:
	sbt stage

bundle: stage
	mkdir -p bundle/static
	cp -r target/universal/stage/* bundle/
	cp -r target/resources/static/* bundle/static/

clean:
	rm -rf bundle
	rm -rf **/target

docker: bundle
	sudo docker build -t viscel:latest .

dockerrun: docker
	sudo docker run -p 2358:2358 viscel:latest

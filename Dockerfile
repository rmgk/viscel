FROM openjdk:12-alpine
COPY bundle /app
CMD ["java", "-cp", "/app/lib/*", "viscel.Viscel", "--static", "/app/static", "--basedir", "/data", "--port", "2358"]
EXPOSE 2358

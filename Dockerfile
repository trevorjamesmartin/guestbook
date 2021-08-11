FROM openjdk:8-alpine

COPY target/uberjar/guestbook.jar /guestbook/app.jar

COPY prod-config.edn /guestbook/config.edn

EXPOSE 80

CMD ["java", "-jar", "-Dconf=/guestbook/config.edn", "/guestbook/app.jar"]

# # TO BUILD THIS IMAGE,
# docker build -t guestbook:latest .

# # ERROR ? 
# #  Connection to 127.0.0.1:5432 refused
# #
# # to expose host without docker-compose yaml, (set option "net" to "host")
#
# docker run -d --net="host" guestbook

FROM openjdk:16-alpine
COPY ./build/libs/DaylinMicroservices-Core.jar ./service.jar
EXPOSE 2999
CMD java -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -Xms128M -Xmx256M -jar service.jar
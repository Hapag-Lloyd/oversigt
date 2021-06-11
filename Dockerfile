FROM adoptopenjdk:8-jre
RUN mkdir /opt/oversigt
COPY * /opt/oversigt/bin
CMD CMD ["/opt/oversigt/run.sh"]

# revenda-challenge


Spring Boot App: http://localhost:8080
Prometheus: http://localhost:9090
Grafana: http://localhost:3000 (default credentials: admin / admin)


Para ver as metricas e dados do prometeus é necessário criar um data source no grafana, 

para vá em Connections > Data sources > Select o Prometheus e adicione no Connection:
http://prometheus:9090

depois salve a configuração:
Save & test

Depois adicione um dashboard para ver os gráficos no site do grafana:
https://grafana.com/grafana/dashboards/

ou adicionar o dashboard SpringBoot 2.1 Statistics com o seguinte ID:
10280

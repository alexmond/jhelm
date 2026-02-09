#!/bin/bash
mkdir -p sample-charts
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add argo https://argoproj.github.io/argo-helm
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

charts=(
    "bitnami/mongodb"
    "bitnami/postgresql"
    "bitnami/redis"
    "bitnami/mysql"
    "bitnami/mariadb"
    "bitnami/apache"
    "bitnami/jenkins"
    "bitnami/kafka"
    "bitnami/rabbitmq"
    "bitnami/elasticsearch"
    "bitnami/drupal"
    "bitnami/wordpress"
    "bitnami/joomla"
    "bitnami/ghost"
    "bitnami/moodle"
    "bitnami/magento"
    "bitnami/redmine"
    "prometheus-community/prometheus"
    "grafana/grafana"
    "prometheus-community/kube-prometheus-stack"
    "argo/argo-cd"
)

for chart in "${charts[@]}"; do
    name=$(basename $chart)
    if [ ! -d "sample-charts/$name" ]; then
        echo "Pulling $chart..."
        helm pull $chart --untar -d sample-charts/
    else
        echo "Chart $name already exists, skipping pull."
    fi
done
